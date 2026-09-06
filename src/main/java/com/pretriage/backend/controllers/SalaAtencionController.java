package com.pretriage.backend.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.pretriage.backend.services.SalaAtencionNotifier;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/atencion/sala")
@RequiredArgsConstructor
public class SalaAtencionController {

    private final SalaAtencionNotifier notifier;

    @GetMapping(value = "/suscribirse/{consultaId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suscribirseSalaAtencion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long consultaId) {
        return notifier.suscribirse(jwt.getSubject(), consultaId);
    }

    @GetMapping(value = "/desuscribirse/{consultaId}")
    public ResponseEntity<Void> desuscribirseSalaAtencion(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long consultaId) {
        notifier.desuscribirse(jwt.getSubject(), consultaId);

        return ResponseEntity.noContent().build();
    }
}
