package com.pretriage.backend.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.pretriage.backend.controllers.dtos.NotificacionSalaDTO;
import com.pretriage.backend.repositories.RepoConsultasMedicas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SalaAtencionNotifier {

    private final RepoConsultasMedicas repoConsultasMedicas;
    private final EsperaPacienteService esperaPacienteService;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> conexiones = new ConcurrentHashMap<>();

    public SseEmitter suscribirse(String userId, Long consultaId) {
        if (!repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(consultaId, userId)) {
            throw new AccessDeniedException("No tiene permisos sobre la consulta");
        }

        SseEmitter emitter = new SseEmitter(0L);
        conexiones.computeIfAbsent(consultaId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> desconectar(consultaId, emitter));
        emitter.onTimeout(() -> desconectar(consultaId, emitter));
        emitter.onError(error -> desconectar(consultaId, emitter));

        enviar(consultaId, emitter, "suscrito", Map.of("consultaId", consultaId));

        return emitter;
    }

    public void desuscribirse(String userId, Long consultaId) {
        if (!repoConsultasMedicas.existsByIdAndPacienteUsuarioAuthId(consultaId, userId)) {
            throw new AccessDeniedException("No tiene permisos sobre la consulta");
        }

        List<SseEmitter> emitters = conexiones.get(consultaId);
        if (emitters == null)
            return;

        emitters.forEach(emitter -> {
            emitter.complete();
            desconectar(consultaId, emitter);
        });
    }

    public void notificarLlamadoAlPaciente(Long consultaId) {
        try {
            NotificacionSalaDTO atencionConSala = esperaPacienteService.obtenerNotificacionSalaDe(consultaId);
            List<SseEmitter> emitters = conexiones.get(consultaId);
            if (emitters == null)
                return;

            emitters.forEach(emitter -> enviar(consultaId, emitter, "llamado", atencionConSala));
        } catch (RuntimeException e) {
            log.error("Error al notificar llamado al paciente para la consulta {}: {}", consultaId, e.getMessage());
        }
    }

    private void desconectar(Long consultaId, SseEmitter emitter) {
        List<SseEmitter> emitters = conexiones.get(consultaId);
        if (emitters == null)
            return;
        emitters.remove(emitter);
        if (emitters.isEmpty())
            conexiones.remove(consultaId);
    }

    @Scheduled(fixedRate = 30000)
    public void enviarHeartbeat() {
        conexiones.forEach((consultaId, emitters) -> emitters
                .forEach(emitter -> enviar(consultaId, emitter, "heartbeat", Map.of("consultaId", consultaId))));
    }

    private void enviar(Long consultaId, SseEmitter emitter, String evento, Object data) {
        try {
            emitter.send(SseEmitter.event().name(evento).data(data));
        } catch (IOException | IllegalStateException error) {
            desconectar(consultaId, emitter);
        }
    }
}
