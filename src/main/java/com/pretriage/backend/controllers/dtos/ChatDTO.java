package com.pretriage.backend.controllers.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record ChatDTO(
        Long id,
        List<MensajeDTO> mensajes,
        LocalDateTime timestamp,
        boolean finalizado) {
}
