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
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
