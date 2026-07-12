package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.*;
import com.pretriage.backend.services.AtencionMedicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MedicoController {

    private final AtencionMedicoService atencionMedicoService;

    @GetMapping("/api/medico/asignaciones")
    public ResponseEntity<List<AsignacionMedicoDTO>> obtenerAsignaciones(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(atencionMedicoService.obtenerAsignaciones(jwt.getSubject()));
    }

    @GetMapping("/api/hospitales/{hospitalId}/salas")
    public ResponseEntity<List<SalaDTO>> obtenerSalas(
            @PathVariable Long hospitalId,
            @RequestParam String codigoEspecialidad) {
        return ResponseEntity.ok(atencionMedicoService.obtenerSalas(hospitalId, codigoEspecialidad));
    }

    @PostMapping("/api/medico/sesiones")
    public ResponseEntity<SesionAtencionMedicaDTO> iniciarSesion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid IniciarSesionMedicaRequest request) {
        return ResponseEntity.ok(atencionMedicoService.iniciarSesion(
                jwt.getSubject(),
                request.getHospitalId(),
                request.getCodigoEspecialidad(),
                request.getSalaId()));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/pausar")
    public ResponseEntity<SesionAtencionMedicaDTO> pausarSesion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId) {
        return ResponseEntity.ok(atencionMedicoService.pausarSesion(jwt.getSubject(), sesionId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/reanudar")
    public ResponseEntity<SesionAtencionMedicaDTO> reanudarSesion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId) {
        return ResponseEntity.ok(atencionMedicoService.reanudarSesion(jwt.getSubject(), sesionId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/cerrar")
    public ResponseEntity<SesionAtencionMedicaDTO> cerrarSesion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId) {
        return ResponseEntity.ok(atencionMedicoService.cerrarSesion(jwt.getSubject(), sesionId));
    }


    @GetMapping("/api/medico/sesiones/{sesionId}/pacientes-disponibles")
    public ResponseEntity<List<ConsultaLlamadaDTO>> listarPacientesDisponibles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId) {
        return ResponseEntity.ok(atencionMedicoService.listarPacientesDisponibles(jwt.getSubject(), sesionId));
    }

    @GetMapping("/api/medico/atenciones")
    public ResponseEntity<List<AtencionMedicaDTO>> obtenerHistorial(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(atencionMedicoService.obtenerHistorial(jwt.getSubject()));
    }

    @GetMapping("/api/medico/pacientes/{pacienteId}/historial-clinico")
    public ResponseEntity<List<EstudioClinicoDTO>> obtenerHistorialClinico(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long pacienteId) {
        return ResponseEntity.ok(atencionMedicoService.obtenerHistorialClinico(jwt.getSubject(), pacienteId));
    }

    @GetMapping("/api/medico/pacientes/{pacienteId}/historial-clinico/{estudioId}/reporte")
    public ResponseEntity<EstudioClinicoDTO> obtenerMetadataDeUnEstudioClinico(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long pacienteId,
            @PathVariable Long estudioId
    ){
        return ResponseEntity.ok(atencionMedicoService.obtenerEstudioClinico(jwt.getSubject(), pacienteId, estudioId));
    }

    @GetMapping("/api/medico/pacientes/{pacienteId}/ultimos-reportes")
    public ResponseEntity<List<EstudioClinicoDTO>> obtenerHistorialClinicoMasActuales(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long pacienteId
    ){
        return ResponseEntity.ok(atencionMedicoService.obtenerUltimosEstudiosClinicos(jwt.getSubject(), pacienteId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/llamar-proximo")
    public ResponseEntity<ConsultaLlamadaDTO> llamarProximo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId) {
        return ResponseEntity.ok(atencionMedicoService.llamarProximo(jwt.getSubject(), sesionId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/consultas/{consultaId}/presente")
    public ResponseEntity<ConsultaLlamadaDTO> confirmarPresente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId,
            @PathVariable Long consultaId) {
        return ResponseEntity.ok(atencionMedicoService.confirmarPresente(jwt.getSubject(), sesionId, consultaId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/consultas/{consultaId}/ausente")
    public ResponseEntity<ConsultaLlamadaDTO> marcarAusente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId,
            @PathVariable Long consultaId) {
        return ResponseEntity.ok(atencionMedicoService.marcarAusente(jwt.getSubject(), sesionId, consultaId));
    }

    @PostMapping("/api/medico/sesiones/{sesionId}/consultas/{consultaId}/finalizar")
    public ResponseEntity<ConsultaLlamadaDTO> finalizarConsulta(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sesionId,
            @PathVariable Long consultaId) {
        return ResponseEntity.ok(atencionMedicoService.finalizarConsulta(jwt.getSubject(), sesionId, consultaId));
    }
}

