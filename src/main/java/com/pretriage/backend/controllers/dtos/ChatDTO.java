package com.pretriage.backend.controllers.dtos;

import java.time.LocalDateTime;
import java.util.List;
import com.pretriage.backend.model.chat.EstadoClasificacionChat;

public record ChatDTO(
        Long id,
        List<MensajeDTO> mensajes,
        LocalDateTime timestamp,
        boolean finalizado,
        EstadoClasificacionChat estadoClasificacion) {
    public ChatDTO(Long id, List<MensajeDTO> mensajes, LocalDateTime timestamp, boolean finalizado) {
        this(id, mensajes, timestamp, finalizado, EstadoClasificacionChat.NO_SOLICITADA);
    }
}
