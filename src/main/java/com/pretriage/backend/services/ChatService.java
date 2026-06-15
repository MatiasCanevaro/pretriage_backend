package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.mappers.MapperChat;
import com.pretriage.backend.model.chat.Chat;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoChat;
import com.pretriage.backend.repositories.RepoPacientes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ChatService {
    private final RepoChat repoChat;
    private final RepoPacientes repoPacientes;

    public ChatDTO iniciarChat(String idPaciente){
        Paciente paciente = repoPacientes.findByUsuarioAuthId(idPaciente).orElseThrow(()-> new RuntimeException("Usuario no encontrado")); //TODO mejorar esta excepción
        Chat chat = new Chat(paciente);
        repoChat.save(chat);

        ChatDTO chatDTO = MapperChat.toDTO(chat);
        BeanUtils.copyProperties(chat, chatDTO);
        return chatDTO;
    }

    public Chat obtenerChat(String idChat){
        return repoChat.getReferenceById(Long.valueOf(idChat));
    }
}
