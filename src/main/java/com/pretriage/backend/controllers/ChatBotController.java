package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.controllers.dtos.MensajeDTO;
import com.pretriage.backend.mappers.MapperChat;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoChat;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.services.ChatService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatBotController {

    private final ChatService chatService;

    @PostMapping()
    public ChatDTO iniciarChat(@AuthenticationPrincipal UserDetails userDetails){
        String idPaciente = userDetails.getUsername();
        return chatService.iniciarChat(idPaciente);
    }

    @GetMapping("{id}")
    public ChatDTO getChatActual(
            @PathVariable String idChat,
            @AuthenticationPrincipal UserDetails userDetails) {
        Chat chat = chatService.obtenerChat(idChat);
        return MapperChat.toDTO(chat);
    }
//TODO
    @PostMapping("/mensaje")
    public MensajeDTO enviarMensaje (@RequestBody String mensaje){
        return null;
    }
}



