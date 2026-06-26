package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.ChatTurnResponse;
import com.pretriage.backend.controllers.dtos.EnviarMensajeRequest;
import com.pretriage.backend.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatBotController {

    private final ChatService chatService;

    @PostMapping
    public ChatDTO iniciarChat(@AuthenticationPrincipal Jwt jwt) {
        return chatService.iniciarChat(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public ChatDTO getChatActual(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return chatService.obtenerChat(id, jwt.getSubject());
    }

    @PostMapping("/{id}/mensajes")
    public ChatTurnResponse enviarMensaje(
            @PathVariable String id,
            @Valid @RequestBody EnviarMensajeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return chatService.enviarMensaje(id, jwt.getSubject(), request.contenido());
    }
}
