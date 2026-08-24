package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.consultas.TipoPausaCola;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoEntradasCola extends JpaRepository<EntradaCola, Long> {

    Optional<EntradaCola> findByConsultaMedicaId(Long idConsultaMedica);

    Optional<EntradaCola> findFirstByConsultaMedicaPacienteIdAndEstadoIn(Long idPaciente, Collection<EstadoEntradaCola> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EntradaCola> findFirstByGestorDeColaIdAndEstadoOrderByPrioridadDescOrdenRelativoAsc(Long idGestorDeCola, EstadoEntradaCola estado);

    Optional<EntradaCola> findFirstByGestorDeColaIdOrderByOrdenRelativoDesc(Long idGestorDeCola);

    Optional<EntradaCola> findFirstByGestorDeColaIdAndEstadoAndPrioridadOrderByOrdenRelativoAsc(Long idGestorDeCola, EstadoEntradaCola estado, int prioridad);

    List<EntradaCola> findByGestorDeColaIdAndEstadoOrderByPrioridadDescOrdenRelativoAscFechaHoraIngresoAsc(Long idGestorDeCola, EstadoEntradaCola estado);

    boolean existsByConsultaMedicaMedicoIdAndEstadoIn(Long medicoId, Collection<EstadoEntradaCola> estados);

    Optional<EntradaCola> findFirstByConsultaMedicaMedicoUsuarioAuthIdAndEstadoInOrderByFechaHoraLlamadoDesc(
            String auth0Id, Collection<EstadoEntradaCola> estados);

    List<EntradaCola> findByEstadoAndFechaHoraSalidaTemporalBefore(EstadoEntradaCola estado, LocalDateTime limite);

    List<EntradaCola> findByEstadoAndTipoPausaAndFechaHoraLimiteRespuestaBefore(EstadoEntradaCola estado, TipoPausaCola tipoPausa, LocalDateTime fechaHoraLimiteRespuesta);

    long countByGestorDeColaHospitalIdAndGestorDeColaEspecialidadIdAndEstado(
            Long hospitalId, Long especialidadId, EstadoEntradaCola estado);
}

