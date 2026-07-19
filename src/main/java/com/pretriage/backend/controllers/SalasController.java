package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.services.SalaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SalasController {

    private final SalaService salaService;

    @PostMapping("/hospitales/{hospitalId}/especialidades/{idEspecialidadMedica}/salas")
    public ResponseEntity<Map<String, String>> crearSalaAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SalaDTO salaRequest,
            @PathVariable("hospitalId") Long hospitalId,
            @PathVariable("idEspecialidadMedica") Long idEspecialidadMedica
            ){

        salaService.crearSalaAdmin(jwt.getSubject(), salaRequest, hospitalId, idEspecialidadMedica);

        return ResponseEntity.ok(Map.of("message", "Sala creada exitosamente"));
    }

    @DeleteMapping("/salas/{salaId}")
    public ResponseEntity<Map<String, String>> eliminarSalaAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("salaId") Long salaId
    ){
        salaService.eliminarSalaAdmin(jwt.getSubject(), salaId);

        return ResponseEntity.ok(Map.of("meesage", "Sala eliminada con éxito"));
    }
}
