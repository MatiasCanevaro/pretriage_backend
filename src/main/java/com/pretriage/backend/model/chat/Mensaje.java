package com.pretriage.backend.model.chat;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Mensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String contenido;

    @Enumerated(EnumType.STRING)
    private AutorMensaje autor;

    private LocalDateTime timestamp;
}
