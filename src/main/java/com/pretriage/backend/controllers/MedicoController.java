package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.services.MedicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medico")
@RequiredArgsConstructor
public class MedicoController {

    private final MedicoService medicoService;


    @GetMapping("/hospitales/{idHospital}/pacientes/atender")
    public ResponseEntity<List<PacienteDTO>> obtenerTodosLosPacientesPosiblesAAtender(
            @PathVariable("idHospital") Long idHospital,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(medicoService.obtenerTodosPacientesParaAtender(idHospital, jwt.getSubject()));
    }


    @GetMapping("/hospitales/{idHospital}/pacientes")
    public ResponseEntity<List<PacienteDTO>> obtenerTodosLosPacientesAtendidos(
            @PathVariable("idHospital") Long idHospital,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(medicoService.findAllPacientesAtendidos(idHospital, jwt.getSubject()));
    }

    @PostMapping("/hospitales/{idHospital}/pacientes/atender/{idPaciente}")
    public ResponseEntity<Map<String, String>> seleccionarPaciente(
            @PathVariable("idHospital") Long idHospital,
            @PathVariable("idPaciente") Long idPaciente,
            @AuthenticationPrincipal Jwt jwt
    ){
        medicoService.seleccionarAPaciente(idPaciente, idHospital, jwt.getSubject());

        return ResponseEntity.ok(Map.of("message", "Paciente seleccionado con éxito"));
    }

}

