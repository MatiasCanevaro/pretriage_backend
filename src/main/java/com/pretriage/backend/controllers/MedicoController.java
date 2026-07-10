package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.services.MedicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/medico")
@RequiredArgsConstructor
public class MedicoController {

    private final MedicoService medicoService;


    @GetMapping("/hospitales/{idHospital}/pacientes/atender")
    public ResponseEntity<List<PacienteDTO>> obtenerTodosLosPacientesPosiblesAAtender(
            @PathVariable Long idHospital,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(medicoService.obtenerTodosPacientesParaAtender(idHospital, jwt.getSubject()));
    }


    @GetMapping("/hospitales/{idHospital}/pacientes")
    public ResponseEntity<List<PacienteDTO>> obtenerTodosLosPacientesAtendidos(
            @PathVariable Long idHospital,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(medicoService.findAllPacientesAtendidos(idHospital, jwt.getSubject()));
    }


}

