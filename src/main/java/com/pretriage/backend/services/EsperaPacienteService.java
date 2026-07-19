package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EstadoConsultaPacienteDTO;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.consultas.TipoPausaCola;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class EsperaPacienteService {

    private static final int MINUTOS_REPREGUNTA_ATRASADO = 30;
    private static final int MINUTOS_MAXIMOS_EN_ESPERA = 60;

    private final PacienteService pacienteService;
    private final RepoEntradasCola repoEntradasCola;
    private final RepoConsultasMedicas repoConsultasMedicas;
    private final EstimacionAtencionService estimacionAtencionService;

    @Transactional
    public EstadoConsultaPacienteDTO ausentarme(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        EntradaCola entrada = obtenerEntradaActivaPaciente(paciente);
        validarEstado(entrada, EstadoEntradaCola.EN_COLA);

        entrada.setEstado(EstadoEntradaCola.EN_ESPERA);
        entrada.setTipoPausa(TipoPausaCola.ESPERA_MANUAL);
        entrada.setFechaHoraSalidaTemporal(LocalDateTime.now());
        entrada.getConsultaMedica().setEstadoConsulta(EstadoConsulta.EN_ESPERA);

        repoConsultasMedicas.save(entrada.getConsultaMedica());
        return mapear(repoEntradasCola.save(entrada));
    }

    @Transactional
    public EstadoConsultaPacienteDTO estoyAtrasado(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        EntradaCola entrada = obtenerEntradaActivaPaciente(paciente);
        validarEstado(entrada, EstadoEntradaCola.EN_ESPERA);
        if (entrada.getTipoPausa() != TipoPausaCola.AUSENTE_AL_LLAMADO) {
            throw new IllegalStateException("Solo puede confirmar atraso si fue marcado ausente por el medico");
        }

        entrada.setEstado(EstadoEntradaCola.ATRASADO);
        entrada.setTipoPausa(TipoPausaCola.ATRASADO_CONFIRMADO);
        entrada.setFechaHoraUltimaRepregunta(LocalDateTime.now());
        entrada.setFechaHoraLimiteRespuesta(LocalDateTime.now().plusMinutes(MINUTOS_REPREGUNTA_ATRASADO));
        entrada.getConsultaMedica().setEstadoConsulta(EstadoConsulta.ATRASADO);

        repoConsultasMedicas.save(entrada.getConsultaMedica());
        return mapear(repoEntradasCola.save(entrada));
    }

    @Transactional
    public EstadoConsultaPacienteDTO sigoAsistiendo(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        EntradaCola entrada = obtenerEntradaActivaPaciente(paciente);
        validarEstado(entrada, EstadoEntradaCola.ATRASADO);

        entrada.setFechaHoraUltimaRepregunta(LocalDateTime.now());
        entrada.setFechaHoraLimiteRespuesta(LocalDateTime.now().plusMinutes(MINUTOS_REPREGUNTA_ATRASADO));
        return mapear(repoEntradasCola.save(entrada));
    }

    @Transactional
    public EstadoConsultaPacienteDTO llegue(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        EntradaCola entrada = obtenerEntradaActivaPaciente(paciente);

        if (entrada.getEstado() == EstadoEntradaCola.ATRASADO) {
            entrada.setOrdenRelativo(obtenerOrdenParaPrimerLugarDePrioridad(entrada));
        } else if (entrada.getEstado() != EstadoEntradaCola.EN_ESPERA || entrada.getTipoPausa() != TipoPausaCola.ESPERA_MANUAL) {
            throw new IllegalStateException("El paciente no puede volver a la cola desde el estado actual");
        }

        entrada.setEstado(EstadoEntradaCola.EN_COLA);
        entrada.setTipoPausa(null);
        entrada.setFechaHoraIngreso(LocalDateTime.now());
        entrada.setFechaHoraLimiteRespuesta(null);
        entrada.setFechaHoraUltimaRepregunta(null);
        entrada.getConsultaMedica().setEstadoConsulta(EstadoConsulta.EN_COLA);
        entrada.getConsultaMedica().setMedico(null);
        entrada.getConsultaMedica().setSala(null);

        repoConsultasMedicas.save(entrada.getConsultaMedica());
        return mapear(repoEntradasCola.save(entrada));
    }

    @Transactional
    public EstadoConsultaPacienteDTO obtenerEstado(String auth0Id) {
        Paciente paciente = obtenerPaciente(auth0Id);
        return mapear(obtenerEntradaActivaPaciente(paciente));
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelarAtrasadosSinRespuesta() {
        List<EntradaCola> vencidas = repoEntradasCola.findByEstadoAndTipoPausaAndFechaHoraLimiteRespuestaBefore(
                EstadoEntradaCola.ATRASADO,
                TipoPausaCola.ATRASADO_CONFIRMADO,
                LocalDateTime.now());

        cancelarEntradas(vencidas);
    }


    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cancelarEsperasVencidas() {
        List<EntradaCola> vencidas = repoEntradasCola.findByEstadoAndFechaHoraSalidaTemporalBefore(
                EstadoEntradaCola.EN_ESPERA,
                LocalDateTime.now().minusMinutes(MINUTOS_MAXIMOS_EN_ESPERA));

        cancelarEntradas(vencidas);
    }

    private void cancelarEntradas(List<EntradaCola> entradas) {
        entradas.forEach(entrada -> {
            entrada.setEstado(EstadoEntradaCola.CANCELADA);
            ConsultaMedica consulta = entrada.getConsultaMedica();
            consulta.setEstadoConsulta(EstadoConsulta.CANCELADA);
            repoConsultasMedicas.save(consulta);
            repoEntradasCola.save(entrada);
        });
    }
    private Paciente obtenerPaciente(String auth0Id) {
        return pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)
                .orElseThrow(() -> new AccessDeniedException("No tiene permisos de paciente"));
    }

    private EntradaCola obtenerEntradaActivaPaciente(Paciente paciente) {
        return repoEntradasCola.findFirstByConsultaMedicaPacienteIdAndEstadoIn(
                        paciente.getId(),
                        List.of(EstadoEntradaCola.EN_COLA,
                                EstadoEntradaCola.LLAMADO,
                                EstadoEntradaCola.EN_ESPERA,
                                EstadoEntradaCola.ATRASADO,
                                EstadoEntradaCola.EN_ATENCION))
                .orElseThrow(() -> new NoSuchElementException("El paciente no tiene una consulta activa en cola"));
    }

    private void validarEstado(EntradaCola entrada, EstadoEntradaCola estadoEsperado) {
        if (entrada.getEstado() != estadoEsperado) {
            throw new IllegalStateException("Estado de cola invalido para la accion solicitada");
        }
    }

    private long obtenerOrdenParaPrimerLugarDePrioridad(EntradaCola entrada) {
        return repoEntradasCola
                .findFirstByGestorDeColaIdAndEstadoAndPrioridadOrderByOrdenRelativoAsc(
                        entrada.getGestorDeCola().getId(),
                        EstadoEntradaCola.EN_COLA,
                        entrada.getPrioridad())
                .map(primera -> primera.getOrdenRelativo() - 1)
                .orElse(entrada.getOrdenRelativo());
    }

    private EstadoConsultaPacienteDTO mapear(EntradaCola entrada) {
        EstadoConsultaPacienteDTO dto = new EstadoConsultaPacienteDTO();
        dto.setConsultaId(entrada.getConsultaMedica().getId());
        dto.setEstadoConsulta(entrada.getConsultaMedica().getEstadoConsulta());
        dto.setEstadoEntradaCola(entrada.getEstado());
        dto.setTipoPausa(entrada.getTipoPausa());
        dto.setFechaHoraLimiteRespuesta(entrada.getFechaHoraLimiteRespuesta());
        if (entrada.getEstado() == EstadoEntradaCola.EN_COLA) {
            dto.setTiempoEstimadoAtencion(estimacionAtencionService.calcularPara(entrada.getConsultaMedica()));
        }
        return dto;
    }
}

