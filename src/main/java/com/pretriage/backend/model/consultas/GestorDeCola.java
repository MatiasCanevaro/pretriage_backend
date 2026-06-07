package com.pretriage.backend.model.consultas;

import java.util.ArrayList;
import java.util.List;

import com.pretriage.backend.model.hospitales.Hospital;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class GestorDeCola {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne
    @JoinColumn(name = "id_hospital", referencedColumnName = "id")
    private Hospital hospital;

    @OneToMany
    @JoinColumn(name = "id_gestor_de_cola", referencedColumnName = "id")
    private List<ConsultaMedica> consultasEnEspera;

    public GestorDeCola (){
        this.consultasEnEspera = new ArrayList<>();
    }
}
