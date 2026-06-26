package com.pretriage.backend.controllers.dtos;

import java.util.List;

public record TriageResultDTO(
        String motivoConsulta,
        List<String> sintomas,
        String inicio,
        String evolucion,
        Integer intensidadDolor,
        List<String> signosAlarma,
        List<String> antecedentesRelevantes,
        List<String> medicamentos,
        List<String> alergias,
        String posibilidadEmbarazo,
        String observaciones,
        boolean requiereAtencionInmediata,
        String recomendacionSeguridad) {
}
