package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.controllers.dtos.TriageResultDTO;
import com.pretriage.backend.exceptions.ProveedorIaException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small boundary around the provider used by the conversational triage flow.
 *
 * <p>The response schema is sent to Ollama as a native structured-output
 * constraint. The response is still checked locally because a provider can
 * return syntactically valid JSON which does not satisfy the application
 * contract.</p>
 */
@Service
public class TriageIaClient {
    private static final String RESULTADO = "resultado";
    private static final String FINALIZADO = "finalizado";
    private static final String MENSAJE = "mensaje";

    /**
     * Ollama accepts a JSON Schema in the request's {@code format} property.
     * The two nullable integer fields are intentionally represented as a union:
     * an unfinished interview may not have a pain score or a priority yet.
     */
    private static final Map<String, Object> OUTPUT_SCHEMA = schema(false);
    private static final Map<String, Object> FINAL_OUTPUT_SCHEMA = schema(true);
    private static final String FINALIZATION_DIRECTIVE = """
            La entrevista debe finalizar en esta respuesta. No hagas preguntas. Para datos ausentes usa
            "no informado", listas vacias o intensidadDolor=null; nunca los inventes.
            Resume todos los hechos informados por el paciente en resultado, conserva los campos requeridos
            y usa como motivoConsulta el motivo principal expresado por el paciente; si describe sintomas
            como motivo, resumelos ahi y no escribas "no informado".
            Si afirma dolor de pecho o dificultad para respirar, agrega en signosAlarma los elementos
            "dolor toracico" o "dificultad respiratoria", respectivamente, aunque tambien aparezcan
            en sintomas; no los pongas en antecedentesRelevantes, que describe enfermedades previas.
            Copia la duracion que el paciente informa; no agregues "desde ayer" si no lo dijo.
            y asigna nivelPrioridad como entero de 1 a 5 según la informacion disponible.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(TriageIaClient.class);

    public TriageIaClient(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Queries the provider and returns a response that conforms to the triage
     * contract. Provider failures and invalid responses are exposed as
     * {@link ProveedorIaException} after this single provider request.
     */
    public TriageAiResponse consultar(String systemPrompt, String conversation) {
        return consultar(systemPrompt, conversation, false);
    }

    /**
     * Queries the provider, optionally requiring the provider to close the
     * interview in this response. The forced-final mode is used when the
     * conversation service has reached its terminal boundary.
     */
    public TriageAiResponse consultar(String systemPrompt, String conversation, boolean finalizacionRequerida) {
        if (isBlank(systemPrompt) || isBlank(conversation)) {
            throw new ProveedorIaException(new IllegalArgumentException("El prompt de triage no puede estar vacio"));
        }

        String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                        .options(OllamaChatOptions.builder()
                                .format(finalizacionRequerida ? FINAL_OUTPUT_SCHEMA : OUTPUT_SCHEMA))
                        .system(finalizacionRequerida ? systemPrompt + "\n\n" + FINALIZATION_DIRECTIVE : systemPrompt)
                        .user(conversation)
                        .call()
                        .content();
        }
        catch (Exception exception) {
            logger.warn("Triage IA fallback category={} reason={}", "provider_failure", "call_failed");
            throw new ProveedorIaException(exception);
        }
        try {
            return parseAndValidate(rawResponse, finalizacionRequerida);
        }
        catch (Exception exception) {
            if (exception instanceof InvalidResponseException invalidResponse) {
                logger.warn("Triage IA fallback category={} field={} reason={}",
                        "invalid_response", invalidResponse.field, invalidResponse.reason);
            }
            else {
                logger.warn("Triage IA fallback category={} field={} reason={}",
                        "invalid_response", "response", "malformed_or_unreadable");
            }
            throw new ProveedorIaException(exception);
        }
    }

    private TriageAiResponse parseAndValidate(String rawResponse, boolean finalizacionRequerida)
            throws JacksonException {
        if (isBlank(rawResponse)) {
            throw invalid("response", "empty");
        }

        JsonNode root = objectMapper.readTree(rawResponse);
        requireObject(root, "response", "not_object");
        requireExactProperties(root, List.of(FINALIZADO, MENSAJE, RESULTADO));
        requireBoolean(root, FINALIZADO);
        requireString(root, MENSAJE);
        if (finalizacionRequerida && !root.get(FINALIZADO).booleanValue()) {
            throw invalid(FINALIZADO, "unexpected_value");
        }

        JsonNode resultadoNode = root.get(RESULTADO);
        if (resultadoNode == null || resultadoNode.isNull()) {
            throw invalid(RESULTADO, "null");
        }

        validateResultNode(resultadoNode);
        TriageAiResponse response = objectMapper.readValue(root.toString(), TriageAiResponse.class);
        if (response == null) {
            throw invalid("response", "null");
        }
        if (response.finalizado()) {
            validateFinalResult(response.resultado());
        }
        return response;
    }

    private void validateResultNode(JsonNode result) {
        requireObject(result, RESULTADO, "not_object");
        List<String> required = List.of(
                "motivoConsulta", "sintomas", "inicio", "evolucion", "intensidadDolor",
                "signosAlarma", "antecedentesRelevantes", "medicamentos", "alergias",
                "posibilidadEmbarazo", "observaciones", "nivelPrioridad",
                "requiereAtencionInmediata", "recomendacionSeguridad");
        requireExactProperties(result, required);

        requireString(result, "motivoConsulta");
        requireStringArray(result, "sintomas");
        requireString(result, "inicio");
        requireString(result, "evolucion");
        requireNullableInteger(result, "intensidadDolor", 0, 10);
        requireStringArray(result, "signosAlarma");
        requireStringArray(result, "antecedentesRelevantes");
        requireStringArray(result, "medicamentos");
        requireStringArray(result, "alergias");
        requireString(result, "posibilidadEmbarazo");
        requireString(result, "observaciones");
        requireNullableInteger(result, "nivelPrioridad", 1, 5);
        requireBoolean(result, "requiereAtencionInmediata");
        requireString(result, "recomendacionSeguridad");
    }

    private void validateFinalResult(TriageResultDTO result) {
        if (result == null) {
            throw invalid(RESULTADO, "null");
        }
        if (result.nivelPrioridad() == null || result.nivelPrioridad() < 1 || result.nivelPrioridad() > 5) {
            throw invalid("nivelPrioridad", "missing_or_out_of_range");
        }
        if (isBlank(result.motivoConsulta()) || isUninformative(result.motivoConsulta())) {
            throw invalid("motivoConsulta", "missing_or_uninformative");
        }
        if (result.sintomas() == null || result.sintomas().stream().noneMatch(this::isMeaningful)) {
            throw invalid("sintomas", "missing_or_uninformative");
        }
    }

    private void requireExactProperties(JsonNode object, List<String> names) {
        for (String name : names) {
            if (!object.has(name)) {
                throw invalid(name, "missing");
            }
        }
        for (String field : object.propertyNames()) {
            if (!names.contains(field)) {
                throw invalid("response", "unexpected_field");
            }
        }
    }

    private void requireObject(JsonNode value, String field, String reason) {
        if (value == null || !value.isObject()) {
            throw invalid(field, reason);
        }
    }

    private void requireString(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isTextual() || isBlank(value.textValue())) {
            throw invalid(name, "invalid_text");
        }
    }

    private void requireStringArray(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isArray()) {
            throw invalid(name, "invalid_list");
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || isBlank(item.textValue())) {
                throw invalid(name, "invalid_list_item");
            }
        }
    }

    private void requireNullableInteger(JsonNode object, String name, int minimum, int maximum) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < minimum || value.intValue() > maximum) {
            throw invalid(name, "missing_or_out_of_range");
        }
    }

    private void requireBoolean(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isBoolean()) {
            throw invalid(name, "invalid_boolean");
        }
    }

    private InvalidResponseException invalid(String field, String reason) {
        return new InvalidResponseException(field, reason);
    }

    private static final class InvalidResponseException extends IllegalStateException {
        private final String field;
        private final String reason;

        private InvalidResponseException(String field, String reason) {
            super("Invalid structured response");
            this.field = field;
            this.reason = reason;
        }
    }

    private boolean isMeaningful(String value) {
        return !isBlank(value) && !isUninformative(value);
    }

    private boolean isUninformative(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("no informado") || normalized.equals("no especificado")
                || normalized.equals("desconocido") || normalized.equals("ninguno");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, Object> schema(boolean finalizacionRequerida) {
        Map<String, Object> intensityOrNull = Map.of(
                "anyOf", List.of(
                        Map.of("type", "integer", "minimum", 0, "maximum", 10),
                        Map.of("type", "null")));
        Map<String, Object> priorityOrNull = Map.of(
                "anyOf", List.of(
                        Map.of("type", "integer", "minimum", 1, "maximum", 5),
                        Map.of("type", "null")));
        Map<String, Object> resultProperties = new LinkedHashMap<>();
        resultProperties.put("motivoConsulta", Map.of("type", "string", "minLength", 1));
        resultProperties.put("sintomas", stringArraySchema());
        resultProperties.put("inicio", Map.of("type", "string", "minLength", 1));
        resultProperties.put("evolucion", Map.of("type", "string", "minLength", 1));
        resultProperties.put("intensidadDolor", intensityOrNull);
        resultProperties.put("signosAlarma", stringArraySchema());
        resultProperties.put("antecedentesRelevantes", stringArraySchema());
        resultProperties.put("medicamentos", stringArraySchema());
        resultProperties.put("alergias", stringArraySchema());
        resultProperties.put("posibilidadEmbarazo", Map.of("type", "string", "minLength", 1));
        resultProperties.put("observaciones", Map.of("type", "string", "minLength", 1));
        resultProperties.put("nivelPrioridad", finalizacionRequerida
                ? Map.of("type", "integer", "minimum", 1, "maximum", 5)
                : priorityOrNull);
        resultProperties.put("requiereAtencionInmediata", Map.of("type", "boolean"));
        resultProperties.put("recomendacionSeguridad", Map.of("type", "string", "minLength", 1));
        Map<String, Object> resultSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.copyOf(resultProperties.keySet()),
                "properties", resultProperties);
        Map<String, Object> responseProperties = new LinkedHashMap<>();
        responseProperties.put(FINALIZADO, finalizacionRequerida
                ? Map.of("type", "boolean", "enum", List.of(true))
                : Map.of("type", "boolean"));
        responseProperties.put(RESULTADO, resultSchema);
        responseProperties.put(MENSAJE, Map.of("type", "string", "minLength", 1));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of(FINALIZADO, RESULTADO, MENSAJE),
                "properties", responseProperties);
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string", "minLength", 1));
    }
}
