package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.NotificacionSalaDTO;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaAtencionNotifierTest {

    @Mock
    RepoConsultasMedicas repoConsultasMedicas;
    @Mock
    EsperaPacienteService esperaPacienteService;

    @InjectMocks
    SalaAtencionNotifier notifier;

    @BeforeEach
    void setUp() {
        // Ensure map is clean
        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        if (conexiones != null) conexiones.clear();
    }

    @Test
    void suscribirse_ok_registraConexionYRetornaEmitter() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);

        SseEmitter emitter = notifier.suscribirse("auth0", 5L);

        assertNotNull(emitter);
        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        assertNotNull(conexiones);
        assertEquals(1, conexiones.get(5L).size());
        assertTrue(conexiones.get(5L).contains(emitter));
        verify(repoConsultasMedicas).existsByIdAndPacienteUsuarioAuthId(5L, "auth0");
    }

    @Test
    void suscribirse_rechazaSiNoEsDueño() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> notifier.suscribirse("auth0", 5L));
    }

    @Test
    void suscribirse_permiteMultiplesEmittersParaMismaConsulta() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);

        SseEmitter e1 = notifier.suscribirse("auth0", 5L);
        SseEmitter e2 = notifier.suscribirse("auth0", 5L);

        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        assertEquals(2, conexiones.get(5L).size());
        assertTrue(conexiones.get(5L).contains(e1));
        assertTrue(conexiones.get(5L).contains(e2));
    }

    @Test
    void desuscribirse_ok_completaYRemueve() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);
        // first subscribe to have emitter
        SseEmitter emitter = notifier.suscribirse("auth0", 5L);
        // mock verification via desuscribirse
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);

        notifier.desuscribirse("auth0", 5L);

        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        assertTrue(conexiones == null || !conexiones.containsKey(5L) || conexiones.get(5L).isEmpty());
    }

    @Test
    void desuscribirse_sinEmitters_noFalla() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(9L, "auth0")).thenReturn(true);

        assertDoesNotThrow(() -> notifier.desuscribirse("auth0", 9L));
    }

    @Test
    void desuscribirse_rechazaSiNoEsDueño() {
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> notifier.desuscribirse("auth0", 5L));
    }

    @Test
    void notificarLlamado_conSuscriptores_invocaObtenerNotificacionSala() throws Exception {
        // arrange: suscribirse primero
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);
        SseEmitter emitter = notifier.suscribirse("auth0", 5L);

        NotificacionSalaDTO dto = new NotificacionSalaDTO();
        dto.setConsultaId(5L);
        dto.setCodigoSala("Consultorio 1");
        dto.setEstadoConsulta(EstadoConsulta.LLAMADO);
        dto.setEstadoEntradaCola(EstadoEntradaCola.LLAMADO);
        when(esperaPacienteService.obtenerNotificacionSalaDe(5L)).thenReturn(dto);

        // act: notificar
        notifier.notificarLlamadoAlPaciente(5L);

        verify(esperaPacienteService).obtenerNotificacionSalaDe(5L);
        // emitter sigue registrado (no desconectado por error)
        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        assertEquals(1, conexiones.get(5L).size());
    }

    @Test
    void notificarLlamado_sinSuscriptores_noFallaYNoLanza() {
        NotificacionSalaDTO dto = new NotificacionSalaDTO();
        dto.setConsultaId(99L);
        dto.setCodigoSala("Sala X");
        when(esperaPacienteService.obtenerNotificacionSalaDe(99L)).thenReturn(dto);

        assertDoesNotThrow(() -> notifier.notificarLlamadoAlPaciente(99L));
        verify(esperaPacienteService).obtenerNotificacionSalaDe(99L);
    }

    @Test
    void notificarLlamado_cuandoServicioLanzaExcepcion_noPropaga() {
        when(esperaPacienteService.obtenerNotificacionSalaDe(5L)).thenThrow(new RuntimeException("boom"));

        // Even with suscriptores, should be caught and logged, not propagated
        when(repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(5L, "auth0")).thenReturn(true);
        notifier.suscribirse("auth0", 5L);

        assertDoesNotThrow(() -> notifier.notificarLlamadoAlPaciente(5L));
    }

    @Test
    void notificarLlamado_conMockEmitterEnviaEventoLlamado() throws Exception {
        // Use reflection to inject mock emitter to verify send
        SseEmitter mockEmitter = mock(SseEmitter.class);
        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        conexiones.put(5L, new CopyOnWriteArrayList<>(List.of(mockEmitter)));

        NotificacionSalaDTO dto = new NotificacionSalaDTO();
        dto.setConsultaId(5L);
        dto.setCodigoSala("Sala 2");
        dto.setEstadoConsulta(EstadoConsulta.LLAMADO);
        dto.setEstadoEntradaCola(EstadoEntradaCola.LLAMADO);
        when(esperaPacienteService.obtenerNotificacionSalaDe(5L)).thenReturn(dto);

        notifier.notificarLlamadoAlPaciente(5L);

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeat_conMockEmitterEnviaHeartbeat() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        conexiones.put(7L, new CopyOnWriteArrayList<>(List.of(mockEmitter)));

        notifier.enviarHeartbeat();

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void enviarConIOExceptionDesconectaEmitter() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new java.io.IOException("broken")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        @SuppressWarnings("unchecked")
        Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones =
                (Map<Long, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(notifier, "conexiones");
        conexiones.put(8L, new CopyOnWriteArrayList<>(List.of(mockEmitter)));

        NotificacionSalaDTO dto = new NotificacionSalaDTO();
        dto.setConsultaId(8L);
        dto.setCodigoSala("Sala 3");
        when(esperaPacienteService.obtenerNotificacionSalaDe(8L)).thenReturn(dto);

        notifier.notificarLlamadoAlPaciente(8L);

        // emitter should have been removed after IOException
        assertTrue(!conexiones.containsKey(8L) || conexiones.get(8L).isEmpty());
    }
}
