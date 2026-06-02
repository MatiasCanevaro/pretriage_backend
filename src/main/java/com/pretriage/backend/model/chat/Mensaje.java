package com.pretriage.backend.model.chat;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Mensaje {
    private String contenido;
    private AutorMensaje autor;
    private LocalDateTime timestamp;
}
