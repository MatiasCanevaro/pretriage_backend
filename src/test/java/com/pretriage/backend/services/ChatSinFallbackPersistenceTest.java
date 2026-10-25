package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.*;
import com.pretriage.backend.exceptions.ProveedorIaException;
import com.pretriage.backend.model.chat.AutorMensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatSinFallbackPersistenceTest {
    @Autowired ChatService chatService;
    @Autowired JdbcTemplate db;
    @MockitoBean TriageIaClient proveedor;
    @MockitoBean AtencionHospitalService cola;
    String pacienteAuth;
    String chatId;

    @BeforeEach
    void crearPacienteYChat() {
        pacienteAuth = "test-no-fallback|" + UUID.randomUUID();
        db.update("INSERT INTO usuario_auth (id, nombre, apellido, numero_documento, tipo_documento, correo_electronico) VALUES (?, 'Paciente', 'Prueba', ?, 'DNI', ?)",
                pacienteAuth, UUID.randomUUID().toString(), UUID.randomUUID() + "@example.invalid");
        db.update("INSERT INTO paciente (auth_id) VALUES (?)", pacienteAuth);
        chatId = chatService.iniciarChat(pacienteAuth).id().toString();
    }

    @AfterEach
    void limpiarSoloPacienteDePrueba() {
        db.update("DELETE FROM mensaje WHERE chat_id IN (SELECT c.id FROM chat c JOIN paciente p ON c.paciente_id=p.id WHERE p.auth_id=?)", pacienteAuth);
        db.update("DELETE FROM chat WHERE paciente_id IN (SELECT id FROM paciente WHERE auth_id=?)", pacienteAuth);
        db.update("DELETE FROM paciente WHERE auth_id=?", pacienteAuth);
        db.update("DELETE FROM usuario_auth WHERE id=?", pacienteAuth);
    }

    @Test
    void falloTextoRevierteElMensajeYReintentoNoDuplicaHistorial() {
        when(proveedor.consultarContinuacion(anyString(), anyString(), any()))
                .thenThrow(new ProveedorIaException(null))
                .thenReturn(new TriageAiResponse(false, "¿Querés agregar algo más?", null));
        assertThrows(ProveedorIaException.class, () -> chatService.enviarMensaje(chatId, pacienteAuth, "Tengo fiebre"));
        assertSinResultadoNiMensajesNuevos();

        var respuesta = chatService.enviarMensaje(chatId, pacienteAuth, "Tengo fiebre");
        assertEquals("GEMINI", respuesta.origenRespuesta());
        assertEquals(3, chatService.obtenerChat(chatId, pacienteAuth).mensajes().size());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM mensaje WHERE chat_id=? AND autor='PACIENTE'", Integer.class, Long.valueOf(chatId)));
    }

    @Test
    void falloClasificacionVozPersisteCierreYTranscripcionSinResultadoNiDuplicados() {
        when(proveedor.consultar(anyString(), anyString(), eq(true))).thenThrow(new ProveedorIaException(null));
        var turnos = List.of(new TurnoVoz(AutorMensaje.PACIENTE, "Dolor de cabeza desde ayer, 8 de 10"),
                new TurnoVoz(AutorMensaje.PACIENTE, "No tengo dificultad para respirar ni dolor de pecho"),
                new TurnoVoz(AutorMensaje.PACIENTE, "No tengo enfermedades, alergias ni medicacion"),
                new TurnoVoz(AutorMensaje.BOT, "¿Hay algo más que quieras contar sobre lo que te pasa?"),
                new TurnoVoz(AutorMensaje.PACIENTE, "No, no tengo más síntomas."));
        var resumen = new ResumenEntrevistaVoz("dolor de cabeza", List.of("dolor de cabeza"), "ayer", "igual", 8,
                List.of(), List.of(), List.of(), List.of(), "no informado", "Caso sintetico");
        assertThrows(ProveedorIaException.class, () -> chatService.finalizarEntrevistaVoz(chatId, pacienteAuth, turnos, resumen));
        assertTrue(chatService.obtenerChat(chatId, pacienteAuth).finalizado());
        assertEquals(com.pretriage.backend.model.chat.EstadoClasificacionChat.FALLIDA,
                chatService.obtenerChat(chatId, pacienteAuth).estadoClasificacion());
        chatService.registrarCierreVozFallido(chatId, pacienteAuth, turnos);
        var historial = chatService.obtenerChat(chatId, pacienteAuth).mensajes();
        assertEquals(6, historial.size()); // greeting + three clinical turns + invitation + its answer
        assertEquals(1, historial.stream().filter(m -> m.contenido()
                .equals("¿Hay algo más que quieras contar sobre lo que te pasa?")).count());
        assertEquals(1, historial.stream().filter(m -> m.contenido()
                .equals("No, no tengo más síntomas.")).count());
        assertNull(db.queryForObject("SELECT resultado_triage_json FROM chat WHERE id=?", String.class, Long.valueOf(chatId)));
        verifyNoInteractions(cola);
    }

    @Test
    void falloTextoCierraYReintentoSinMensajeActualizaColaUnaVez() {
        when(proveedor.consultar(anyString(), anyString(), eq(true))).thenThrow(new ProveedorIaException(null));
        assertThrows(ProveedorIaException.class, () -> chatService.enviarMensaje(chatId, pacienteAuth, "Tengo dolor de pecho"));
        var cerrado = chatService.obtenerChat(chatId, pacienteAuth);
        assertTrue(cerrado.finalizado());
        assertEquals(com.pretriage.backend.model.chat.EstadoClasificacionChat.FALLIDA, cerrado.estadoClasificacion());
        assertEquals(2, cerrado.mensajes().size());
        when(proveedor.consultar(anyString(), anyString(), eq(true))).thenReturn(new TriageAiResponse(true, "Gracias",
                new TriageResultDTO("dolor de pecho", List.of("dolor de pecho"), "no informado", "no informado", null,
                        List.of("dolor toracico"), List.of(), List.of(), List.of(), "no informado", "no informado", 4, true, "Acudir a guardia")));
        chatService.reintentarClasificacion(chatId, pacienteAuth);
        assertEquals(com.pretriage.backend.model.chat.EstadoClasificacionChat.COMPLETADA,
                chatService.obtenerChat(chatId, pacienteAuth).estadoClasificacion());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM mensaje WHERE chat_id=? AND autor='PACIENTE'", Integer.class, Long.valueOf(chatId)));
        assertThrows(com.pretriage.backend.exceptions.ChatFinalizadoException.class,
                () -> chatService.reintentarClasificacion(chatId, pacienteAuth));
        verify(cola, times(1)).finalizarTriageEIngresarACola(eq(pacienteAuth), any(), anyString(), any());
    }

    @Test
    void preguntaInvalidaCorregidaPorGeminiGuardaUnaSolaRespuestaSinFinalizar() {
        var resultado = new TriageResultDTO("fiebre", List.of("fiebre"), "no informado", "no informado", null,
                List.of(), List.of(), List.of(), List.of(), "no informado", "no informado", 3, false, "Consultar si empeora");
        when(proveedor.consultarContinuacion(anyString(), anyString(), any()))
                .thenReturn(new TriageAiResponse(true, "Listo", resultado),
                        new TriageAiResponse(false, "¿Querés agregar algo más?", null));
        var respuesta = chatService.enviarMensaje(chatId, pacienteAuth, "Tengo fiebre");
        assertEquals("GEMINI", respuesta.origenRespuesta());
        var chat = chatService.obtenerChat(chatId, pacienteAuth);
        assertFalse(chat.finalizado());
        assertEquals(3, chat.mensajes().size());
        assertEquals("¿Querés agregar algo más?", chat.mensajes().getLast().contenido());
        assertNull(db.queryForObject("SELECT resultado_triage_json FROM chat WHERE id=?", String.class, Long.valueOf(chatId)));
        verify(proveedor, times(2)).consultarContinuacion(anyString(), anyString(), any());
        verify(proveedor, never()).consultar(anyString(), anyString(), anyBoolean());
    }

    @Test
    void rechazoDeAlcancePersisteExclusionYConservaEstadoAbierto() {
        when(proveedor.consultarContinuacion(anyString(), anyString(), any()))
                .thenReturn(new TriageAiResponse(false, "mensaje del modelo ignorado", null,
                        com.pretriage.backend.controllers.dtos.EstadoEntradaChat.FUERA_DE_CONTEXTO));

        var respuesta = chatService.enviarMensaje(chatId, pacienteAuth, "Dame una receta de torta");

        assertEquals("CONTROL_CONTEXTO", respuesta.origenRespuesta());
        var chat = chatService.obtenerChat(chatId, pacienteAuth);
        assertFalse(chat.finalizado());
        assertEquals(com.pretriage.backend.model.chat.EstadoClasificacionChat.NO_SOLICITADA,
                chat.estadoClasificacion());
        assertEquals(3, chat.mensajes().size(), "el texto y la respuesta fija siguen visibles");
        assertEquals(Boolean.TRUE, db.queryForObject(
                "SELECT excluido_del_triage FROM mensaje WHERE chat_id=? AND autor='PACIENTE'",
                Boolean.class, Long.valueOf(chatId)));
        verifyNoInteractions(cola);
    }

    private void assertSinResultadoNiMensajesNuevos() {
        var chat = chatService.obtenerChat(chatId, pacienteAuth);
        assertFalse(chat.finalizado());
        assertEquals(1, chat.mensajes().size());
        assertNull(db.queryForObject("SELECT resultado_triage_json FROM chat WHERE id=?", String.class, Long.valueOf(chatId)));
    }
}
