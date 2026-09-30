package com.pretriage.backend.services.voz;

import com.pretriage.backend.controllers.dtos.*;
import com.pretriage.backend.model.chat.*;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoChat;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.services.AtencionHospitalService;
import com.pretriage.backend.services.ChatService;
import com.pretriage.backend.services.TriageIaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises session, close validation and ChatService together, without external providers. */
@Timeout(10)
class SesionVozIntegracionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resumenVacioNoCierraYLaEntrevistaPuedeContinuarHastaGuardarUnaClasificacion() throws Exception {
        Flujo flujo = new Flujo();
        flujo.entrada("Me duele la cabeza desde ayer.");
        flujo.finalizar(mapper.createObjectNode());

        assertFalse(flujo.chat.isFinalizado());
        assertFalse(flujo.canal.contiene("entrevista_finalizada"));
        verifyNoInteractions(flujo.ia);
        verify(flujo.repo, never()).save(any());

        flujo.completarEntrevista();
        flujo.finalizar(resumen());
        flujo.terminarGemini();

        assertTrue(flujo.chat.isFinalizado());
        assertTrue(flujo.canal.contiene("triage_finalizado"));
        assertEquals(4, flujo.chat.getMensajes().stream()
                .filter(mensaje -> mensaje.getAutor() == AutorMensaje.PACIENTE).count());
        verify(flujo.ia, times(1)).consultar(anyString(), anyString(), eq(true));
        verify(flujo.repo, times(1)).save(flujo.chat);
    }

    @Test
    void correccionDeDolorTardiaLlegaAlFallbackAntesDeActualizarLaColaUnaSolaVez() throws Exception {
        Flujo flujo = new Flujo();
        flujo.entrada("Me duele la cabeza desde ayer.");
        flujo.completarEntrevista();
        flujo.finalizar(resumen());
        flujo.transcripcion("outputTranscription", SesionVozChat.MENSAJE_DESPEDIDA);
        flujo.sesion.alRecibir("{\"serverContent\":{\"turnComplete\":true}}");

        assertFalse(flujo.chat.isFinalizado());
        verifyNoInteractions(flujo.ia);
        flujo.entrada("El dolor ahora es 8/10.");
        assertTrue(flujo.conexion.cierreSolicitado.await(5, TimeUnit.SECONDS));
        verifyNoInteractions(flujo.ia);

        flujo.sesion.alCerrar(1000, "fin");
        assertTrue(flujo.canal.cerrado.await(3, TimeUnit.SECONDS));
        flujo.sesion.alCerrar(1000, "duplicado");

        var resultado = mapper.readTree(flujo.chat.getResultadoTriageJson());
        assertEquals(8, resultado.path("intensidadDolor").intValue());
        assertTrue(resultado.path("inicio").asText().contains("ayer"));
        assertEquals(4, resultado.path("nivelPrioridad").intValue());
        assertEquals("FALLBACK_LOCAL", resultado.path("origenClasificacion").asText());
        assertTrue(flujo.chat.getMensajes().stream().anyMatch(m -> m.getContenido().contains("ahora es 8/10")));
        verify(flujo.ia, times(1)).consultar(anyString(), anyString(), eq(true));
        verify(flujo.cola, times(1)).finalizarTriageEIngresarACola(eq("auth0|voz"), any(), anyString());
        verify(flujo.repo, times(1)).save(flujo.chat);
    }

    private ResumenEntrevistaVoz resumen() {
        return new ResumenEntrevistaVoz("dolor de cabeza", List.of("dolor de cabeza"),
                "hoy", "se mantiene", 2, List.of(), List.of(), List.of(), List.of(),
                "no informado", "Niega signos de alarma, antecedentes, medicacion y alergias.");
    }

    private final class Flujo {
        final Chat chat = new Chat(new Paciente());
        final RepoChat repo = mock(RepoChat.class);
        final TriageIaClient ia = mock(TriageIaClient.class);
        final AtencionHospitalService cola = mock(AtencionHospitalService.class);
        final Conexion conexion = new Conexion();
        final Canal canal = new Canal();
        final SesionVozChat sesion;

        Flujo() {
            chat.setId(7L);
            chat.agregarMensaje(new Mensaje("Cual es el motivo?", AutorMensaje.BOT, null));
            when(repo.findByIdAndPacienteUsuarioAuthId(7L, "auth0|voz")).thenReturn(Optional.of(chat));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ia.consultar(anyString(), anyString(), anyBoolean()))
                    .thenThrow(new IllegalStateException("Ollama no disponible en este escenario"));
            when(cola.finalizarTriageEIngresarACola(anyString(), any(), anyString()))
                    .thenReturn(new TiempoEstimadoAtencionResponse());
            ChatService service = new ChatService(repo, mock(RepoPacientes.class), cola, ia, mapper);
            GeminiLiveCliente gemini = mock(GeminiLiveCliente.class);
            when(gemini.conectar(any())).thenReturn(CompletableFuture.completedFuture(conexion));
            sesion = new SesionVozChat("7", "auth0|voz",
                    List.of(new MensajeDTO("Cual es el motivo?", "BOT", LocalDateTime.now())), service,
                    gemini, new GeminiLiveProperties("test", "test", "wss://example.invalid", "Kore",
                    "es-US", Duration.ofSeconds(1)), mapper, canal);
            sesion.iniciar();
            sesion.alRecibir("{\"setupComplete\":{}}");
        }

        void entrada(String texto) {
            transcripcion("inputTranscription", texto);
        }

        void transcripcion(String tipo, String texto) {
            var evento = mapper.createObjectNode();
            evento.putObject("serverContent").putObject(tipo).put("text", texto);
            sesion.alRecibir(evento.toString());
        }

        void completarEntrevista() {
            transcripcion("outputTranscription", "Cuanto te duele?");
            entrada("El dolor es 2/10 y se mantiene.");
            transcripcion("outputTranscription", "Hay signos de alarma?");
            entrada("No tengo dificultad para respirar, dolor de pecho, desmayos ni confusion.");
            transcripcion("outputTranscription", "Antecedentes, medicacion o alergias?");
            entrada("No tengo antecedentes, no tomo medicamentos y no tengo alergias.");
        }

        void finalizar(Object resumen) {
            var evento = mapper.createObjectNode();
            var call = evento.putObject("toolCall").putArray("functionCalls").addObject();
            call.put("id", "fin-voz").put("name", "finalizar_entrevista");
            call.set("args", mapper.valueToTree(resumen));
            sesion.alRecibir(evento.toString());
        }

        void terminarGemini() throws Exception {
            transcripcion("outputTranscription", SesionVozChat.MENSAJE_DESPEDIDA);
            sesion.alRecibir("{\"serverContent\":{\"turnComplete\":true}}");
            assertTrue(conexion.cierreSolicitado.await(5, TimeUnit.SECONDS));
            sesion.alCerrar(1000, "fin");
            assertTrue(canal.cerrado.await(3, TimeUnit.SECONDS));
        }
    }

    private static final class Conexion implements GeminiLiveConexion {
        final CountDownLatch cierreSolicitado = new CountDownLatch(1);
        public void enviar(String mensajeJson) { }
        public void cerrar() { cierreSolicitado.countDown(); }
    }

    private final class Canal implements CanalVozCliente {
        final List<String> eventos = new CopyOnWriteArrayList<>();
        final CountDownLatch cerrado = new CountDownLatch(1);
        public void enviarEvento(String eventoJson) { eventos.add(eventoJson); }
        public void enviarAudio(byte[] audioPcm) { }
        public void cerrar() { cerrado.countDown(); }
        boolean contiene(String tipo) {
            return eventos.stream().anyMatch(e -> tipo.equals(mapper.readTree(e).path("tipo").asText()));
        }
    }
}
