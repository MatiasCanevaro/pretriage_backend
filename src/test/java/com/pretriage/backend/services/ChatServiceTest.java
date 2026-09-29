package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.ResumenEntrevistaVoz;
import com.pretriage.backend.controllers.dtos.TurnoVoz;
import com.pretriage.backend.controllers.dtos.TriageAiResponse;
import com.pretriage.backend.controllers.dtos.TriageResultDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.chat.AutorMensaje;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoChat;
import com.pretriage.backend.repositories.RepoPacientes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private RepoChat repoChat;
    private RepoPacientes repoPacientes;
    private AtencionHospitalService atencionHospitalService;
    private ChatService chatService;
    private TriageIaClient triageIaClient;

    @BeforeEach
    void setUp() {
        repoChat = mock(RepoChat.class);
        repoPacientes = mock(RepoPacientes.class);
        atencionHospitalService = mock(AtencionHospitalService.class);
        triageIaClient = mock(TriageIaClient.class);
        chatService = new ChatService(repoChat, repoPacientes, atencionHospitalService, triageIaClient, new ObjectMapper());
    }

    @Test
    void iniciarChatAsociaPacienteYAgregaSaludoInicial() {
        Paciente paciente = new Paciente();
        when(repoPacientes.findByUsuarioAuthId("auth0|paciente")).thenReturn(Optional.of(paciente));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatDTO resultado = chatService.iniciarChat("auth0|paciente");

        assertFalse(resultado.finalizado());
        assertEquals(1, resultado.mensajes().size());
        assertEquals(AutorMensaje.BOT.name(), resultado.mensajes().getFirst().autor());
        verify(repoChat).save(argThat(chat -> chat.getPaciente() == paciente));
    }


    @Test
    void enviarMensajeCuandoLaIaFallaContinuaConPreguntaFallbackLocal() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de cabeza y fiebre desde ayer.", AutorMensaje.PACIENTE, paciente));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("ollama down"));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo dolor de cabeza y fiebre desde ayer.");

        assertFalse(chat.isFinalizado());
        assertEquals("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", resultado.respuesta().contenido());
        verify(repoChat).save(argThat(chatGuardado -> !chatGuardado.isFinalizado()));
    }

    @Test
    void enviarMensajeNoAceptaCierreTempranoSinAlarma() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo fiebre y dolor de cabeza desde ayer.", AutorMensaje.PACIENTE, paciente));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Gracias. Con lo informado, cierro la entrevista y dejo el resumen estructurado.", null));

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo fiebre de 39 grados desde ayer y dolor fuerte de cabeza.");

        assertFalse(chat.isFinalizado());
        assertEquals("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", resultado.respuesta().contenido());
        verify(repoChat).save(argThat(chatGuardado -> !chatGuardado.isFinalizado()));
    }
    @Test
    void enviarMensajeInsisteSiPacienteEsquivaContextoClinicoBasico() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Me siento mal desde hace un rato, tengo dolor de panza y estoy medio mareada.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("No se bien, empezo suave ayer pero hoy me molesta mas. Es como abajo de la panza.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Tenes dificultad para respirar, dolor de pecho, confusion, desmayo, convulsiones o algun empeoramiento importante?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo un poco de nauseas, no vomite. No fui mucho al baño, creo que normal.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Tenes antecedentes relevantes, alergias, tomas alguna medicacion o podria haber embarazo?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("El dolor viene y va, ahora sera un 6 de 10. Caminar me molesta un poco.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Antes de cerrar necesito ese dato: alguna enfermedad previa, alergia, medicacion habitual o posibilidad de embarazo?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Gracias. Con lo informado, cierro la entrevista y dejo el resumen estructurado.", null));

        var resultado = chatService.enviarMensaje("1", "auth0|paciente",
                "No tengo dolor de pecho ni me falta el aire. Me siento debil y tuve algo de temperatura, 37.8.");

        assertFalse(chat.isFinalizado());
        assertEquals("Para completar el pre-triage, respondeme puntualmente: enfermedades previas, alergias, medicacion y posibilidad de embarazo.", resultado.respuesta().contenido());
    }
    @Test
    void enviarMensajeNoCierraSinContextoClinicoBasico() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Me siento mal desde hace un rato, tengo dolor de panza y estoy medio mareada.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("No se bien, empezo suave ayer pero hoy me molesta mas. Es como abajo de la panza.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Tenes dificultad para respirar, dolor de pecho, confusion, desmayo, convulsiones o algun empeoramiento importante?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo un poco de nauseas, no vomite. No fui mucho al baño, creo que normal.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Tenes antecedentes relevantes, alergias, tomas alguna medicacion o podria haber embarazo?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Gracias. Con lo informado, cierro la entrevista y dejo el resumen estructurado.", null));

        var resultado = chatService.enviarMensaje("1", "auth0|paciente",
                "El dolor viene y va, ahora sera un 6 de 10. Caminar me molesta un poco.");

        assertFalse(chat.isFinalizado());
        assertEquals("Antes de cerrar necesito ese dato: alguna enfermedad previa, alergia, medicacion habitual o posibilidad de embarazo?", resultado.respuesta().contenido());
    }
    @Test
    void enviarMensajeNoMarcaAlarmasNegadasComoUrgentes() {
        Paciente paciente = new Paciente();
        paciente.setId(10L);
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo fiebre de 39 grados desde ayer y dolor fuerte de cabeza.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Empezo ayer a la tarde. La fiebre llego a 39 y no baja mucho con paracetamol.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("Tenes dificultad para respirar, dolor de pecho, confusion, desmayo, convulsiones o algun empeoramiento importante?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("ollama down"));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.enviarMensaje("1", "auth0|paciente",
                "No tengo dificultad para respirar, dolor de pecho, confusion, desmayos ni convulsiones. El dolor de cabeza es 7 de 10, en la frente. No tengo enfermedades previas, alergias ni medicacion habitual.");

        assertFalse(chat.isFinalizado());
        assertNull(resultado.atencionEstimada());
        assertEquals("FALLBACK_LOCAL", resultado.origenRespuesta());
        verify(repoChat).save(argThat(chatGuardado -> !chatGuardado.isFinalizado()));
    }
    @Test
    void enviarMensajeCuandoLaIaRepiteLaPreguntaContinuaLaIndagacion() {
        Paciente paciente = new Paciente();
        paciente.setId(10L);
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de cabeza y fiebre.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("¿Cuál es el síntoma más agudo o molestia que estás experimentando?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(false, "¿Cuál es el síntoma más agudo o molestia que estás experimentando?", null));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo dolor de cabeza desde ayer y fiebre de 39, sin dificultad para respirar ni dolor de pecho.");

        assertFalse(chat.isFinalizado());
        assertNull(resultado.atencionEstimada());
        assertEquals("Necesito precisar el dolor: del 0 al 10, cuanto te duele ahora y en que zona lo sentis?", resultado.respuesta().contenido());
        assertEquals(AutorMensaje.BOT.name(), resultado.respuesta().autor());
        assertEquals("FALLBACK_LOCAL", resultado.origenRespuesta());
        verify(repoChat).save(argThat(chatGuardado -> !chatGuardado.isFinalizado()));
    }

    @Test
    void respuestaNoFinalValidaQueRepitePreguntaPideOtroDatoSinCerrar() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(2L);
        String pregunta = "¿Cual es el sintoma principal?";
        chat.agregarMensaje(new Mensaje("Inicio", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor desde ayer.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje(pregunta, AutorMensaje.BOT, null));
        when(repoChat.findByIdAndPacienteUsuarioAuthId(2L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean())).thenReturn(new TriageAiResponse(false, pregunta,
                new TriageResultDTO("dolor", List.of("dolor"), "desde ayer", "igual", null,
                        List.of(), List.of(), List.of(), List.of(), "no informado", "en curso", null,
                        false, "Continua respondiendo las preguntas.")));

        var respuesta = chatService.enviarMensaje("2", "auth0|paciente", "El dolor sigue igual.");

        assertFalse(chat.isFinalizado());
        assertEquals("FALLBACK_LOCAL", respuesta.origenRespuesta());
        assertNotEquals(pregunta, respuesta.respuesta().contenido());
    }

    @Test
    void conservaResultadoFinalValidoDeIaYRegistraSuOrigen() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Inicio", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de garganta desde ayer.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("¿Como evoluciono?", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Empeoro un poco, intensidad 5 de 10.", AutorMensaje.PACIENTE, paciente));

        TriageResultDTO resultadoIa = new TriageResultDTO(
                "dolor de garganta", List.of("dolor de garganta"), "desde ayer", "empeorando", 5,
                List.of(), List.of("ninguno"), List.of("ninguno"), List.of("ninguna"), "no aplica",
                "evaluacion completa", 3, false, "Acudi a una guardia si empeora.");
        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Cierre IA detallado", resultadoIa));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var respuesta = chatService.enviarMensaje("1", "auth0|paciente", "No tengo fiebre ni dificultad para respirar. No tengo enfermedades previas, alergias ni medicacion.");

        assertTrue(chat.isFinalizado());
        assertEquals("Cierre IA detallado", respuesta.respuesta().contenido());
        assertEquals("OLLAMA", respuesta.origenRespuesta());
        assertTrue(chat.getResultadoTriageJson().contains("\"origenClasificacion\":\"OLLAMA\""));
        assertEquals(resultadoIa, new ObjectMapper().readValue(chat.getResultadoTriageJson(), TriageResultDTO.class));
        verify(triageIaClient).consultar(anyString(), contains("Datos aportados por el paciente"), eq(true));
    }

    @Test
    void unTurnoSinInformacionNoSeClasificaComoNoUrgente() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Inicio", AutorMensaje.BOT, null));
        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "final", new TriageResultDTO(
                        "no informado", List.of("no informado"), "no informado", "no informado", null,
                        List.of(), List.of(), List.of(), List.of(), "no informado", "", 1, false, "")));

        var respuesta = chatService.enviarMensaje("1", "auth0|paciente", "No se.");

        assertFalse(chat.isFinalizado());
        assertNull(respuesta.atencionEstimada());
        assertEquals("FALLBACK_LOCAL", respuesta.origenRespuesta());
    }

    @Test
    void aceptaAlarmaReconocidaPorIaDesdeElPrimerTurno() {
        Chat chat = prepararChat();
        TriageResultDTO resultado = resultadoFinal(4, true);
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Busque atencion urgente.", resultado));

        var turno = chatService.enviarMensaje("1", "auth0|paciente", "No puedo respirar.");

        assertTrue(chat.isFinalizado());
        assertEquals("OLLAMA", turno.origenRespuesta());
        assertEquals(resultado, new ObjectMapper().readValue(chat.getResultadoTriageJson(), TriageResultDTO.class));
        verify(atencionHospitalService).finalizarTriageEIngresarACola(eq("auth0|paciente"), eq(NivelDeGravedad.MUY_URGENTE), anyString());
    }

    @Test
    void cierreValidoDeIaEnTercerTurnoNoOmiteContextoFaltante() {
        Chat chat = prepararChat();
        chat.agregarMensaje(new Mensaje("Tengo dolor de garganta desde ayer.", AutorMensaje.PACIENTE, chat.getPaciente()));
        chat.agregarMensaje(new Mensaje("Es 5 de 10.", AutorMensaje.PACIENTE, chat.getPaciente()));
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenReturn(new TriageAiResponse(true, "Listo.", resultadoFinal(3, false)));

        var turno = chatService.enviarMensaje("1", "auth0|paciente", "Sigue igual.");

        assertFalse(chat.isFinalizado());
        assertNull(turno.atencionEstimada());
        verifyNoInteractions(atencionHospitalService);
    }

    @Test
    void preguntasAgotadasNoCierranAntesDelLimiteYLimitePersisteOrigenLocal() {
        Chat chat = prepararChat();
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean())).thenThrow(new RuntimeException("unavailable"));
        for (int turno = 1; turno <= 12; turno++) {
            var respuesta = chatService.enviarMensaje("1", "auth0|paciente", "No se.");
            assertEquals(turno == 12, chat.isFinalizado(), "turno " + turno);
            assertEquals("FALLBACK_LOCAL", respuesta.origenRespuesta());
        }
        var json = new ObjectMapper().readTree(chat.getResultadoTriageJson());
        assertEquals("FALLBACK_LOCAL", json.get("origenClasificacion").asText());
        assertEquals(3, json.get("nivelPrioridad").asInt());
        verify(triageIaClient, times(1)).consultar(anyString(), anyString(), eq(true));
    }

    @Test
    void alarmaLocalIncluyeAvisoUrgenteYResultadoCoherenteSiOllamaFalla() {
        Chat chat = prepararChat();
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean())).thenThrow(new RuntimeException("unavailable"));

        var turno = chatService.enviarMensaje("1", "auth0|paciente", "Tengo confusion desde hace una hora.");

        var resultado = new ObjectMapper().readValue(chat.getResultadoTriageJson(), TriageResultDTO.class);
        assertTrue(chat.isFinalizado());
        assertTrue(resultado.requiereAtencionInmediata());
        assertEquals(5, resultado.nivelPrioridad());
        assertTrue(turno.respuesta().contenido().contains("urgente"));
        assertEquals("FALLBACK_LOCAL", turno.origenRespuesta());
    }

    @Test
    void cierreIaInmediatoMuestraLaRecomendacionAunqueFalteEnElMensaje() {
        Chat chat = prepararChat();
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean())).thenReturn(new TriageAiResponse(
                true,
                "Gracias. Registre tus sintomas y la preclasificacion.",
                new TriageResultDTO("dificultad respiratoria", List.of("dificultad respiratoria"), "ahora",
                        "empeorando", null, List.of("dificultad respiratoria"), List.of(), List.of(), List.of(),
                        "no informado", "signo de alarma", 5, true,
                        "Llama a emergencias de inmediato.")));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var respuesta = chatService.enviarMensaje("1", "auth0|paciente", "No puedo respirar.");

        assertTrue(chat.isFinalizado());
        assertEquals("OLLAMA", respuesta.origenRespuesta());
        assertTrue(respuesta.respuesta().contenido().contains("Llama a emergencias de inmediato."));
        assertEquals(1, respuesta.respuesta().contenido().split("Llama a emergencias de inmediato.", -1).length - 1);
    }

    private Chat prepararChat() {
        Chat chat = new Chat(new Paciente());
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Cual es el motivo de consulta?", AutorMensaje.BOT, null));
        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return chat;
    }

    private TriageResultDTO resultadoFinal(int prioridad, boolean inmediata) {
        return new TriageResultDTO("Sintoma referido", List.of("sintoma referido"), "hoy", "sin cambios", null,
                List.of(), List.of(), List.of(), List.of(), "no informado", "no informado", prioridad,
                inmediata, "Busque atencion presencial.");
    }

    @Test
    void entrevistaDeVozGuardaTranscripcionYConservaClasificacionDeOllama() {
        Paciente paciente = new Paciente();
        Chat chat = chatDeVoz(paciente);
        TriageResultDTO resultadoIa = new TriageResultDTO("dolor de cabeza", List.of("dolor de cabeza", "fiebre"),
                "desde ayer", "se mantiene", 6, List.of(), List.of(), List.of(), List.of(), "no",
                "Niega dificultad respiratoria", 3, false, "Si empeora, consulte a una guardia.");
        when(triageIaClient.consultar(anyString(), anyString(), eq(true)))
                .thenReturn(new TriageAiResponse(true, "Gracias. Registre tus sintomas y la preclasificacion.", resultadoIa));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.finalizarEntrevistaVoz("1", "auth0|paciente", turnosDeVoz(), resumen(List.of()));

        assertTrue(chat.isFinalizado());
        assertEquals("OLLAMA", resultado.origenRespuesta());
        assertEquals(List.of("BOT", "PACIENTE", "BOT", "PACIENTE", "BOT"),
                chat.getMensajes().stream().map(mensaje -> mensaje.getAutor().name()).toList());
        assertTrue(chat.getResultadoTriageJson().contains("\"origenClasificacion\":\"OLLAMA\""));
        verify(triageIaClient).consultar(anyString(), argThat(datos -> datos.contains("Resumen del entrevistador")
                && datos.contains("motivoConsulta: dolor de cabeza con fiebre")
                && datos.contains("Me duele la cabeza y tengo fiebre desde ayer")), eq(true));
        verify(atencionHospitalService).finalizarTriageEIngresarACola(eq("auth0|paciente"), eq(NivelDeGravedad.URGENTE), anyString());
    }

    @Test
    void entrevistaDeVozSinOllamaUsaElResumenYRespetaAlarmasDelEntrevistador() {
        Paciente paciente = new Paciente();
        Chat chat = chatDeVoz(paciente);
        when(triageIaClient.consultar(anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("ollama down"));
        when(atencionHospitalService.finalizarTriageEIngresarACola(eq("auth0|paciente"), any(NivelDeGravedad.class), anyString()))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.finalizarEntrevistaVoz("1", "auth0|paciente", turnosDeVoz(),
                resumen(List.of("rigidez de nuca")));

        assertEquals("FALLBACK_LOCAL", resultado.origenRespuesta());
        assertTrue(chat.getResultadoTriageJson().contains("rigidez de nuca"));
        assertTrue(chat.getResultadoTriageJson().contains("\"motivoConsulta\":\"dolor de cabeza con fiebre\""));
        verify(atencionHospitalService).finalizarTriageEIngresarACola(
                eq("auth0|paciente"), eq(NivelDeGravedad.RIESGO_VITAL_INMEDIATO), anyString());
    }

    @Test
    void entrevistaDeVozRechazaClasificacionSinUrgenciaSiHayAlarma() {
        Paciente paciente = new Paciente();
        chatDeVoz(paciente);
        TriageResultDTO resultadoIa = new TriageResultDTO("dolor de cabeza", List.of("dolor de cabeza"),
                "desde ayer", "se mantiene", 6, List.of(), List.of(), List.of(), List.of(), "no",
                "sin datos", 2, false, "Consulte si empeora.");
        when(triageIaClient.consultar(anyString(), anyString(), eq(true)))
                .thenReturn(new TriageAiResponse(true, "Gracias.", resultadoIa));

        var resultado = chatService.finalizarEntrevistaVoz("1", "auth0|paciente", turnosDeVoz(),
                resumen(List.of("confusion")));

        assertEquals("FALLBACK_LOCAL", resultado.origenRespuesta());
        verify(atencionHospitalService).finalizarTriageEIngresarACola(
                eq("auth0|paciente"), eq(NivelDeGravedad.RIESGO_VITAL_INMEDIATO), anyString());
    }

    @Test
    void registrarTurnosVozGuardaSinFinalizar() {
        Paciente paciente = new Paciente();
        Chat chat = chatDeVoz(paciente);

        chatService.registrarTurnosVoz("1", "auth0|paciente", turnosDeVoz());

        assertFalse(chat.isFinalizado());
        assertEquals(4, chat.getMensajes().size());
        verify(repoChat).save(chat);
        verifyNoInteractions(triageIaClient, atencionHospitalService);
    }

    private Chat chatDeVoz(Paciente paciente) {
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Cual es el principal motivo de tu consulta hoy?", AutorMensaje.BOT, null));
        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return chat;
    }

    private List<TurnoVoz> turnosDeVoz() {
        return List.of(
                new TurnoVoz(AutorMensaje.PACIENTE, "Me duele la cabeza y tengo fiebre desde ayer"),
                new TurnoVoz(AutorMensaje.BOT, "Del 0 al 10, cuanto te duele?"),
                new TurnoVoz(AutorMensaje.PACIENTE, "Un 6. No tengo dificultad para respirar ni alergias."),
                new TurnoVoz(AutorMensaje.BOT, " "));
    }

    private ResumenEntrevistaVoz resumen(List<String> signosAlarma) {
        return new ResumenEntrevistaVoz("dolor de cabeza con fiebre", List.of("dolor de cabeza", "fiebre"),
                "desde ayer", "se mantiene", 6, signosAlarma, List.of(), List.of(), List.of(), "no",
                "Niega dificultad respiratoria y alergias");
    }
}


