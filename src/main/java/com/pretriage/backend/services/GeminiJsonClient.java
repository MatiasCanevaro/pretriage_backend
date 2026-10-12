package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.ProveedorIaException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/** One bounded Gemini request, with JSON Schema and no credentials or patient text in logs. */
@Service
@EnableConfigurationProperties(GeminiChatProperties.class)
public class GeminiJsonClient {
    private final GeminiChatProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public GeminiJsonClient(GeminiChatProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    }

    GeminiJsonClient(GeminiChatProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public String generar(String systemPrompt, String input, Map<String, Object> schema) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw failure("Gemini no configurado: falta GEMINI_API_KEY");
        }
        String model = properties.model().replaceFirst("^models/", "");
        if (!model.matches("[A-Za-z0-9._-]+")) throw failure("Modelo Gemini invalido");
        try {
            Map<String, Object> body = Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input)))),
                    "generationConfig", Map.of("temperature", 0, "maxOutputTokens", 2048,
                            "responseFormat", Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema))));
            URI endpoint = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/models/" + model + ":generateContent");
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(properties.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw failure("Gemini HTTP " + response.statusCode());
            var root = mapper.readTree(response.body());
            var candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) throw failure("Gemini no devolvio candidatos");
            var candidate = candidates.get(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) {
                throw failure("Gemini no completo la respuesta");
            }
            StringBuilder output = new StringBuilder();
            for (var part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isString()) {
                    output.append(part.path("text").stringValue());
                }
            }
            if (output.isEmpty()) throw failure("Gemini devolvio una respuesta vacia");
            return output.toString();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw failure("Consulta Gemini interrumpida");
        } catch (ProveedorIaException error) {
            throw error;
        } catch (Exception error) {
            // Do not attach provider response bodies: they may contain clinical data.
            throw failure("Fallo de transporte o formato de Gemini: " + error.getClass().getSimpleName());
        }
    }

    private ProveedorIaException failure(String message) {
        return new ProveedorIaException(new IllegalStateException(message));
    }
}
