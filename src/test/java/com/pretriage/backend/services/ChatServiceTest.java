package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.TriageAiResponse;
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
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private RepoChat repoChat;
    private RepoPacientes repoPacientes;
    private AtencionHospitalService atencionHospitalService;
    private ChatService chatService;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        repoChat = mock(RepoChat.class);
        repoPacientes = mock(RepoPacientes.class);
        atencionHospitalService = mock(AtencionHospitalService.class);
        chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        chatService = new ChatService(repoChat, repoPacientes, atencionHospitalService, builder, new ObjectMapper());
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
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
                .thenThrow(new RuntimeException("ollama down"));
        when(atencionHospitalService.finalizarTriageEIngresarACola("auth0|paciente", NivelDeGravedad.URGENTE))
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
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
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
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
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
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
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
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
                .thenThrow(new RuntimeException("ollama down"));
        when(atencionHospitalService.finalizarTriageEIngresarACola("auth0|paciente", NivelDeGravedad.URGENTE))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.enviarMensaje("1", "auth0|paciente",
                "No tengo dificultad para respirar, dolor de pecho, confusion, desmayos ni convulsiones. El dolor de cabeza es 7 de 10, en la frente. No tengo enfermedades previas, alergias ni medicacion habitual.");

        assertNotNull(resultado.atencionEstimada());
        verify(repoChat).save(argThat(Chat::isFinalizado));
    }
    @Test
    void enviarMensajeCuandoLaIaRepiteLaPreguntaCierraLaConversacion() {
        Paciente paciente = new Paciente();
        paciente.setId(10L);
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de cabeza y fiebre.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("¿Cuál es el síntoma más agudo o molestia que estás experimentando?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
                .thenReturn(new TriageAiResponse(false, "¿Cuál es el síntoma más agudo o molestia que estás experimentando?", null));
        when(atencionHospitalService.finalizarTriageEIngresarACola("auth0|paciente", NivelDeGravedad.URGENTE))
                .thenReturn(new TiempoEstimadoAtencionResponse());

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo dolor de cabeza desde ayer y fiebre de 39, sin dificultad para respirar ni dolor de pecho.");

        assertTrue(chat.isFinalizado());
        assertNotNull(resultado.atencionEstimada());
        assertEquals("Gracias. Ya registre tus respuestas.", resultado.respuesta().contenido());
        assertEquals(AutorMensaje.BOT.name(), resultado.respuesta().autor());
        verify(repoChat).save(argThat(Chat::isFinalizado));
    }
}


