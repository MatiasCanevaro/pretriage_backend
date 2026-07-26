package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.acceso.StaffAccessDtos.*;
import com.pretriage.backend.services.StaffAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StaffAccessController {
    private final StaffAccessService service;

    @GetMapping("/api/staff/me")
    public StaffMeResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return service.obtenerContexto(jwt.getSubject());
    }

    @GetMapping("/api/admin/hospitales/{hospitalId}/personal")
    public List<PersonalResponse> personal(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                           @PathVariable Long hospitalId) {
        return service.listarPersonal(jwt.getSubject(), hospitalId);
    }

    @GetMapping("/api/admin/hospitales/{hospitalId}/invitaciones")
    public List<InvitacionResponse> invitaciones(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long hospitalId) {
        return service.listarInvitaciones(jwt.getSubject(), hospitalId);
    }

    @PostMapping("/api/admin/hospitales/{hospitalId}/invitaciones")
    public InvitacionResponse invitar(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                      @PathVariable Long hospitalId,
                                      @Valid @RequestBody CrearInvitacionRequest request) {
        return service.crearInvitacion(jwt.getSubject(), hospitalId, request);
    }

    @DeleteMapping("/api/admin/hospitales/{hospitalId}/invitaciones/{invitacionId}")
    public ResponseEntity<Void> revocar(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                        @PathVariable Long hospitalId, @PathVariable Long invitacionId) {
        service.revocarInvitacion(jwt.getSubject(), hospitalId, invitacionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/hospitales/{hospitalId}/invitaciones/{invitacionId}/reenviar")
    public InvitacionResponse reenviar(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                        @PathVariable Long hospitalId, @PathVariable Long invitacionId) {
        return service.reenviarInvitacion(jwt.getSubject(), hospitalId, invitacionId);
    }

    @PatchMapping("/api/admin/hospitales/{hospitalId}/membresias/{membresiaId}")
    public MembresiaResponse actualizarEstado(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long hospitalId, @PathVariable Long membresiaId,
                                               @Valid @RequestBody ActualizarMembresiaRequest request) {
        return service.actualizarEstado(jwt.getSubject(), hospitalId, membresiaId, request);
    }

    @PutMapping("/api/admin/hospitales/{hospitalId}/membresias/{membresiaId}/roles")
    public MembresiaResponse actualizarRoles(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long hospitalId, @PathVariable Long membresiaId,
                                              @Valid @RequestBody ActualizarRolesRequest request) {
        return service.actualizarRoles(jwt.getSubject(), hospitalId, membresiaId, request);
    }

    @GetMapping("/api/admin/hospitales/{hospitalId}/auditoria")
    public List<AuditoriaResponse> auditoria(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                             @PathVariable Long hospitalId) {
        return service.listarAuditoria(jwt.getSubject(), hospitalId);
    }

    @PostMapping("/api/platform/hospitales/{hospitalId}/primer-admin/invitaciones")
    public InvitacionResponse primerAdmin(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                           @PathVariable Long hospitalId,
                                           @Valid @RequestBody CrearInvitacionRequest request) {
        return service.crearPrimerAdmin(jwt.getSubject(), hospitalId, request);
    }

    @GetMapping("/api/platform/hospitales")
    public List<HospitalPlataformaResponse> hospitalesPlataforma(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return service.listarHospitalesPlataforma(jwt.getSubject());
    }

    @GetMapping("/api/invitaciones/{token}/resumen")
    public InvitacionResumenResponse resumen(@PathVariable String token) {
        return service.resumir(token);
    }

    @PostMapping("/api/invitaciones/{token}/aceptar")
    public MembresiaResponse aceptar(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                     @PathVariable String token) {
        return service.aceptar(jwt.getSubject(), token);
    }

    @PostMapping("/api/invitaciones/{token}/registro")
    public MembresiaResponse registrar(@PathVariable String token,
                                       @Valid @RequestBody RegistrarInvitadoRequest request) {
        return service.registrarYAceptar(token, request);
    }
}
