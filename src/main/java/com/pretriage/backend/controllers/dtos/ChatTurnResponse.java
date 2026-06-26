package com.pretriage.backend.controllers.dtos;

public record ChatTurnResponse(
        ChatDTO chat,
        MensajeDTO respuesta,
        TriageResultDTO resultado) {
}
