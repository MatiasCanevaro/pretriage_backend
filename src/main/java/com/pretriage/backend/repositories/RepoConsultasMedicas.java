package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoConsultasMedicas extends JpaRepository<ConsultaMedica, Long> {

    Optional<ConsultaMedica> findByPacienteIdAndEstadoConsultaEquals(Long idPaciente, EstadoConsulta estadoConsulta);

    Optional<ConsultaMedica> findByPacienteId(Long idPaciente);

    @Query("SELECT c FROM ConsultaMedica c WHERE c.hospital.id = :idHospital AND c.medico.id = :idMedico")
    List<ConsultaMedica> findAllByHospitalIdAndMedicoId(Long idHospital, Long idMedico);
}
