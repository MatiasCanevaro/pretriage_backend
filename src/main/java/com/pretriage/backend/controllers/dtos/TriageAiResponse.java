package com.pretriage.backend.controllers.dtos;

public record TriageAiResponse(
        boolean finalizado,
        String mensaje,
        TriageResultDTO resultado) {
}
