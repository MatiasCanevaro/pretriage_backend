package com.pretriage.backend.model.consultas;

import java.time.LocalDateTime;
import java.util.List;

import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultaMedica {
    private LocalDateTime fechaHoraCreacion;
    private Hospital hospital;
    private Medico medico;
    private Paciente paciente;
    private List<Sintoma> sintomasBot;
    private NivelDeGravedad nivelDeGravedadBot;
    private NivelDeGravedad nivelDeGravedadMedico;
    private List<Mensaje> chat;
}
