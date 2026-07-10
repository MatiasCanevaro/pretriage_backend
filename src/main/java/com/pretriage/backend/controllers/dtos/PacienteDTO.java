package com.pretriage.backend.controllers.dtos;

import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.personas.TipoDocumento;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PacienteDTO {

    private Long idPaciente;
    private String nombre;
    private String apellido;
    private String numeroDocumento;
    private TipoDocumento tipoDocumento;
    private NivelDeGravedad nivelDeGravedadBot;
    private LocalDateTime fechaHoraIngresoAColaEspera;
    private EstadoConsulta estadoConsulta;

}
