package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.exceptions.HospitalNoEncontradoException;
import com.pretriage.backend.exceptions.MedicoNoEncontradoException;
import com.pretriage.backend.mappers.MapperPaciente;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoMedico;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MedicoService {

    private final ColaService colaService;

    private final RepoMedico repoMedico;
    private final RepoHospitales repoHospitales;
    private final RepoConsultasMedicas repoConsultasMedicas;

    @Transactional
    public List<PacienteDTO> obtenerTodosPacientesParaAtender(Long idHospital, String auth0IdMedico){
        this.obtenerMedico(auth0IdMedico);
        Hospital hospital = this.obtenerHospital(idHospital);
        GestorDeCola gestorDeCola = colaService.obtenerColaDe(hospital);

        List<Paciente> pacientes = gestorDeCola.obtenerPacientesParaAtender();

        return  pacientes.stream()
                .map( paciente -> {
                    ConsultaMedica consultaMedica = this.obtenerConsultaMedicaDe(paciente);

                    return MapperPaciente.toPacienteDTO(paciente, consultaMedica);
                })
                .toList();
    }

    @Transactional
    public List<PacienteDTO> findAllPacientesAtendidos(Long idHospital, String auth0IdMedico) {
        Medico medico = this.obtenerMedico(auth0IdMedico);
        Hospital hospital = this.obtenerHospital(idHospital);
        List<ConsultaMedica> consultasMedicas = this.obtenerConsultasMedicasEnHospitalYDeMedico(hospital, medico);

        return consultasMedicas.stream()
                .map( consultaMedica -> {
                    Paciente paciente = consultaMedica.getPaciente();

                    return MapperPaciente.toPacienteDTO(paciente, consultaMedica);
                })
                .toList();
    }

    @Transactional
    private List<ConsultaMedica> obtenerConsultasMedicasEnHospitalYDeMedico(Hospital hospital, Medico medico){
        return this.repoConsultasMedicas.findAllByHospitalIdAndMedicoId(hospital.getId(), medico.getId());
    }

    private ConsultaMedica obtenerConsultaMedicaDe(Paciente paciente) {
       return repoConsultasMedicas.findByPacienteId(paciente.getId())
               .orElseThrow(() -> new NoSuchElementException(
                       "No existe una consulta médica para el paciente con id " + paciente.getId()));
    }

    private Hospital obtenerHospital(Long idHospital) {
        return this.repoHospitales.findById(idHospital)
                .orElseThrow(() -> new HospitalNoEncontradoException(idHospital));
    }

    private Medico obtenerMedico(String auth0IdMedico){
        return this.repoMedico.findByUsuarioAuth0Id(auth0IdMedico)
                .orElseThrow(() -> new MedicoNoEncontradoException(auth0IdMedico));
    }

}
