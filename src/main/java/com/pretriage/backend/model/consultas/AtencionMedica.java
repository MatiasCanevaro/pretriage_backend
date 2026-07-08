package com.pretriage.backend.model.consultas;

import com.pretriage.backend.model.hospitales.Hospital;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class AtencionMedica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fechaHoraInicioAtencion;
    private LocalDateTime fechaHoraFinAtencion;

    @ManyToOne
    @JoinColumn(name = "id_asignacion_sala", referencedColumnName = "id")
    private AsignacionSala salaAtendido;

    @OneToOne
    @JoinColumn(name = "id_consulta_medica", referencedColumnName = "id")
    private ConsultaMedica consultaMedica;

    @ManyToOne
    @JoinColumn(name = "id_hospital", referencedColumnName = "id")
    private Hospital hospital;
}
