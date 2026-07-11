package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.EstadoAtencionMedica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepoAtencionesMedicas extends JpaRepository<AtencionMedica, Long> {
    Optional<AtencionMedica> findByConsultaMedicaIdAndEstado(Long consultaId, EstadoAtencionMedica estado);
    boolean existsBySesionAtencionMedicaIdAndEstado(Long sesionId, EstadoAtencionMedica estado);
    List<AtencionMedica> findBySesionAtencionMedicaMedicoUsuarioAuthIdOrderByFechaHoraInicioDesc(String auth0Id);
}
