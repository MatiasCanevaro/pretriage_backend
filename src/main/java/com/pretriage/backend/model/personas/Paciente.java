package com.pretriage.backend.model.personas;

import java.util.ArrayList;
import java.util.List;

import com.pretriage.backend.model.hospitales.Coordenada;
import com.pretriage.backend.model.hospitales.Credencial;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "auth_id", referencedColumnName = "id")
    private UsuarioAuth usuarioAuth;

    @OneToMany(
            mappedBy = "paciente",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Credencial> credenciales;

    @OneToOne
    @JoinColumn(name="id_coordenada", referencedColumnName = "id")
    private Coordenada coordenadaActual;

    public Paciente() {
        this.credenciales = new ArrayList<>();
    }
}
