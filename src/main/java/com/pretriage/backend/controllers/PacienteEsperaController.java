package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.EstadoConsultaPacienteDTO;
import com.pretriage.backend.services.EsperaPacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PacienteEsperaController {

    private final EsperaPacienteService esperaPacienteService;

    @PostMapping("/api/paciente/consulta/ausentarme")
    public ResponseEntity<EstadoConsultaPacienteDTO> ausentarme(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(esperaPacienteService.ausentarme(jwt.getSubject()));
    }

    @PostMapping("/api/paciente/consulta/estoy-atrasado")
    public ResponseEntity<EstadoConsultaPacienteDTO> estoyAtrasado(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(esperaPacienteService.estoyAtrasado(jwt.getSubject()));
    }

    @PostMapping("/api/paciente/consulta/sigo-asistiendo")
    public ResponseEntity<EstadoConsultaPacienteDTO> sigoAsistiendo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(esperaPacienteService.sigoAsistiendo(jwt.getSubject()));
    }

    @PostMapping("/api/paciente/consulta/llegue")
    public ResponseEntity<EstadoConsultaPacienteDTO> llegue(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(esperaPacienteService.llegue(jwt.getSubject()));
    }

    @GetMapping("/api/paciente/consulta/estado")
    public ResponseEntity<EstadoConsultaPacienteDTO> obtenerEstado(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(esperaPacienteService.obtenerEstado(jwt.getSubject()));
    }
}
