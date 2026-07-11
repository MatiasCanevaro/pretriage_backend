package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.AtencionMedicaDTO;
import com.pretriage.backend.controllers.dtos.AsignacionMedicoDTO;
import com.pretriage.backend.controllers.dtos.ConsultaLlamadaDTO;
import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.controllers.dtos.SesionAtencionMedicaDTO;
import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoAtencionMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.consultas.EstadoSesionMedica;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.SesionAtencionMedica;
import com.pretriage.backend.model.consultas.TipoPausaCola;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.AsignacionMedicoHospital;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.repositories.RepoAsignacionesMedicoHospital;
import com.pretriage.backend.repositories.RepoAtencionesMedicas;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoMedico;
import com.pretriage.backend.repositories.RepoSalas;
import com.pretriage.backend.repositories.RepoSesionesAtencionMedica;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AtencionMedicoService {

    private static final List<EstadoSesionMedica> SESIONES_RESERVAN_RECURSOS = List.of(
            EstadoSesionMedica.ACTIVA,
            EstadoSesionMedica.PAUSADA);

    private final RepoMedico repoMedico;
    private final RepoHospitales repoHospitales;
    private final RepoEspecialidadesMedicas repoEspecialidadesMedicas;
    private final RepoSalas repoSalas;
    private final RepoAsignacionesMedicoHospital repoAsignacionesMedicoHospital;
    private final RepoSesionesAtencionMedica repoSesionesAtencionMedica;
    private final RepoGestoresDeColas repoGestoresDeColas;
    private final RepoEntradasCola repoEntradasCola;
    private final RepoConsultasMedicas repoConsultasMedicas;
    private final RepoAtencionesMedicas repoAtencionesMedicas;

    public List<AsignacionMedicoDTO> obtenerAsignaciones(String auth0Id) {
        Medico medico = obtenerMedico(auth0Id);
        return repoAsignacionesMedicoHospital.findByMedicoId(medico.getId()).stream()
                .map(this::mapearAsignacion)
                .toList();
    }

    public List<SalaDTO> obtenerSalas(Long hospitalId, String codigoEspecialidad) {
        return repoSalas.findByHospitalIdAndEspecialidadCodigoAndActivaTrue(hospitalId, codigoEspecialidad).stream()
                .map(this::mapearSala)
                .toList();
    }
    public List<ConsultaLlamadaDTO> listarPacientesDisponibles(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionActiva(auth0Id, sesionId);
        GestorDeCola gestor = obtenerGestorDeCola(sesion);
        return repoEntradasCola
                .findByGestorDeColaIdAndEstadoOrderByPrioridadDescOrdenRelativoAscFechaHoraIngresoAsc(
                        gestor.getId(), EstadoEntradaCola.EN_COLA)
                .stream()
                .map(EntradaCola::getConsultaMedica)
                .map(this::mapearConsultaLlamada)
                .toList();
    }

    public List<AtencionMedicaDTO> obtenerHistorial(String auth0Id) {
        obtenerMedico(auth0Id);
        return repoAtencionesMedicas
                .findBySesionAtencionMedicaMedicoUsuarioAuthIdOrderByFechaHoraInicioDesc(auth0Id)
                .stream()
                .map(this::mapearAtencion)
                .toList();
    }

    @Transactional
    public SesionAtencionMedicaDTO iniciarSesion(String auth0Id, Long hospitalId, String codigoEspecialidad, Long salaId) {
        Medico medico = obtenerMedico(auth0Id);
        Hospital hospital = repoHospitales.findById(hospitalId)
                .orElseThrow(() -> new NoSuchElementException("Hospital inexistente"));
        EspecialidadMedica especialidad = repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)
                .orElseThrow(() -> new NoSuchElementException("Especialidad medica inexistente"));
        Sala sala = repoSalas.findById(salaId)
                .orElseThrow(() -> new NoSuchElementException("Sala inexistente"));

        validarAsignacion(medico, hospital, especialidad);
        validarSala(sala, hospital, especialidad);
        validarRecursosDisponibles(medico, sala);

        SesionAtencionMedica sesion = new SesionAtencionMedica();
        sesion.setMedico(medico);
        sesion.setHospital(hospital);
        sesion.setEspecialidad(especialidad);
        sesion.setSala(sala);
        sesion.setEstado(EstadoSesionMedica.ACTIVA);
        sesion.setFechaHoraInicio(LocalDateTime.now());

        return mapearSesion(repoSesionesAtencionMedica.save(sesion));
    }

    @Transactional
    public SesionAtencionMedicaDTO pausarSesion(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionDelMedico(auth0Id, sesionId);
        validarSesionNoFinalizada(sesion);
        validarSinConsultaEnCurso(sesion);
        sesion.setEstado(EstadoSesionMedica.PAUSADA);
        sesion.setFechaHoraPausa(LocalDateTime.now());
        return mapearSesion(repoSesionesAtencionMedica.save(sesion));
    }

    @Transactional
    public SesionAtencionMedicaDTO reanudarSesion(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionDelMedico(auth0Id, sesionId);
        if (sesion.getEstado() != EstadoSesionMedica.PAUSADA) {
            throw new IllegalStateException("Solo se puede reanudar una sesion pausada");
        }
        sesion.setEstado(EstadoSesionMedica.ACTIVA);
        sesion.setFechaHoraPausa(null);
        return mapearSesion(repoSesionesAtencionMedica.save(sesion));
    }

    @Transactional
    public SesionAtencionMedicaDTO cerrarSesion(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionDelMedico(auth0Id, sesionId);
        validarSesionNoFinalizada(sesion);
        validarSinConsultaEnCurso(sesion);
        sesion.setEstado(EstadoSesionMedica.FINALIZADA);
        sesion.setFechaHoraFin(LocalDateTime.now());
        return mapearSesion(repoSesionesAtencionMedica.save(sesion));
    }

    @Transactional
    public ConsultaLlamadaDTO llamarProximo(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionActiva(auth0Id, sesionId);
        validarSinConsultaEnCurso(sesion);
        GestorDeCola gestorDeCola = repoGestoresDeColas
                .findByHospitalIdAndEspecialidadId(sesion.getHospital().getId(), sesion.getEspecialidad().getId())
                .orElseThrow(() -> new NoSuchElementException("No existe cola para la especialidad del hospital"));

        EntradaCola entrada = repoEntradasCola
                .findFirstByGestorDeColaIdAndEstadoOrderByPrioridadDescOrdenRelativoAsc(gestorDeCola.getId(), EstadoEntradaCola.EN_COLA)
                .orElseThrow(() -> new NoSuchElementException("No hay pacientes en cola"));

        ConsultaMedica consulta = entrada.getConsultaMedica();
        entrada.setEstado(EstadoEntradaCola.LLAMADO);
        entrada.setFechaHoraLlamado(LocalDateTime.now());
        consulta.setEstadoConsulta(EstadoConsulta.LLAMADO);
        consulta.setMedico(sesion.getMedico());
        consulta.setSala(sesion.getSala());

        repoConsultasMedicas.save(consulta);
        repoEntradasCola.save(entrada);
        return mapearConsultaLlamada(consulta);
    }

    @Transactional
    public ConsultaLlamadaDTO confirmarPresente(String auth0Id, Long sesionId, Long consultaId) {
        SesionAtencionMedica sesion = obtenerSesionActiva(auth0Id, sesionId);
        EntradaCola entrada = obtenerEntradaDeConsulta(consultaId);
        validarConsultaTomadaPorSesion(entrada.getConsultaMedica(), sesion);
        validarEstadoEntrada(entrada, EstadoEntradaCola.LLAMADO);

        ConsultaMedica consulta = entrada.getConsultaMedica();
        entrada.setEstado(EstadoEntradaCola.EN_ATENCION);
        consulta.setEstadoConsulta(EstadoConsulta.EN_ATENCION);

        AtencionMedica atencion = new AtencionMedica();
        atencion.setConsultaMedica(consulta);
        atencion.setSesionAtencionMedica(sesion);
        atencion.setEstado(EstadoAtencionMedica.EN_CURSO);
        atencion.setFechaHoraInicio(LocalDateTime.now());
        repoAtencionesMedicas.save(atencion);
        repoEntradasCola.save(entrada);
        repoConsultasMedicas.save(consulta);
        return mapearConsultaLlamada(consulta);
    }

    @Transactional
    public ConsultaLlamadaDTO marcarAusente(String auth0Id, Long sesionId, Long consultaId) {
        SesionAtencionMedica sesion = obtenerSesionActiva(auth0Id, sesionId);
        EntradaCola entrada = obtenerEntradaDeConsulta(consultaId);
        validarConsultaTomadaPorSesion(entrada.getConsultaMedica(), sesion);
        validarEstadoEntrada(entrada, EstadoEntradaCola.LLAMADO);

        ConsultaMedica consulta = entrada.getConsultaMedica();
        entrada.setEstado(EstadoEntradaCola.EN_ESPERA);
        entrada.setTipoPausa(TipoPausaCola.AUSENTE_AL_LLAMADO);
        entrada.setFechaHoraSalidaTemporal(LocalDateTime.now());
        consulta.setEstadoConsulta(EstadoConsulta.EN_ESPERA);
        repoEntradasCola.save(entrada);
        repoConsultasMedicas.save(consulta);
        return mapearConsultaLlamada(consulta);
    }

    @Transactional
    public ConsultaLlamadaDTO finalizarConsulta(String auth0Id, Long sesionId, Long consultaId) {
        SesionAtencionMedica sesion = obtenerSesionActiva(auth0Id, sesionId);
        EntradaCola entrada = obtenerEntradaDeConsulta(consultaId);
        validarConsultaTomadaPorSesion(entrada.getConsultaMedica(), sesion);
        validarEstadoEntrada(entrada, EstadoEntradaCola.EN_ATENCION);

        ConsultaMedica consulta = entrada.getConsultaMedica();
        entrada.setEstado(EstadoEntradaCola.FINALIZADA);
        consulta.setEstadoConsulta(EstadoConsulta.FINALIZADA);
        AtencionMedica atencion = repoAtencionesMedicas
                .findByConsultaMedicaIdAndEstado(consultaId, EstadoAtencionMedica.EN_CURSO)
                .orElseThrow(() -> new IllegalStateException("No existe una atencion en curso para la consulta"));
        atencion.setEstado(EstadoAtencionMedica.FINALIZADA);
        atencion.setFechaHoraFin(LocalDateTime.now());
        repoAtencionesMedicas.save(atencion);
        repoEntradasCola.save(entrada);
        repoConsultasMedicas.save(consulta);
        return mapearConsultaLlamada(consulta);
    }


    private GestorDeCola obtenerGestorDeCola(SesionAtencionMedica sesion) {
        return repoGestoresDeColas
                .findByHospitalIdAndEspecialidadId(sesion.getHospital().getId(), sesion.getEspecialidad().getId())
                .orElseThrow(() -> new NoSuchElementException("No existe cola para la especialidad del hospital"));
    }

    private void validarSinConsultaEnCurso(SesionAtencionMedica sesion) {
        boolean consultaTomada = repoEntradasCola.existsByConsultaMedicaMedicoIdAndEstadoIn(
                sesion.getMedico().getId(),
                List.of(EstadoEntradaCola.LLAMADO, EstadoEntradaCola.EN_ATENCION));
        if (consultaTomada || repoAtencionesMedicas.existsBySesionAtencionMedicaIdAndEstado(
                sesion.getId(), EstadoAtencionMedica.EN_CURSO)) {
            throw new IllegalStateException("Debe finalizar o resolver la consulta actual antes de continuar");
        }
    }
    private Medico obtenerMedico(String auth0Id) {
        return repoMedico.findByUsuarioAuthId(auth0Id)
                .orElseThrow(() -> new AccessDeniedException("No tiene permisos de medico"));
    }

    private SesionAtencionMedica obtenerSesionDelMedico(String auth0Id, Long sesionId) {
        return repoSesionesAtencionMedica.findByIdAndMedicoUsuarioAuthId(sesionId, auth0Id)
                .orElseThrow(() -> new AccessDeniedException("No tiene permisos sobre la sesion"));
    }

    private SesionAtencionMedica obtenerSesionActiva(String auth0Id, Long sesionId) {
        SesionAtencionMedica sesion = obtenerSesionDelMedico(auth0Id, sesionId);
        if (sesion.getEstado() != EstadoSesionMedica.ACTIVA) {
            throw new IllegalStateException("La sesion medica debe estar activa");
        }
        return sesion;
    }

    private EntradaCola obtenerEntradaDeConsulta(Long consultaId) {
        return repoEntradasCola.findByConsultaMedicaId(consultaId)
                .orElseThrow(() -> new NoSuchElementException("La consulta no esta en cola"));
    }

    private void validarAsignacion(Medico medico, Hospital hospital, EspecialidadMedica especialidad) {
        boolean asignado = repoAsignacionesMedicoHospital.existsByMedicoIdAndHospitalIdAndEspecialidadCodigo(
                medico.getId(), hospital.getId(), especialidad.getCodigo());
        if (!asignado) {
            throw new AccessDeniedException("El medico no esta asignado al hospital y especialidad indicados");
        }
    }

    private void validarSala(Sala sala, Hospital hospital, EspecialidadMedica especialidad) {
        if (!sala.isActiva()
                || !sala.getHospital().getId().equals(hospital.getId())
                || !sala.getEspecialidad().getCodigo().equals(especialidad.getCodigo())) {
            throw new NoSuchElementException("La sala no corresponde al hospital y especialidad indicados");
        }
    }

    private void validarRecursosDisponibles(Medico medico, Sala sala) {
        if (repoSesionesAtencionMedica.existsByMedicoIdAndEstadoIn(medico.getId(), SESIONES_RESERVAN_RECURSOS)) {
            throw new IllegalStateException("El medico ya tiene una sesion activa o pausada");
        }
        if (repoSesionesAtencionMedica.existsBySalaIdAndEstadoIn(sala.getId(), SESIONES_RESERVAN_RECURSOS)) {
            throw new IllegalStateException("La sala ya esta siendo usada por otro medico");
        }
    }

    private void validarSesionNoFinalizada(SesionAtencionMedica sesion) {
        if (sesion.getEstado() == EstadoSesionMedica.FINALIZADA) {
            throw new IllegalStateException("La sesion ya fue finalizada");
        }
    }

    private void validarConsultaTomadaPorSesion(ConsultaMedica consulta, SesionAtencionMedica sesion) {
        if (consulta.getMedico() == null || !consulta.getMedico().getId().equals(sesion.getMedico().getId())) {
            throw new AccessDeniedException("La consulta no fue llamada por el medico de la sesion");
        }
        if (consulta.getSala() == null || !consulta.getSala().getId().equals(sesion.getSala().getId())) {
            throw new AccessDeniedException("La consulta no corresponde a la sala de la sesion");
        }
    }

    private void validarEstadoEntrada(EntradaCola entrada, EstadoEntradaCola estadoEsperado) {
        if (entrada.getEstado() != estadoEsperado) {
            throw new IllegalStateException("Estado de cola invalido para la accion solicitada");
        }
    }

    private AsignacionMedicoDTO mapearAsignacion(AsignacionMedicoHospital asignacion) {
        AsignacionMedicoDTO dto = new AsignacionMedicoDTO();
        dto.setHospitalId(asignacion.getHospital().getId());
        dto.setNombreHospital(asignacion.getHospital().getNombre());
        dto.setCodigoEspecialidad(asignacion.getEspecialidad().getCodigo());
        dto.setNombreEspecialidad(asignacion.getEspecialidad().getNombre());
        return dto;
    }

    private SesionAtencionMedicaDTO mapearSesion(SesionAtencionMedica sesion) {
        SesionAtencionMedicaDTO dto = new SesionAtencionMedicaDTO();
        dto.setId(sesion.getId());
        dto.setHospitalId(sesion.getHospital().getId());
        dto.setCodigoEspecialidad(sesion.getEspecialidad().getCodigo());
        dto.setSalaId(sesion.getSala().getId());
        dto.setEstado(sesion.getEstado());
        return dto;
    }

    private SalaDTO mapearSala(Sala sala) {
        SalaDTO dto = new SalaDTO();
        dto.setId(sala.getId());
        dto.setNombre(sala.getNombre());
        return dto;
    }


    private AtencionMedicaDTO mapearAtencion(AtencionMedica atencion) {
        AtencionMedicaDTO dto = new AtencionMedicaDTO();
        dto.setId(atencion.getId());
        dto.setConsultaId(atencion.getConsultaMedica().getId());
        dto.setSesionId(atencion.getSesionAtencionMedica().getId());
        dto.setPacienteId(atencion.getConsultaMedica().getPaciente().getId());
        dto.setHospitalId(atencion.getSesionAtencionMedica().getHospital().getId());
        dto.setCodigoEspecialidad(atencion.getSesionAtencionMedica().getEspecialidad().getCodigo());
        dto.setSalaId(atencion.getSesionAtencionMedica().getSala().getId());
        dto.setEstado(atencion.getEstado());
        dto.setFechaHoraInicio(atencion.getFechaHoraInicio());
        dto.setFechaHoraFin(atencion.getFechaHoraFin());
        return dto;
    }
    private ConsultaLlamadaDTO mapearConsultaLlamada(ConsultaMedica consulta) {
        ConsultaLlamadaDTO dto = new ConsultaLlamadaDTO();
        dto.setConsultaId(consulta.getId());
        dto.setCodigoLlamado(consulta.getCodigoLlamado());
        dto.setPacienteId(consulta.getPaciente().getId());
        dto.setSalaId(consulta.getSala().getId());
        dto.setNombreSala(consulta.getSala().getNombre());
        dto.setEstadoConsulta(consulta.getEstadoConsulta());
        return dto;
    }
}


