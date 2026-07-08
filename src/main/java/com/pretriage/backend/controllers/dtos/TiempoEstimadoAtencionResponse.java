package com.pretriage.backend.controllers.dtos;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class TiempoEstimadoAtencionResponse {

    private Long idConsulta;

    private LocalDateTime fechaHoraAtencionEstimadaDesde;
    private LocalDateTime fechaHoraAtencionEstimadaHasta;



}
