package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.controllers.dtos.CredencialResponse;
import com.pretriage.backend.services.CredencialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ObraSocialController {

    private final CredencialService credencialService;

    @GetMapping("/obrasocial/credenciales")
    public ResponseEntity<List<CredencialResponse>> obtenerCredencialesPaciente(
            @AuthenticationPrincipal Jwt jwt
    ){
        return ResponseEntity.ok(
                credencialService.obtenerCredencialesPaciente(jwt.getSubject()));
    }

    @PostMapping("/obrasocial/credenciales")
    public ResponseEntity<Map<String, String>> cargarCredencialPaciente(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CredencialRequest request
    ){

        credencialService.cargarCredencialPaciente(jwt.getSubject(), request);

        return ResponseEntity.ok(Map.of("mensaje", "credencial cargada con éxito"));
    }

    @PostMapping("/pacientes/{idPaciente}/obrasocial/credencial")
    public ResponseEntity<Map<String, String>> cargarCredencialRecepcionista(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idPaciente") Long idPaciente,
            @Valid @RequestBody CredencialRequest request
    ){

        credencialService.cargarCredencialRecepcionista(jwt.getSubject(), idPaciente, request);

        return ResponseEntity.ok(Map.of("mensaje", "credencial cargada con éxito"));
    }

}