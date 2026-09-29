package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.ChatTurnResponse;
import com.pretriage.backend.controllers.dtos.MensajeDTO;
import com.pretriage.backend.controllers.dtos.ResumenEntrevistaVoz;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SesionVozChatTest {
    private static final String SALUDO = "Hola. Voy a hacerte algunas preguntas breves. Cual es el principal motivo de tu consulta hoy?";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatService chatService;
    private GeminiLiveCliente geminiLiveCliente;
    private ConexionFalsa conexion;
    private CanalFalso canal;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        geminiLiveCliente = mock(GeminiLiveCliente.class);
        conexion = new ConexionFalsa();
        canal = new CanalFalso();
        when(geminiLiveCliente.conectar(any())).thenReturn(CompletableFuture.completedFuture(conexion));
    }

    @Test
    void setupDeclaraLaEntrevistaYLaFuncionDeCierreConElResumen() {
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();

        JsonNode setup = json(conexion.enviados.getFirst()).path("setup");
        assertEquals("models/gemini-3.8-live", setup.path("model").asText());
        assertEquals("AUDIO", setup.path("generationConfig").path("responseModalities").get(0).asText());
        String instruccion = setup.path("systemInstruction").path("parts").get(0).path("text").asText();
        assertTrue(instruccion.contains("signos de alarma"));
        assertTrue(instruccion.contains("finalizar_entrevista"));
        JsonNode funcion = setup.path("tools").get(0).path("functionDeclarations").get(0);
        assertEquals(SesionVozChat.FUNCION_FINALIZAR, funcion.path("name").asText());
        assertTrue(funcion.path("parameters").path("properties").has("signosAlarma"));
        assertTrue(funcion.path("parameters").path("properties").has("intensidadDolor"));
        assertFalse(funcion.path("parameters").path("properties").has("nivelPrioridad"));
        assertTrue(setup.has("inputAudioTranscription"));
        assertTrue(setup.has("outputAudioTranscription"));
    }

    @Test
    void chatNuevoArrancaConElSaludoDelBot() {
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();

        sesion.alRecibir("{\"setupComplete\":{}}");

        assertTrue(textoCliente(conexion.enviados.get(1)).contains(SALUDO));
        assertEquals("listo", json(canal.eventos.getFirst()).path("tipo").asText());
    }

    @Test
    void chatEmpezadoPorTextoRetomaConLaConversacionPrevia() {
        List<MensajeDTO> historial = new ArrayList<>(historialNuevo());
        historial.add(new MensajeDTO("Me duele la panza", "PACIENTE", LocalDateTime.now()));
        SesionVozChat sesion = sesion(historial);
        sesion.iniciar();

        sesion.alRecibir("{\"setupComplete\":{}}");

        String inicio = textoCliente(conexion.enviados.get(1));
        assertTrue(inicio.contains("PACIENTE: Me duele la panza"));
        assertTrue(inicio.contains("sin repetir"));
    }

    @Test
    void reenviaAudioDelClienteSoloDespuesDelSetup() {
        SesionVozChat sesion = sesion(historialNuevo());
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
        SesionVozChat sesion = sesion(historialNuevo());
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
    void alFinalizarCierraGeminiYRecienEntoncesPideLaPreclasificacionAOllama() {
        TiempoEstimadoAtencionResponse atencion = new TiempoEstimadoAtencionResponse();
        atencion.setConsultaId(10L);
        when(chatService.finalizarEntrevistaVoz(eq("7"), eq("auth0|paciente"), anyList(), any()))
                .thenReturn(new ChatTurnResponse(new MensajeDTO("Gracias. Registre tus sintomas.", "BOT",
                        LocalDateTime.now()), atencion, "OLLAMA"));
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();
        sesion.alRecibir("{\"setupComplete\":{}}");

        salida(sesion, SALUDO);
        turnoCompleto(sesion);
        entrada(sesion, "Me duele la cabeza ");
        entrada(sesion, "desde ayer.");
        salida(sesion, "Del 0 al 10, cuanto te duele?");
        turnoCompleto(sesion);
        entrada(sesion, "Un 6, no tengo alergias.");
        sesion.alRecibir(llamadaFinalizar("llamada-1", List.of()));

        JsonNode respuesta = json(conexion.enviados.getLast()).path("toolResponse").path("functionResponses").get(0);
        assertEquals("llamada-1", respuesta.path("id").asText());
        assertEquals(SesionVozChat.MENSAJE_DESPEDIDA, respuesta.path("response").path("mensaje").asText());
        assertTrue(canal.eventos.stream().anyMatch(e -> e.contains("entrevista_finalizada")));
        verify(chatService, never()).finalizarEntrevistaVoz(anyString(), anyString(), anyList(), any());

        entrada(sesion, " y tomo ibuprofeno");
        salida(sesion, SesionVozChat.MENSAJE_DESPEDIDA);
        turnoCompleto(sesion);

        esperar(() -> canal.cerrado);
        assertTrue(conexion.cerrada);
        ArgumentCaptor<List<TurnoVoz>> turnos = ArgumentCaptor.captor();
        ArgumentCaptor<ResumenEntrevistaVoz> resumen = ArgumentCaptor.captor();
        verify(chatService).finalizarEntrevistaVoz(eq("7"), eq("auth0|paciente"), turnos.capture(), resumen.capture());
        assertEquals(List.of(
                new TurnoVoz(AutorMensaje.PACIENTE, "Me duele la cabeza desde ayer."),
                new TurnoVoz(AutorMensaje.BOT, "Del 0 al 10, cuanto te duele?"),
                new TurnoVoz(AutorMensaje.PACIENTE, "Un 6, no tengo alergias."),
                new TurnoVoz(AutorMensaje.PACIENTE, "y tomo ibuprofeno")), turnos.getValue());
        assertEquals("dolor de cabeza", resumen.getValue().motivoConsulta());
        assertEquals(6, resumen.getValue().intensidadDolor());

        JsonNode triage = canal.eventos.stream().map(this::json)
                .filter(e -> "triage_finalizado".equals(e.path("tipo").asText())).findFirst().orElseThrow();
        assertEquals(10L, triage.path("atencionEstimada").path("consultaId").asLong());
        assertEquals("OLLAMA", triage.path("origenRespuesta").asText());
        assertEquals("fin", json(canal.eventos.getLast()).path("tipo").asText());
        verify(chatService, never()).registrarTurnosVoz(anyString(), anyString(), anyList());
    }

    @Test
    void noPermiteFinalizarSinRespuestasDelPaciente() {
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();

        sesion.alRecibir(llamadaFinalizar("llamada-1", List.of()));

        JsonNode respuesta = json(conexion.enviados.getLast()).path("toolResponse").path("functionResponses").get(0);
        assertTrue(respuesta.path("response").has("error"));
        assertTrue(canal.eventos.stream().noneMatch(e -> e.contains("entrevista_finalizada")));
    }

    @Test
    void siElPacienteCortaAntesDeTerminarGuardaLaTranscripcionSinClasificar() {
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();
        entrada(sesion, "Tengo fiebre");
        salida(sesion, "Desde cuando?");

        sesion.recibirControlCliente("{\"tipo\":\"cerrar\"}");

        verify(chatService, timeout(2_000)).registrarTurnosVoz("7", "auth0|paciente", List.of(
                new TurnoVoz(AutorMensaje.PACIENTE, "Tengo fiebre"),
                new TurnoVoz(AutorMensaje.BOT, "Desde cuando?")));
        verify(chatService, never()).finalizarEntrevistaVoz(anyString(), anyString(), anyList(), any());
        assertTrue(conexion.cerrada);
        assertTrue(canal.cerrado);
    }

    @Test
    void siFallaLaPreclasificacionAvisaYConservaLaTranscripcion() {
        when(chatService.finalizarEntrevistaVoz(anyString(), anyString(), anyList(), any()))
                .thenThrow(new IllegalStateException("sin cola"));
        SesionVozChat sesion = sesion(historialNuevo());
        sesion.iniciar();
        entrada(sesion, "Me duele la cabeza");
        sesion.alRecibir(llamadaFinalizar("llamada-1", List.of()));

        sesion.alCerrar(1000, "fin");

        esperar(() -> canal.cerrado);
        assertTrue(canal.eventos.stream().anyMatch(e -> e.contains("\"error\"")));
        verify(chatService).registrarTurnosVoz("7", "auth0|paciente",
                List.of(new TurnoVoz(AutorMensaje.PACIENTE, "Me duele la cabeza")));
    }

    @Test
    void alLlegarAlLimiteDeRespuestasPideCerrarLaEntrevista() {
        List<MensajeDTO> historial = new ArrayList<>(historialNuevo());
        IntStream.range(0, ChatService.MAX_MENSAJES_PACIENTE - 1).forEach(i ->
                historial.add(new MensajeDTO("respuesta " + i, "PACIENTE", LocalDateTime.now())));
        SesionVozChat sesion = sesion(historial);
        sesion.iniciar();

        entrada(sesion, "Ultima respuesta");
        turnoCompleto(sesion);

        assertTrue(textoCliente(conexion.enviados.getLast()).contains("limite de respuestas"));
    }

    @Test
    void falloDeConexionInformaErrorYCierraElCanal() {
        when(geminiLiveCliente.conectar(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sin red")));

        sesion(historialNuevo()).iniciar();

        assertEquals("error", json(canal.eventos.getFirst()).path("tipo").asText());
        assertTrue(canal.cerrado);
    }

    private SesionVozChat sesion(List<MensajeDTO> historial) {
        GeminiLiveProperties properties = new GeminiLiveProperties(
                "clave", "models/gemini-3.8-live", "wss://gemini.test", "Kore", "es-US", Duration.ofSeconds(1));
        return new SesionVozChat("7", "auth0|paciente", historial, chatService,
                geminiLiveCliente, properties, objectMapper, canal);
    }

    private List<MensajeDTO> historialNuevo() {
        return List.of(new MensajeDTO(SALUDO, "BOT", LocalDateTime.now()));
    }

    private void entrada(SesionVozChat sesion, String texto) {
        sesion.alRecibir(serverContent("inputTranscription", texto));
    }

    private void salida(SesionVozChat sesion, String texto) {
        sesion.alRecibir(serverContent("outputTranscription", texto));
    }

    private void turnoCompleto(SesionVozChat sesion) {
        sesion.alRecibir("{\"serverContent\":{\"turnComplete\":true}}");
    }

    private String serverContent(String campo, String texto) {
        ObjectNode mensaje = objectMapper.createObjectNode();
        mensaje.putObject("serverContent").putObject(campo).put("text", texto);
        return mensaje.toString();
    }

    private String llamadaFinalizar(String id, List<String> signosAlarma) {
        ObjectNode mensaje = objectMapper.createObjectNode();
        ObjectNode llamada = mensaje.putObject("toolCall").putArray("functionCalls").addObject();
        llamada.put("id", id);
        llamada.put("name", SesionVozChat.FUNCION_FINALIZAR);
        ObjectNode args = llamada.putObject("args");
        args.put("motivoConsulta", "dolor de cabeza");
        args.putArray("sintomas").add("dolor de cabeza");
        args.put("inicio", "desde ayer");
        args.put("evolucion", "no informado");
        args.put("intensidadDolor", 6);
        signosAlarma.forEach(args.putArray("signosAlarma")::add);
        args.putArray("antecedentesRelevantes");
        args.putArray("medicamentos");
        args.putArray("alergias");
        args.put("posibilidadEmbarazo", "no informado");
        args.put("observaciones", "Niega alergias");
        return mensaje.toString();
    }

    private String textoCliente(String mensaje) {
        return json(mensaje).path("clientContent").path("turns").get(0).path("parts").get(0).path("text").asText();
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
