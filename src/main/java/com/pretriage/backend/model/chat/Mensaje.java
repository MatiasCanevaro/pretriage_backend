package com.pretriage.backend.model.chat;

import java.time.LocalDateTime;

import com.pretriage.backend.model.personas.Paciente;
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

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name="paciente_id", referencedColumnName = "id")
    private Paciente pacienteAutor;

    private LocalDateTime fechaHoraEnvio;
}
