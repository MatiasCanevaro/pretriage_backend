package com.pretriage.backend.controllers.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TiempoEstimadoAtencionResponse {

    private Long consultaId;

    private LocalDateTime fechaHoraAtencionEstimada;

    private LocalDateTime fechaHoraAtencionEstimadaDesde;
    private LocalDateTime fechaHoraAtencionEstimadaHasta;


    private boolean hayMedicosActivos;

    private int medicosActivos;

    private int medicosParaEstimacion;

    private int posicionEnCola;

    private int pacientesAntes;

    private int minutosPromedioAtencion;

    private String mensaje;
}
