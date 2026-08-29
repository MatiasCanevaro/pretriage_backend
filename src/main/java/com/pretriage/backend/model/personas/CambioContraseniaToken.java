package com.pretriage.backend.model.personas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class CambioContraseniaToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", referencedColumnName = "id", nullable = false)
    private UsuarioAuth usuario;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Column(nullable = false)
    private LocalDateTime fechaHoraCreacion;

    @Column(nullable = false)
    private LocalDateTime fechaHoraExpiracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCambioContrasenia estado = EstadoCambioContrasenia.PENDIENTE;

    public CambioContraseniaToken(UsuarioAuth usuario, String token, LocalDateTime fechaHoraCreacion, LocalDateTime fechaHoraExpiracion) {
        this.usuario = usuario;
        this.token = token;
        this.fechaHoraCreacion = fechaHoraCreacion;
        this.fechaHoraExpiracion = fechaHoraExpiracion;
        this.estado = EstadoCambioContrasenia.PENDIENTE;
    }

    public boolean expiro() {
        return LocalDateTime.now().isAfter(fechaHoraExpiracion);
    }

    public boolean esPendienteValido() {
        return estado == EstadoCambioContrasenia.PENDIENTE && !expiro();
    }
}
