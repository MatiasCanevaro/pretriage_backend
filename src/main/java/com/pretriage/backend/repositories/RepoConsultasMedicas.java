package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface RepoConsultasMedicas extends JpaRepository<ConsultaMedica, Long> {

    boolean existsByIdAndPacienteUsuarioAuthId(Long consultaId, String auth0Id);

    Optional<ConsultaMedica> findByPacienteIdAndEstadoConsultaEquals(Long idPaciente, EstadoConsulta estadoConsulta);

    Optional<ConsultaMedica> findFirstByPacienteIdAndEstadoConsultaIn(Long idPaciente, Collection<EstadoConsulta> estadosConsulta);
}
