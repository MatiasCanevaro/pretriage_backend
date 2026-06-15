package com.pretriage.backend.controllers.dtos;

import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.personas.Paciente;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ChatDTO(List<MensajeDTO> mensajes, LocalDateTime timestamp) {
}
