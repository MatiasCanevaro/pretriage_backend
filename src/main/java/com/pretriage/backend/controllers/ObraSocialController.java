package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.controllers.dtos.CredencialResponse;
import com.pretriage.backend.controllers.dtos.ObraSocialDTO;
import com.pretriage.backend.model.hospitales.ObraSocial;
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

    @DeleteMapping("/obrasocial/credenciales/{idCredencial}")
    public ResponseEntity<Map<String, String>> eliminarCredencialPaciente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idCredencial") Long idCredencial
    ){

        credencialService.eliminarCredencial(idCredencial, jwt.getSubject());

        return ResponseEntity.ok(Map.of("mensaje", "credencial eliminada con éxito"));
    }

    @DeleteMapping("/pacientes/{idPaciente}/obrasocial/credenciales/{idCredencial}")
    public ResponseEntity<Map<String, String>> eliminarCredencialRecepcionista(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idPaciente") Long idPaciente,
            @PathVariable("idCredencial") Long idCredencial
    ){

        credencialService.eliminarCredencialRecepcionista(idCredencial, idPaciente, jwt.getSubject());

        return ResponseEntity.ok(Map.of("mensaje", "credencial eliminada con éxito"));
    }

    @PutMapping("/obrasocial/credenciales/{idCredencial}")
    public ResponseEntity<Map<String, String>> editarCredencialPaciente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idCredencial") Long idCredencial,
            @Valid @RequestBody CredencialRequest request
    ){

        credencialService.editarCredencialPaciente(idCredencial, jwt.getSubject(), request);

        return ResponseEntity.ok(Map.of("mensaje", "credencial actualizada con éxito"));
    }

    @PutMapping("/pacientes/{idPaciente}/obrasocial/credenciales/{idCredencial}")
    public ResponseEntity<Map<String, String>> editarCredencialRecepcionista(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idPaciente") Long idPaciente,
            @PathVariable("idCredencial") Long idCredencial,
            @Valid @RequestBody CredencialRequest request
    ){

        credencialService.editarCredencialRecepcionista(idCredencial, idPaciente, jwt.getSubject(), request);

        return ResponseEntity.ok(Map.of("mensaje", "credencial actualizada con éxito"));
    }

    @PostMapping("/obrasocial")
    public ResponseEntity<Map<String, String>> cargarObraSocialAdmin(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ObraSocialDTO request
    ){

        credencialService.cargarObraSocialAdmin(jwt.getSubject(), request);

        return ResponseEntity.ok(Map.of("mensaje", "obra social cargada con éxito"));
    }

}