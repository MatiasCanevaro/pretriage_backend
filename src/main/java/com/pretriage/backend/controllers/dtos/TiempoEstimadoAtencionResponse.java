package com.pretriage.backend.controllers.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TiempoEstimadoAtencionResponse {

    private LocalDateTime fechaHoraAtencionEstimadaDesde;
    private LocalDateTime fechaHoraAtencionEstimadaHasta;



}
