package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.exceptions.HospitalNoEncontradoException;
import com.pretriage.backend.exceptions.MedicoNoEncontradoException;
import com.pretriage.backend.mappers.MapperPaciente;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoMedico;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
                    ConsultaMedica consultaMedica = this.obtenerConsultaMedicaDe(paciente.getId());

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

    public void seleccionarAPaciente(Long idPaciente, Long idHospital, String auth0IdMedico){
        Medico medico = this.obtenerMedico(auth0IdMedico);
        Hospital hospital = this.obtenerHospital(idHospital);
        ConsultaMedica consultaMedica = this.obtenerConsultaMedicaDe(idPaciente);

        EstadoConsulta estadoConsultaPrevio = consultaMedica.getEstadoConsulta();
        if(!estadoConsultaPrevio.equals(EstadoConsulta.EN_ESPERA) &&
                !estadoConsultaPrevio.equals(EstadoConsulta.HOSPITAL_SELECCIONADO)
        ){
            throw new AccessDeniedException("No se puede seleccionar un paciente que no esté en espera o que no haya seleccionado el hospital");
        }

        consultaMedica.setMedico(medico);
        consultaMedica.setHospital(hospital);// no es necesario pero ya que estamos
        consultaMedica.setEstadoConsulta(EstadoConsulta.FINALIZADA);//para evitar que el paciente pueda ser seleccionado por otro médico
        this.repoConsultasMedicas.save(consultaMedica);

        if(!estadoConsultaPrevio.equals(EstadoConsulta.EN_ESPERA)){
            this.colaService.sacarDeLaColaDelHospital(consultaMedica);// lo saco de la cola
        }// si no estaba en espera, no se hace nada porque no esta en la cola
    }

    @Transactional
    private List<ConsultaMedica> obtenerConsultasMedicasEnHospitalYDeMedico(Hospital hospital, Medico medico){
        return this.repoConsultasMedicas.findAllByHospitalIdAndMedicoId(hospital.getId(), medico.getId());
    }

    private ConsultaMedica obtenerConsultaMedicaDe(Long idPaciente) {
       return repoConsultasMedicas.findByPacienteId(idPaciente)
               .orElseThrow(() -> new NoSuchElementException(
                       "No existe una consulta médica para el paciente con id " + idPaciente));
    }

    private Hospital obtenerHospital(Long idHospital) {
        return this.repoHospitales.findById(idHospital)
                .orElseThrow(() -> new HospitalNoEncontradoException(idHospital));
    }

    private Medico obtenerMedico(String auth0IdMedico){
        return this.repoMedico.findByUsuarioAuthId(auth0IdMedico)
                .orElseThrow(() -> new MedicoNoEncontradoException(auth0IdMedico));
    }

}
