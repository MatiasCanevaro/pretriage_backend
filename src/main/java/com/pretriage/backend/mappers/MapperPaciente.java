package com.pretriage.backend.mappers;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.UsuarioAuth;
import jakarta.transaction.Transactional;

public class MapperPaciente {

    @Transactional
    public static PacienteDTO toPacienteDTO(Paciente paciente, ConsultaMedica consultaMedica) {
        PacienteDTO dto = new PacienteDTO();
        UsuarioAuth usuarioPaciente = paciente.getUsuarioAuth();

        dto.setIdPaciente(paciente.getId());
        dto.setNombre(usuarioPaciente.getNombre());
        dto.setApellido(usuarioPaciente.getApellido());
        dto.setNumeroDocumento(usuarioPaciente.getNumeroDocumento());
        dto.setTipoDocumento(usuarioPaciente.getTipoDocumento());

        dto.setNivelDeGravedadBot(consultaMedica.getNivelDeGravedadBot());
        dto.setFechaHoraIngresoAColaEspera(consultaMedica.getFechaHoraCreacion()); //se crea cuando se ingresa a la cola
        dto.setEstadoConsulta(consultaMedica.getEstadoConsulta());
        return dto;
    }
}
