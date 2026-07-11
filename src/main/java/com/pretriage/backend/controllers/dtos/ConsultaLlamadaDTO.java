package com.pretriage.backend.controllers.dtos;

import com.pretriage.backend.model.consultas.EstadoConsulta;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultaLlamadaDTO {

    private Long consultaId;
    private String codigoLlamado;

    private Long pacienteId;

    private Long salaId;

    private String nombreSala;

    private EstadoConsulta estadoConsulta;
}
