package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.ChatTurnResponse;
import com.pretriage.backend.controllers.dtos.MensajeDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.ChatFinalizadoException;
import com.pretriage.backend.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SesionVozChatTest {
    private static final String MENSAJE_INICIAL = "Cual es el principal motivo de tu consulta hoy?";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatService chatService;
    private GeminiLiveCliente geminiLiveCliente;
    private ConexionFalsa conexion;
    private CanalFalso canal;
    private SesionVozChat sesion;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        geminiLiveCliente = mock(GeminiLiveCliente.class);
        conexion = new ConexionFalsa();
        canal = new CanalFalso();
        when(geminiLiveCliente.conectar(any())).thenReturn(CompletableFuture.completedFuture(conexion));
        GeminiLiveProperties properties = new GeminiLiveProperties(
                "clave", "models/gemini-3.8-live", "wss://gemini.test", "Kore", "es-US", Duration.ofSeconds(1));
        sesion = new SesionVozChat("7", "auth0|paciente", MENSAJE_INICIAL, chatService,
                geminiLiveCliente, properties, objectMapper, canal);
    }

    @Test
    void iniciarEnviaSetupConModeloFuncionYTranscripciones() {
        sesion.iniciar();

        JsonNode setup = json(conexion.enviados.getFirst()).path("setup");
        assertEquals("models/gemini-3.8-live", setup.path("model").asText());
        assertEquals("AUDIO", setup.path("generationConfig").path("responseModalities").get(0).asText());
        assertEquals("Kore", setup.path("generationConfig").path("speechConfig")
                .path("voiceConfig").path("prebuiltVoiceConfig").path("voiceName").asText());
        assertEquals(SesionVozChat.FUNCION_REGISTRAR, setup.path("tools").get(0)
                .path("functionDeclarations").get(0).path("name").asText());
        assertTrue(setup.has("inputAudioTranscription"));
        assertTrue(setup.has("outputAudioTranscription"));
    }

    @Test
    void alCompletarSetupLeeLaUltimaPreguntaDelBotYAvisaAlCliente() {
        sesion.iniciar();

        sesion.alRecibir("{\"setupComplete\":{}}");

        JsonNode saludo = json(conexion.enviados.get(1)).path("clientContent");
        assertTrue(saludo.path("turns").get(0).path("parts").get(0).path("text").asText().contains(MENSAJE_INICIAL));
        assertTrue(saludo.path("turnComplete").asBoolean());
        assertEquals("listo", json(canal.eventos.getFirst()).path("tipo").asText());
    }

    @Test
    void reenviaAudioDelClienteSoloDespuesDelSetup() {
        sesion.iniciar();
        byte[] audio = {1, 2, 3, 4};

        sesion.recibirAudioCliente(audio);
        assertEquals(1, conexion.enviados.size());

        sesion.alRecibir("{\"setupComplete\":{}}");
        sesion.recibirAudioCliente(audio);

        JsonNode entrada = json(conexion.enviados.getLast()).path("realtimeInput").path("audio");
        assertEquals(SesionVozChat.MIME_AUDIO_ENTRADA, entrada.path("mimeType").asText());
        assertArrayEquals(audio, Base64.getDecoder().decode(entrada.path("data").asText()));
    }

    @Test
    void reenviaAudioYTranscripcionesDeGeminiAlCliente() {
        sesion.iniciar();
        String audio = Base64.getEncoder().encodeToString("pcm".getBytes(StandardCharsets.UTF_8));

        sesion.alRecibir("""
                {"serverContent":{
                  "modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"%s"}}]},
                  "inputTranscription":{"text":"me duele la cabeza"},
                  "outputTranscription":{"text":"Desde cuando?"}}}
                """.formatted(audio));

        assertArrayEquals("pcm".getBytes(StandardCharsets.UTF_8), canal.audios.getFirst());
        assertEquals("transcripcion_paciente", json(canal.eventos.get(0)).path("tipo").asText());
        assertEquals("me duele la cabeza", json(canal.eventos.get(0)).path("texto").asText());
        assertEquals("transcripcion_bot", json(canal.eventos.get(1)).path("tipo").asText());
    }

    @Test
    void llamadaDeFuncionPasaPorElBotExistenteYDevuelveSuRespuesta() {
        when(chatService.enviarMensaje("7", "auth0|paciente", "me duele la cabeza desde ayer"))
                .thenReturn(turno("Del 0 al 10, cuanto te duele?", null));
        when(chatService.obtenerChat("7", "auth0|paciente")).thenReturn(chat(false));
        sesion.iniciar();

        sesion.alRecibir(llamada("llamada-1", "me duele la cabeza desde ayer"));

        esperar(() -> conexion.enviados.stream().anyMatch(m -> m.contains("toolResponse")));
        JsonNode respuesta = json(conexion.enviados.getLast()).path("toolResponse").path("functionResponses").get(0);
        assertEquals("llamada-1", respuesta.path("id").asText());
        assertEquals("Del 0 al 10, cuanto te duele?", respuesta.path("response").path("respuesta").asText());
        assertFalse(respuesta.path("response").path("finalizado").asBoolean());
        JsonNode turnoBot = json(canal.eventos.getLast());
        assertEquals("turno_bot", turnoBot.path("tipo").asText());
        assertEquals("OLLAMA", turnoBot.path("origenRespuesta").asText());
    }

    @Test
    void alFinalizarElTriageCierraLaSesionDespuesDeLeerElCierre() {
        TiempoEstimadoAtencionResponse atencion = new TiempoEstimadoAtencionResponse();
        atencion.setConsultaId(10L);
        when(chatService.enviarMensaje(anyString(), anyString(), anyString()))
                .thenReturn(turno("Gracias. Registre tus sintomas.", atencion));
        when(chatService.obtenerChat("7", "auth0|paciente")).thenReturn(chat(true));
        sesion.iniciar();

        sesion.alRecibir(llamada("llamada-1", "no tengo alergias ni tomo medicacion"));
        esperar(() -> conexion.enviados.stream().anyMatch(m -> m.contains("toolResponse")));
        JsonNode turnoBot = json(canal.eventos.getLast());
        assertTrue(turnoBot.path("finalizado").asBoolean());
        assertEquals(10L, turnoBot.path("atencionEstimada").path("consultaId").asLong());

        sesion.alRecibir("{\"serverContent\":{\"turnComplete\":true}}");

        assertEquals("fin", json(canal.eventos.getLast()).path("tipo").asText());
        assertTrue(conexion.cerrada);
        assertTrue(canal.cerrado);
    }

    @Test
    void textoVacioDevuelveErrorSinRegistrarMensaje() {
        sesion.iniciar();

        sesion.alRecibir(llamada("llamada-1", "  "));

        esperar(() -> conexion.enviados.stream().anyMatch(m -> m.contains("toolResponse")));
        assertTrue(json(conexion.enviados.getLast()).path("toolResponse").path("functionResponses")
                .get(0).path("response").has("error"));
        verify(chatService, never()).enviarMensaje(anyString(), anyString(), anyString());
    }

    @Test
    void chatYaFinalizadoCierraSinPedirMasDatos() {
        when(chatService.enviarMensaje(anyString(), anyString(), anyString()))
                .thenThrow(new ChatFinalizadoException());
        sesion.iniciar();

        sesion.alRecibir(llamada("llamada-1", "hola"));

        esperar(() -> conexion.enviados.stream().anyMatch(m -> m.contains("toolResponse")));
        assertTrue(json(conexion.enviados.getLast()).path("toolResponse").path("functionResponses")
                .get(0).path("response").path("finalizado").asBoolean());
    }

    @Test
    void falloDeConexionInformaErrorYCierraElCanal() {
        when(geminiLiveCliente.conectar(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sin red")));

        sesion.iniciar();

        assertEquals("error", json(canal.eventos.getFirst()).path("tipo").asText());
        assertTrue(canal.cerrado);
    }

    private String llamada(String id, String texto) {
        return objectMapper.createObjectNode().set("toolCall", objectMapper.createObjectNode()
                .set("functionCalls", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                        .put("id", id)
                        .put("name", SesionVozChat.FUNCION_REGISTRAR)
                        .set("args", objectMapper.createObjectNode().put("texto", texto))))).toString();
    }

    private ChatTurnResponse turno(String contenido, TiempoEstimadoAtencionResponse atencion) {
        return new ChatTurnResponse(new MensajeDTO(contenido, "BOT", LocalDateTime.now()), atencion, "OLLAMA");
    }

    private ChatDTO chat(boolean finalizado) {
        return new ChatDTO(7L, List.of(), LocalDateTime.now(), finalizado);
    }

    private JsonNode json(String texto) {
        return objectMapper.readTree(texto);
    }

    private void esperar(BooleanSupplier condicion) {
        long limite = System.currentTimeMillis() + 2_000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                fail("La condicion no se cumplio a tiempo");
            }
            Thread.onSpinWait();
        }
    }

    private static final class ConexionFalsa implements GeminiLiveConexion {
        private final List<String> enviados = new CopyOnWriteArrayList<>();
        private volatile boolean cerrada;

        @Override
        public void enviar(String mensajeJson) {
            enviados.add(mensajeJson);
        }

        @Override
        public void cerrar() {
            cerrada = true;
        }
    }

    private static final class CanalFalso implements CanalVozCliente {
        private final List<String> eventos = new CopyOnWriteArrayList<>();
        private final List<byte[]> audios = new CopyOnWriteArrayList<>();
        private volatile boolean cerrado;

        @Override
        public void enviarEvento(String eventoJson) {
            eventos.add(eventoJson);
        }

        @Override
        public void enviarAudio(byte[] audioPcm) {
            audios.add(audioPcm);
        }

        @Override
        public void cerrar() {
            cerrado = true;
        }
    }
}
