package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.controllers.dtos.TriageResultDTO;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.chat.EstadoClasificacionChat;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real chat/provider boundaries, fake HTTP only: retries must not duplicate domain side effects. */
class ChatAutomaticRetryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final RepoChat chats = mock(RepoChat.class);
    private final AtencionHospitalService queue = mock(AtencionHospitalService.class);
    private final Chat chat = new Chat(new Paciente());

    private ChatService service() {
        chat.setId(7L);
        when(chats.findByIdAndPacienteUsuarioAuthId(7L, "patient")).thenReturn(Optional.of(chat));
        when(chats.findForUpdate(7L, "patient")).thenReturn(Optional.of(chat));
        var properties = new GeminiChatProperties("test-key", "gemini-3.5-flash-lite",
                "https://example.invalid/v1beta", Duration.ofSeconds(1), Duration.ofSeconds(3), 8192,
                2, Duration.ofSeconds(1), Duration.ZERO);
        var gemini = new GeminiJsonClient(properties, mapper, http);
        return new ChatService(chats, mock(RepoPacientes.class), mock(RepoConsultasMedicas.class),
                mock(RepoEntradasCola.class), queue, new TriageIaClient(gemini, mapper), mapper);
    }

    @Test
    void error503RecuperadoEntregaUnaPreguntaYGuardaUnSoloTurno() throws Exception {
        var unavailable = response(503, "");
        var success = response(200, envelope(mapper.writeValueAsString(
                Map.of("mensaje", "¿Podés contarme qué sentís?", "estadoEntrada", "RELEVANTE"))));
        var verified = response(200, envelope("{\"valido\":true}"));
        when(http.<String>send(any(), any())).thenReturn(unavailable, success, verified);

        var result = service().enviarMensaje("7", "patient", "Me siento raro");

        assertFalse(chat.isFinalizado());
        assertEquals(EstadoClasificacionChat.NO_SOLICITADA, chat.getEstadoClasificacion());
        assertNull(chat.getResultadoTriageJson());
        assertEquals("GEMINI", result.origenRespuesta());
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.PACIENTE).count());
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.BOT).count());
        verify(http, times(3)).send(any(), any());
        verify(chats, times(1)).save(chat);
        verifyNoInteractions(queue);
    }

    @Test
    void recetaMarcadaRelevanteSeCorrigeSinGuardarLaRespuestaFueraDeAlcance() throws Exception {
        String receta = mapper.writeValueAsString(Map.of("mensaje", "Mezclá harina, azúcar y chocolate.",
                "estadoEntrada", "RELEVANTE"));
        String pregunta = mapper.writeValueAsString(Map.of("mensaje", "¿Podés contarme qué sentís?",
                "estadoEntrada", "RELEVANTE"));
        var unsafe = response(200, envelope(receta));
        var rejected = response(200, envelope("{\"valido\":false}"));
        var safe = response(200, envelope(pregunta));
        var verified = response(200, envelope("{\"valido\":true}"));
        when(http.<String>send(any(), any())).thenReturn(unsafe, rejected, safe, verified);

        var response = service().enviarMensaje("7", "patient", "Me siento raro");

        assertEquals("¿Podés contarme qué sentís?", response.respuesta().contenido());
        assertFalse(chat.isFinalizado());
        assertNull(chat.getResultadoTriageJson());
        assertEquals(2, chat.getMensajes().size());
        assertTrue(chat.getMensajes().stream().noneMatch(m -> m.getContenido().contains("harina")));
        verify(http, times(4)).send(any(), any());
        verify(chats, times(1)).save(chat);
        verifyNoInteractions(queue);
    }

    @Test
    void rechazoSemanticoDePreguntaGuiaUnaCorreccionConUnSoloTurnoYSinEfectosDeCola() throws Exception {
        var firstQuestion = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", "Ya registré tus respuestas para el equipo médico.", "estadoEntrada", "RELEVANTE")));
        var correctedQuestion = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", "¿Desde cuándo te duele la cabeza?", "estadoEntrada", "RELEVANTE")));
        var responses = List.of(response(200, firstQuestion),
                response(200, envelope("{\"valido\":false,\"motivo\":\"COMUNICACION\"}")),
                response(200, correctedQuestion), response(200, envelope("{\"valido\":true}")));
        var requests = responderConCaptura(responses);

        var result = service().enviarMensaje("7", "patient", "Tengo dolor de cabeza");

        assertEquals("¿Desde cuándo te duele la cabeza?", result.respuesta().contenido());
        assertEquals(2, chat.getMensajes().size());
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.PACIENTE).count());
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.BOT).count());
        assertEquals(4, requests.size(), "una generación/verificación y una corrección/verificación");
        String correctionPrompt = requestSystemInstruction(requests.get(2));
        assertTrue(correctionPrompt.contains("No menciones registro, despedida, clasificación, triage ni resultados."));
        assertFalse(correctionPrompt.contains("invalid_question_response"));
        assertFalse(requestBody(requests.get(2)).contains("Ya registré tus respuestas"));
        verify(chats, times(1)).save(chat);
        verifyNoInteractions(queue);
    }

    @Test
    void segundoRechazoSemanticoDePreguntaNoDuplicaTurnoNiEncola() throws Exception {
        var firstQuestion = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", "Ya registré tus respuestas para el equipo médico.", "estadoEntrada", "RELEVANTE")));
        var correctedQuestion = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", "Tu dolor se debe a una enfermedad grave.", "estadoEntrada", "RELEVANTE")));
        var responses = List.of(response(200, firstQuestion),
                response(200, envelope("{\"valido\":false,\"motivo\":\"COMUNICACION\"}")),
                response(200, correctedQuestion), response(200, envelope("{\"valido\":false,\"motivo\":\"INFERENCIA\"}")));
        var requests = responderConCaptura(responses);

        assertThrows(com.pretriage.backend.exceptions.ProveedorIaException.class,
                () -> service().enviarMensaje("7", "patient", "Tengo dolor de cabeza"));

        assertFalse(chat.isFinalizado());
        assertEquals(EstadoClasificacionChat.NO_SOLICITADA, chat.getEstadoClasificacion());
        assertNull(chat.getResultadoTriageJson());
        assertTrue(chat.getMensajes().isEmpty(), "El turno fallido se revierte una sola vez y no guarda respuesta del bot.");
        assertEquals(4, requests.size(), "No debe haber una segunda corrección después del rechazo corregido.");
        assertTrue(requestSystemInstruction(requests.get(2))
                .contains("No menciones registro, despedida, clasificación, triage ni resultados."));
        verify(chats, never()).save(any());
        verifyNoInteractions(queue);
    }

    @Test
    void aclaracionPuedeRepetirLaUltimaPreguntaPendiente() throws Exception {
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                "¿Cuál es el motivo?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                "Tengo fiebre", AutorMensaje.PACIENTE, chat.getPaciente()));
        String pendingQuestion = "¿Qué temperatura te tomaste?";
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                pendingQuestion, AutorMensaje.BOT, null));
        var response = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", pendingQuestion, "estadoEntrada", "RELEVANTE")));
        var requests = responderConCaptura(List.of(response(200, response),
                response(200, envelope("{\"valido\":true}"))));

        var result = service().enviarMensaje("7", "patient", "38 kg celsius");

        assertEquals(pendingQuestion, result.respuesta().contenido());
        assertTrue(chat.getMensajes().stream().filter(m -> "38 kg celsius".equals(m.getContenido()))
                .findFirst().orElseThrow().getExcluidoDelTriage());
        assertEquals(2, requests.size());
        assertTrue(requestSystemInstruction(requests.get(0)).contains("temperatura"));
        verify(chats, times(1)).save(chat);
        verifyNoInteractions(queue);
    }

    @Test
    void soloLaUltimaPreguntaPuedeRepetirseDuranteUnaAclaracionPendiente() throws Exception {
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                "¿Cuál es el motivo?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                "Tengo fiebre", AutorMensaje.PACIENTE, chat.getPaciente()));
        String olderQuestion = "¿Tenés alergias?";
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                olderQuestion, AutorMensaje.BOT, null));
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                "No", AutorMensaje.PACIENTE, chat.getPaciente()));
        String pendingQuestion = "¿Qué temperatura te tomaste?";
        chat.agregarMensaje(new com.pretriage.backend.model.chat.Mensaje(
                pendingQuestion, AutorMensaje.BOT, null));
        var repeated = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", olderQuestion, "estadoEntrada", "RELEVANTE")));
        var corrected = envelope(mapper.writeValueAsString(Map.of(
                "mensaje", "¿Mediste la temperatura?", "estadoEntrada", "RELEVANTE")));
        var requests = responderConCaptura(List.of(response(200, repeated),
                response(200, envelope("{\"valido\":true}")), response(200, corrected),
                response(200, envelope("{\"valido\":true}"))));

        var result = service().enviarMensaje("7", "patient", "38 cm");

        assertEquals("¿Mediste la temperatura?", result.respuesta().contenido());
        assertEquals(4, requests.size());
        assertTrue(requestSystemInstruction(requests.get(2)).contains("repeated_question"));
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.PACIENTE
                && "38 cm".equals(m.getContenido())).count());
        verify(chats, times(1)).save(chat);
        verifyNoInteractions(queue);
    }

    private List<HttpRequest> responderConCaptura(List<HttpResponse<String>> responses) throws Exception {
        var requests = new ArrayList<HttpRequest>();
        when(http.<String>send(any(), any())).thenAnswer(invocation -> {
            int index = requests.size();
            requests.add(invocation.getArgument(0));
            return responses.get(index);
        });
        return requests;
    }

    private String requestSystemInstruction(HttpRequest request) throws Exception {
        return mapper.readTree(requestBody(request)).path("systemInstruction").path("parts").get(0)
                .path("text").stringValue();
    }

    private String requestBody(HttpRequest request) throws Exception {
        var captured = new ByteArrayOutputStream();
        var completed = new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                captured.write(bytes, 0, bytes.length);
            }
            @Override public void onError(Throwable error) { completed.completeExceptionally(error); }
            @Override public void onComplete() { completed.complete(null); }
        });
        completed.get(1, TimeUnit.SECONDS);
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void timeoutRecuperadoClasificaUnaSolaVezSinRepetirMensajeNiIngresoEnCola() throws Exception {
        var result = new TriageAiResponse(true, "Busca atención urgente.", new TriageResultDTO(
                "dolor de pecho", List.of("dolor de pecho"), "no informado", "no informado", null,
                List.of("dolor de pecho"), List.of(), List.of(), List.of(), "no informado", "",
                5, true, "Busca atención urgente."));
        var validatedResult = mapper.valueToTree(result);
        ((tools.jackson.databind.node.ObjectNode) validatedResult).set("evidenciaClinica", mapper.valueToTree(List.of(
                Map.of("campo", "sintomas", "valor", "dolor de pecho", "cita", "Tengo dolor de pecho"),
                Map.of("campo", "signosAlarma", "valor", "dolor de pecho", "cita", "Tengo dolor de pecho"))));
        when(http.<String>send(any(), any())).thenThrow(new HttpTimeoutException("transient"))
                .thenReturn(response(200, envelope(mapper.writeValueAsString(validatedResult))),
                        response(200, envelope("{\"valido\":true}")));

        service().enviarMensaje("7", "patient", "Tengo dolor de pecho");

        assertTrue(chat.isFinalizado());
        assertEquals(EstadoClasificacionChat.COMPLETADA, chat.getEstadoClasificacion());
        assertNotNull(chat.getResultadoTriageJson());
        assertTrue(chat.getResultadoTriageJson().contains("\"nivelPrioridad\":5"));
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.PACIENTE).count());
        assertEquals(1, chat.getMensajes().stream().filter(m -> m.getAutor() == AutorMensaje.BOT).count());
        verify(http, times(3)).send(any(), any());
        verify(chats, times(1)).save(chat);
        verify(queue, times(1)).finalizarTriageEIngresarACola(eq("patient"), any(), anyString(), same(chat));
    }

    private String envelope(String text) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }
}
