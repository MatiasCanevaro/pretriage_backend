package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.EstadoSesionMedica;
import com.pretriage.backend.model.consultas.SesionAtencionMedica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface RepoSesionesAtencionMedica extends JpaRepository<SesionAtencionMedica, Long> {

    boolean existsByMedicoIdAndEstadoIn(Long idMedico, Collection<EstadoSesionMedica> estados);

    boolean existsBySalaIdAndEstadoIn(Long idSala, Collection<EstadoSesionMedica> estados);

    Optional<SesionAtencionMedica> findByIdAndMedicoUsuarioAuthId(Long id, String auth0Id);

    Optional<SesionAtencionMedica> findFirstByMedicoUsuarioAuthIdAndEstadoInOrderByFechaHoraInicioDesc(
            String auth0Id, Collection<EstadoSesionMedica> estados);

    int countByHospitalIdAndEspecialidadIdAndEstado(Long idHospital, Long idEspecialidad, EstadoSesionMedica estado);
}

