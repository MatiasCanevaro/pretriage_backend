package com.pretriage.backend.model.consultas;

import com.pretriage.backend.model.personas.Medico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class AsignacionSala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "id_sala_atencion", referencedColumnName = "id")
    private SalaAtencion salaAtendido;

    private LocalDateTime fechaHoraInicioAtencion;

    private LocalDateTime fechaHoraFinAtencion;

    @OneToOne
    @JoinColumn(name = "id_medico", referencedColumnName = "id")
    private Medico medicoAsignado;

    private boolean activo;

    public AsignacionSala() {
        this.activo = true;
    }
}
