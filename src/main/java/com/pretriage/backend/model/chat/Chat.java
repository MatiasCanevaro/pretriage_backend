package com.pretriage.backend.model.chat;

import com.pretriage.backend.model.personas.Paciente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "chat_id", referencedColumnName = "id")
    @OrderBy("fechaHoraEnvio ASC")
    private List<Mensaje> mensajes = new ArrayList<>();

    private LocalDateTime fechaHoraCreacion;

    @OneToOne
    @JoinColumn(name = "paciente_id", referencedColumnName = "id")
    private Paciente paciente;

    private boolean finalizado;

    @Column(columnDefinition = "TEXT")
    private String resultadoTriageJson;

    public Chat(Paciente paciente) {
        this.paciente = paciente;
        this.fechaHoraCreacion = LocalDateTime.now();
        this.finalizado = false;
    }

    public void agregarMensaje(Mensaje mensaje) {
        mensajes.add(mensaje);
    }
}
