package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.*;
import com.pretriage.backend.exceptions.ConflictoDeEstadoException;
import com.pretriage.backend.exceptions.RecursoNoEncontradoException;
import com.pretriage.backend.model.acceso.AuditoriaHospital;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoSesionMedica;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.hospitales.Sector;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoAuditoriasHospital;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoSalas;
import com.pretriage.backend.repositories.RepoSectores;
import com.pretriage.backend.repositories.RepoSesionesAtencionMedica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HospitalConfigurationService {
    private final StaffAccessService staffAccessService;
    private final RepoHospitales hospitales;
    private final RepoEspecialidadesMedicas especialidades;
    private final RepoSalas salas;
    private final RepoSectores sectores;
    private final RepoConsultasMedicas consultasMedicas;
    private final RepoSesionesAtencionMedica sesionesAtencionMedica;
    private final RepoAuditoriasHospital auditorias;

    @Transactional(readOnly = true)
    public ConfiguracionHospitalResponse obtener(String subject, Long hospitalId) {
        staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        Set<Long> habilitadas = new HashSet<>(hospital.getEspecialidades().stream()
                .map(EspecialidadMedica::getId).toList());
        List<EspecialidadHospitalResponse> catalogo = especialidades.findAll().stream()
                .sorted(Comparator.comparing(EspecialidadMedica::getNombre))
                .map(item -> new EspecialidadHospitalResponse(item.getId(), item.getCodigo(), item.getNombre(),
                        habilitadas.contains(item.getId())))
                .toList();
        List<SalaHospitalResponse> salasHospital = salas.findByHospitalIdOrderByNombreAsc(hospitalId).stream()
                .map(this::aSalaResponse)
                .toList();
        List<SectorHospitalResponse> sectoresHospital = sectores.findByHospitalIdOrderByNombreAsc(hospitalId).stream()
                .map(this::aSectorResponse)
                .toList();
        return new ConfiguracionHospitalResponse(catalogo, salasHospital, sectoresHospital);
    }

    @Transactional
    public ConfiguracionHospitalResponse habilitarEspecialidad(String subject, Long hospitalId, Long especialidadId) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidad(especialidadId);
        boolean yaHabilitada = hospital.getEspecialidades().stream()
                .anyMatch(item -> item.getId().equals(especialidadId));
        if (!yaHabilitada) {
            hospital.getEspecialidades().add(especialidad);
            hospitales.save(hospital);
            auditar(hospital, actor, "ESPECIALIDAD_HABILITADA", "especialidad:" + especialidadId,
                    especialidad.getNombre());
        }
        return obtener(subject, hospitalId);
    }

    @Transactional
    public ConfiguracionHospitalResponse deshabilitarEspecialidad(String subject, Long hospitalId,
            Long especialidadId) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidad(especialidadId);
        if (salas.existsByHospitalIdAndEspecialidadIdAndActivaTrue(hospitalId, especialidadId)) {
            throw new ConflictoDeEstadoException(
                    "Desactivá las salas de la especialidad antes de quitarla del hospital");
        }
        boolean removida = hospital.getEspecialidades().removeIf(item -> item.getId().equals(especialidadId));
        if (removida) {
            hospitales.save(hospital);
            auditar(hospital, actor, "ESPECIALIDAD_DESHABILITADA", "especialidad:" + especialidadId,
                    especialidad.getNombre());
        }
        return obtener(subject, hospitalId);
    }

    @Transactional
    public SalaHospitalResponse crearSala(String subject, Long hospitalId, GuardarSalaRequest request) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidadHabilitada(hospital, request.especialidadId());
        String nombre = request.nombre().trim();
        if (salas.existsByHospitalIdAndNombreIgnoreCase(hospitalId, nombre)) {
            throw new ConflictoDeEstadoException("Ya existe una sala con ese nombre en el hospital");
        }
        Sala sala = new Sala();
        sala.setNombre(nombre);
        sala.setHospital(hospital);
        sala.setEspecialidad(especialidad);
        sala.setActiva(true);
        sala = salas.save(sala);
        auditar(hospital, actor, "SALA_CREADA", "sala:" + sala.getId(), nombre);
        return aSalaResponse(sala);
    }

    @Transactional
    public SalaHospitalResponse actualizarSala(String subject, Long hospitalId, Long salaId,
            GuardarSalaRequest request) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        Sala sala = sala(hospitalId, salaId);
        EspecialidadMedica especialidad = especialidadHabilitada(hospital, request.especialidadId());
        String nombre = request.nombre().trim();
        if (salas.existsByHospitalIdAndNombreIgnoreCaseAndIdNot(hospitalId, nombre, salaId)) {
            throw new ConflictoDeEstadoException("Ya existe una sala con ese nombre en el hospital");
        }
        sala.setNombre(nombre);
        sala.setEspecialidad(especialidad);
        sala = salas.save(sala);
        auditar(hospital, actor, "SALA_ACTUALIZADA", "sala:" + salaId, nombre + " · " + especialidad.getNombre());
        return aSalaResponse(sala);
    }

    @Transactional
    public SalaHospitalResponse actualizarEstadoSala(String subject, Long hospitalId, Long salaId,
            ActualizarEstadoSalaRequest request) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        Sala sala = sala(hospitalId, salaId);
        if (request.activa())
            especialidadHabilitada(hospital, sala.getEspecialidad().getId());
        sala.setActiva(request.activa());
        sala = salas.save(sala);
        auditar(hospital, actor, request.activa() ? "SALA_ACTIVADA" : "SALA_DESACTIVADA",
                "sala:" + salaId, sala.getNombre());
        return aSalaResponse(sala);
    }

    @Transactional
    public SectorHospitalResponse crearSector(String subject, Long hospitalId, GuardarSectorRequest request) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidadHabilitada(hospital, request.especialidadId());
        String nombre = request.nombre().trim();
        if (sectores.existsByHospitalIdAndNombreIgnoreCase(hospitalId, nombre)) {
            throw new ConflictoDeEstadoException("Ya existe un sector con ese nombre en el hospital");
        }
        Sector sector = new Sector();
        sector.setNombre(nombre);
        sector.setActiva(true);
        sector.setHospital(hospital);
        sector.setEspecialidad(especialidad);
        sector = sectores.save(sector);
        auditar(hospital, actor, "SECTOR_CREADO", "sector:" + sector.getId(),
                nombre + " · " + especialidad.getNombre());
        return aSectorResponse(sector);
    }

    @Transactional
    public SectorHospitalResponse actualizarSector(String subject, Long hospitalId, Long sectorId,
            ActualizarSectorRequest request) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        Sector sector = sector(hospitalId, sectorId);
        EspecialidadMedica especialidad = especialidadHabilitada(hospital, request.especialidadId());
        String nombre = request.nombre().trim();
        if (sectores.existsByHospitalIdAndNombreIgnoreCaseAndIdNot(hospitalId, nombre, sectorId)) {
            throw new ConflictoDeEstadoException("Ya existe un sector con ese nombre en el hospital");
        }
        validarSectorSinPacientes(sectorId);
        sector.setNombre(nombre);
        sector.setEspecialidad(especialidad);
        sector.setActiva(request.activa());
        sector = sectores.save(sector);
        auditar(hospital, actor, "SECTOR_ACTUALIZADO", "sector:" + sectorId,
                nombre + " · " + especialidad.getNombre() + (sector.isActiva() ? " ACTIVO" : " INACTIVO"));
        return aSectorResponse(sector);
    }

    @Transactional
    public void eliminarSector(String subject, Long hospitalId, Long sectorId) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        Sector sector = sector(hospitalId, sectorId);
        validarSectorSinPacientes(sectorId);
        validarSectorSinSesionesActivas(sectorId);
        List<Sala> salasDelSector = salas.findBySectorId(sectorId);
        for (Sala sala : salasDelSector) {
            sala.setSector(null);
            salas.save(sala);
        }
        sectores.delete(sector);
        auditar(hospital, actor, "SECTOR_ELIMINADO", "sector:" + sectorId, sector.getNombre());
    }

    private Hospital hospital(Long id) {
        return hospitales.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Hospital no encontrado"));
    }

    private EspecialidadMedica especialidad(Long id) {
        return especialidades.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada"));
    }

    private EspecialidadMedica especialidadHabilitada(Hospital hospital, Long id) {
        return hospital.getEspecialidades().stream().filter(item -> item.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ConflictoDeEstadoException("La especialidad no está habilitada en el hospital"));
    }

    private Sala sala(Long hospitalId, Long salaId) {
        return salas.findByIdAndHospitalId(salaId, hospitalId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sala no encontrada"));
    }

    private Sector sector(Long hospitalId, Long sectorId) {
        return sectores.findByIdAndHospitalId(sectorId, hospitalId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sector no encontrado"));
    }

    private void validarSectorSinPacientes(Long sectorId) {
        List<Sala> salasDelSector = salas.findBySectorId(sectorId);
        if (salasDelSector.isEmpty()) return;
        List<Long> salaIds = salasDelSector.stream().map(Sala::getId).toList();
        if (consultasMedicas.existsBySalaIdInAndEstadoConsultaNotIn(salaIds,
                List.of(EstadoConsulta.FINALIZADA, EstadoConsulta.CANCELADA))) {
            throw new ConflictoDeEstadoException(
                    "No se puede modificar/eliminar un sector con salas que aún tienen pacientes asignados");
        }
    }

    private void validarSectorSinSesionesActivas(Long sectorId) {
        List<Sala> salasDelSector = salas.findBySectorId(sectorId);
        if (salasDelSector.isEmpty()) return;
        List<Long> salaIds = salasDelSector.stream().map(Sala::getId).toList();
        if (sesionesAtencionMedica.existsBySalaIdInAndEstadoIn(salaIds,
                List.of(EstadoSesionMedica.ACTIVA, EstadoSesionMedica.PAUSADA))) {
            throw new ConflictoDeEstadoException(
                    "No se puede eliminar un sector con sesiones de atención activas o pausadas");
        }
    }

    private SalaHospitalResponse aSalaResponse(Sala sala) {
        return new SalaHospitalResponse(sala.getId(), sala.getNombre(), sala.isActiva(), sala.getEspecialidad().getId(),
                sala.getEspecialidad().getCodigo(), sala.getEspecialidad().getNombre());
    }

    private SectorHospitalResponse aSectorResponse(Sector sector) {
        return new SectorHospitalResponse(sector.getId(), sector.getNombre(), sector.isActiva(),
                sector.getEspecialidad().getId(), sector.getEspecialidad().getCodigo(),
                sector.getEspecialidad().getNombre());
    }

    private void auditar(Hospital hospital, UsuarioAuth actor, String accion, String objetivo, String resultado) {
        AuditoriaHospital auditoria = new AuditoriaHospital();
        auditoria.setHospital(hospital);
        auditoria.setActor(actor);
        auditoria.setFecha(Instant.now());
        auditoria.setAccion(accion);
        auditoria.setObjetivo(objetivo);
        auditoria.setResultado(resultado);
        auditorias.save(auditoria);
    }
}
