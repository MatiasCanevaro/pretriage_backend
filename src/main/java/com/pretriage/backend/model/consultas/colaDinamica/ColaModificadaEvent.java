package com.pretriage.backend.model.consultas.colaDinamica;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;

import java.util.List;

public record ColaModificadaEvent(List<TiempoEstimadoAtencionResponse> tiemposEstimados) {
}
