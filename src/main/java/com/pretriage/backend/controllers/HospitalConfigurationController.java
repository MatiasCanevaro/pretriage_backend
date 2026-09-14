package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.*;
import com.pretriage.backend.services.HospitalConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/hospitales/{hospitalId}/configuracion")
public class HospitalConfigurationController {
    private final HospitalConfigurationService service;

    @GetMapping
    public ConfiguracionHospitalResponse obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId) {
        return service.obtener(jwt.getSubject(), hospitalId);
    }

    @PostMapping("/especialidades/{especialidadId}")
    public ConfiguracionHospitalResponse habilitarEspecialidad(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long hospitalId, @PathVariable Long especialidadId) {
        return service.habilitarEspecialidad(jwt.getSubject(), hospitalId, especialidadId);
    }

    @DeleteMapping("/sectores/{sectorId}/especialidades/{especialidadId}")
    public ConfiguracionHospitalResponse deshabilitarEspecialidad(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long hospitalId, @PathVariable Long sectorId, @PathVariable Long especialidadId) {
        return service.deshabilitarEspecialidad(jwt.getSubject(), hospitalId, especialidadId, sectorId);
    }

    @PostMapping("/sectores/{sectorId}/salas")
    public SalaHospitalResponse crearSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long sectorId, @Valid @RequestBody GuardarSalaRequest request) {
        return service.crearSala(jwt.getSubject(), hospitalId, sectorId, request);
    }

    @PutMapping("/sectores/{sectorId}/salas/{salaId}")
    public SalaHospitalResponse actualizarSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long sectorId, @PathVariable Long salaId, @Valid @RequestBody GuardarSalaRequest request) {
        return service.actualizarSala(jwt.getSubject(), hospitalId, sectorId, salaId, request);
    }

    @PatchMapping("/sectores/{sectorId}/salas/{salaId}/estado")
    public SalaHospitalResponse actualizarEstadoSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long sectorId, @PathVariable Long salaId,
            @Valid @RequestBody ActualizarEstadoSalaRequest request) {
        return service.actualizarEstadoSala(jwt.getSubject(), hospitalId, sectorId, salaId, request);
    }

    @PostMapping("/sectores")
    public SectorHospitalResponse crearSector(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @Valid @RequestBody GuardarSectorRequest request) {
        return service.crearSector(jwt.getSubject(), hospitalId, request);
    }

    @PutMapping("/sectores/{sectorId}")
    public SectorHospitalResponse actualizarSector(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long sectorId, @Valid @RequestBody ActualizarSectorRequest request) {
        return service.actualizarSector(jwt.getSubject(), hospitalId, sectorId, request);
    }

    @DeleteMapping("/sectores/{sectorId}")
    public ResponseEntity<Void> eliminarSector(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long sectorId) {
        service.eliminarSector(jwt.getSubject(), hospitalId, sectorId);
        return ResponseEntity.noContent().build();
    }

}
