package com.pretriage.backend.model.chat;

import com.pretriage.backend.model.personas.Paciente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Mensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutorMensaje autor;

    @ManyToOne
    @JoinColumn(name = "paciente_id", referencedColumnName = "id")
    private Paciente pacienteAutor;

    @Column(nullable = false)
    private LocalDateTime fechaHoraEnvio;

    public Mensaje(String contenido, AutorMensaje autor, Paciente pacienteAutor) {
        this.contenido = contenido;
        this.autor = autor;
        this.pacienteAutor = pacienteAutor;
        this.fechaHoraEnvio = LocalDateTime.now();
    }
}
