package com.pretriage.backend.mappers;


import com.pretriage.backend.controllers.dtos.ChatDTO;
import com.pretriage.backend.model.chat.Chat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;


@RequiredArgsConstructor
public class MapperChat {
    public static ChatDTO toDTO(Chat chat){
        return new ChatDTO(
                chat.getMensajes().stream().map(MapperMensaje::toDTO).collect(Collectors.toList()),
                chat.getFechaHoraCreacion());
    }
    public static Chat toChat(ChatDTO dto){
        return null;
    }
}
