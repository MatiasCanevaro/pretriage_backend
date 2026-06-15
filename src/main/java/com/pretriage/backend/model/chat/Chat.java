package com.pretriage.backend.model.chat;

import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.UsuarioAuth;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Chat {

    public Chat(Paciente paciente) {
        this.mensajes = new ArrayList<>();
        this.fechaHoraCreacion = LocalDateTime.now();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany
    @JoinColumn(name="chat_id", referencedColumnName = "id")
    private List<Mensaje> mensajes;

    private LocalDateTime fechaHoraCreacion;

    @OneToOne
    @JoinColumn(name = "paciente_id", referencedColumnName = "id")
    private Paciente paciente;
}
