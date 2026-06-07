package com.pretriage.backend.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hello")
public class HelloController { //TODO usar esto para probar oauth, borrar luego


    @GetMapping("")
    public ResponseEntity<Map<String, String>> saludar() {
        return ResponseEntity.ok(Map.of(
                "mensaje", "holaa que hace"
        ));
    }

    @GetMapping("/nose")
    public ResponseEntity<Map<String, Object>> privateEndpoint(@AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(Map.of(
                "message", "Necesitar autenticarte!!",
                "claims", jwt.getClaims()
        ));
    }

}

