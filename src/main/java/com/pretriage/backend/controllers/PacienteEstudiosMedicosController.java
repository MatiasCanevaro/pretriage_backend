package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.EstudioClinicoDTO;
import com.pretriage.backend.services.EstudioClinicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PacienteEstudiosMedicosController {


    private final EstudioClinicoService estudioClinicoService;

    @PostMapping("/estudios")
    public ResponseEntity<Map<String, String>> subirEstudioClinico(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @Valid @RequestBody EstudioClinicoDTO estudioClinicoRequest) {

        estudioClinicoService.subirArchivoEstudioClinico(jwt.getSubject(), file, estudioClinicoRequest);

        return ResponseEntity.ok(Map.of("message", "Archivo subido exitosamente"));
    }

    @DeleteMapping("/estudios/{idEstudio}")
    public ResponseEntity<Map<String, String>> eliminarEstudioClinico(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("idEstudio") Long idEstudio) {

        estudioClinicoService.eliminarArchivoEstudioClinico(jwt.getSubject(), idEstudio);

        return ResponseEntity.ok(Map.of("message", "Archivo eliminado exitosamente"));
    }

    //todo falta agregar el endpoint para obtener todos los estudios medicos del paciente
    //todo falta agregar el endpoint para obtener un estudio medico por id

}
