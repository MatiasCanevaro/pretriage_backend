package com.pretriage.backend.model.personas;

import java.time.LocalDate;
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

    private Genero generoBiologico;

    @Enumerated(EnumType.STRING)
    private Genero generoConElQueSeIdentifica;

    private String nombre;
    private String apellido;
    @Column(unique = true)
    private String numeroDocumento;
    @Enumerated(EnumType.STRING)
    private TipoDocumento tipoDocumento;
    @Enumerated(EnumType.STRING)
    private OrigenRegistroPaciente origenRegistro;

    private LocalDate fechaNacimiento;

    private Double peso;

    private Integer altura;

    public Paciente() {
        this.credenciales = new ArrayList<>();
    }
}
