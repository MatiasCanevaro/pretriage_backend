package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.*;
import com.pretriage.backend.exceptions.ConflictoDeEstadoException;
import com.pretriage.backend.exceptions.RecursoNoEncontradoException;
import com.pretriage.backend.model.acceso.AuditoriaHospital;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoAuditoriasHospital;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoSalas;
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
        return new ConfiguracionHospitalResponse(catalogo, salasHospital);
    }

    @Transactional
    public ConfiguracionHospitalResponse habilitarEspecialidad(String subject, Long hospitalId, Long especialidadId) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidad(especialidadId);
        boolean yaHabilitada = hospital.getEspecialidades().stream().anyMatch(item -> item.getId().equals(especialidadId));
        if (!yaHabilitada) {
            hospital.getEspecialidades().add(especialidad);
            hospitales.save(hospital);
            auditar(hospital, actor, "ESPECIALIDAD_HABILITADA", "especialidad:" + especialidadId, especialidad.getNombre());
        }
        return obtener(subject, hospitalId);
    }

    @Transactional
    public ConfiguracionHospitalResponse deshabilitarEspecialidad(String subject, Long hospitalId, Long especialidadId) {
        UsuarioAuth actor = staffAccessService.exigirAdminHospital(subject, hospitalId);
        Hospital hospital = hospital(hospitalId);
        EspecialidadMedica especialidad = especialidad(especialidadId);
        if (salas.existsByHospitalIdAndEspecialidadIdAndActivaTrue(hospitalId, especialidadId)) {
            throw new ConflictoDeEstadoException("Desactivá las salas de la especialidad antes de quitarla del hospital");
        }
        boolean removida = hospital.getEspecialidades().removeIf(item -> item.getId().equals(especialidadId));
        if (removida) {
            hospitales.save(hospital);
            auditar(hospital, actor, "ESPECIALIDAD_DESHABILITADA", "especialidad:" + especialidadId, especialidad.getNombre());
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
    public SalaHospitalResponse actualizarSala(String subject, Long hospitalId, Long salaId, GuardarSalaRequest request) {
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
        if (request.activa()) especialidadHabilitada(hospital, sala.getEspecialidad().getId());
        sala.setActiva(request.activa());
        sala = salas.save(sala);
        auditar(hospital, actor, request.activa() ? "SALA_ACTIVADA" : "SALA_DESACTIVADA",
                "sala:" + salaId, sala.getNombre());
        return aSalaResponse(sala);
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

    private SalaHospitalResponse aSalaResponse(Sala sala) {
        return new SalaHospitalResponse(sala.getId(), sala.getNombre(), sala.isActiva(), sala.getEspecialidad().getId(),
                sala.getEspecialidad().getCodigo(), sala.getEspecialidad().getNombre());
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
