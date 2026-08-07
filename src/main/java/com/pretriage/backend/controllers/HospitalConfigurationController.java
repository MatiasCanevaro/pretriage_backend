package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.*;
import com.pretriage.backend.services.HospitalConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @DeleteMapping("/especialidades/{especialidadId}")
    public ConfiguracionHospitalResponse deshabilitarEspecialidad(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long hospitalId, @PathVariable Long especialidadId) {
        return service.deshabilitarEspecialidad(jwt.getSubject(), hospitalId, especialidadId);
    }

    @PostMapping("/salas")
    public SalaHospitalResponse crearSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @Valid @RequestBody GuardarSalaRequest request) {
        return service.crearSala(jwt.getSubject(), hospitalId, request);
    }

    @PutMapping("/salas/{salaId}")
    public SalaHospitalResponse actualizarSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long salaId, @Valid @RequestBody GuardarSalaRequest request) {
        return service.actualizarSala(jwt.getSubject(), hospitalId, salaId, request);
    }

    @PatchMapping("/salas/{salaId}/estado")
    public SalaHospitalResponse actualizarEstadoSala(@AuthenticationPrincipal Jwt jwt, @PathVariable Long hospitalId,
            @PathVariable Long salaId, @Valid @RequestBody ActualizarEstadoSalaRequest request) {
        return service.actualizarEstadoSala(jwt.getSubject(), hospitalId, salaId, request);
    }
}
