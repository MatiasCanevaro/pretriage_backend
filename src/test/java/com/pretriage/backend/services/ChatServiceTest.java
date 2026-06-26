package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.TriageAiResponse;
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
    private ChatService chatService;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        repoChat = mock(RepoChat.class);
        repoPacientes = mock(RepoPacientes.class);
        chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        chatService = new ChatService(repoChat, repoPacientes, builder, new ObjectMapper());
    }

    @Test
    void iniciarChatAsociaPacienteYAgregaSaludoInicial() {
        Paciente paciente = new Paciente();
        when(repoPacientes.findByUsuarioAuthId("auth0|paciente")).thenReturn(Optional.of(paciente));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatDTO resultado = chatService.iniciarChat("auth0|paciente");

        assertFalse(resultado.finalizado());
        assertNull(resultado.resultado());
        assertEquals(1, resultado.mensajes().size());
        assertEquals(AutorMensaje.BOT.name(), resultado.mensajes().getFirst().autor());
        verify(repoChat).save(argThat(chat -> chat.getPaciente() == paciente));
    }


    @Test
    void enviarMensajeCuandoLaIaFallaCierraConFallbackLocal() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de cabeza y fiebre desde ayer.", AutorMensaje.PACIENTE, paciente));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
                .thenThrow(new RuntimeException("ollama down"));

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo dolor de cabeza y fiebre desde ayer.");

        assertTrue(resultado.chat().finalizado());
        assertNotNull(resultado.resultado());
        assertEquals("Gracias. Con lo informado, cierro la entrevista y dejo el resumen estructurado.", resultado.respuesta().contenido());
        assertTrue(resultado.resultado().sintomas().contains("fiebre"));
        verify(repoChat).save(argThat(Chat::isFinalizado));
    }

    @Test
    void enviarMensajeCuandoLaIaRepiteLaPreguntaCierraLaConversacion() {
        Paciente paciente = new Paciente();
        Chat chat = new Chat(paciente);
        chat.setId(1L);
        chat.agregarMensaje(new Mensaje("Hola. Voy a hacerte algunas preguntas breves para registrar tus sintomas.", AutorMensaje.BOT, null));
        chat.agregarMensaje(new Mensaje("Tengo dolor de cabeza y fiebre.", AutorMensaje.PACIENTE, paciente));
        chat.agregarMensaje(new Mensaje("¿Cuál es el síntoma más agudo o molestia que estás experimentando?", AutorMensaje.BOT, null));

        when(repoChat.findByIdAndPacienteUsuarioAuthId(1L, "auth0|paciente")).thenReturn(Optional.of(chat));
        when(repoChat.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(TriageAiResponse.class), any()))
                .thenReturn(new TriageAiResponse(false, "¿Cuál es el síntoma más agudo o molestia que estás experimentando?", null));

        var resultado = chatService.enviarMensaje("1", "auth0|paciente", "Tengo dolor de cabeza desde ayer y fiebre de 39, sin dificultad para respirar ni dolor de pecho.");

        assertTrue(resultado.chat().finalizado());
        assertNotNull(resultado.resultado());
        assertEquals("Gracias. Con lo informado, cierro la entrevista y dejo el resumen estructurado.", resultado.respuesta().contenido());
        assertEquals(AutorMensaje.BOT.name(), resultado.respuesta().autor());
        verify(repoChat).save(argThat(Chat::isFinalizado));
    }
}
