package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.CambiarContraseniaRequest;
import com.pretriage.backend.controllers.dtos.SolicitarTokenCambioContraseniaRequest;
import com.pretriage.backend.controllers.dtos.SolicitarTokenCambioContraseniaResponse;
import com.pretriage.backend.services.CambioContraseniaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/cambio-contrasenia")
@RequiredArgsConstructor
public class CambioContraseniaController {

    private final CambioContraseniaService cambioContraseniaService;

    @PostMapping("/solicitar-token")
    public ResponseEntity<SolicitarTokenCambioContraseniaResponse> solicitarToken(
            @Valid @RequestBody SolicitarTokenCambioContraseniaRequest request) {

        return ResponseEntity.ok(
                cambioContraseniaService.obtenerTokenCambioContraseña(request.getEmail()));
    }

    @GetMapping("/validar")
    public ResponseEntity<Map<String, Object>> validarToken(@RequestParam String token) {
        cambioContraseniaService.validarTokenCambioContrasenia(token);
        return ResponseEntity.ok(Map.of("valido", true, "message", "Token válido"));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> cambiarContrasenia(
            @Valid @RequestBody CambiarContraseniaRequest request) {

        cambioContraseniaService.cambiarContraseña(request.getNuevaContrasenia(), request.getToken());
        return ResponseEntity.ok(Map.of("message", "Contraseña cambiada con éxito"));
    }
}
