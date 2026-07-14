package com.pretriage.backend.model.consultas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.pretriage.backend.model.chat.Mensaje;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ConsultaMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fechaHoraCreacion;

    @Column(unique = true)
    private String codigoLlamado;

    @ManyToOne
    @JoinColumn(name="id_hospital", referencedColumnName = "id")
    private Hospital hospital;

    @ManyToOne
    @JoinColumn(name="id_especialidad_medica", referencedColumnName = "id")
    private EspecialidadMedica especialidad;

    @ManyToOne
    @JoinColumn(name="id_medico", referencedColumnName = "id")
    private Medico medico;

    @ManyToOne
    @JoinColumn(name="id_sala", referencedColumnName = "id")
    private Sala sala;

    @ManyToOne
    @JoinColumn(name="id_paciente", referencedColumnName = "id")
    private Paciente paciente;

    @OneToMany
    @JoinColumn(name ="id_consulta_medica", referencedColumnName = "id")
    private List<Sintoma> sintomasBot;


    @Enumerated(EnumType.STRING)
    private NivelDeGravedad nivelDeGravedadBot;

    @Enumerated(EnumType.STRING)
    private NivelDeGravedad nivelDeGravedadMedico;

    @Enumerated(EnumType.STRING)
    private EstadoConsulta estadoConsulta;

    @OneToMany
    @JoinColumn(name = "id_consulta_medica", referencedColumnName = "id")
    private List<Mensaje> chat;

    public ConsultaMedica(){
        this.sintomasBot = new ArrayList<>();
        this.chat = new ArrayList<>();
        this.estadoConsulta = EstadoConsulta.PENDIENTE;
    }

}
