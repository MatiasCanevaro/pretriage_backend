package com.pretriage.backend.model.hospitales;

import java.util.ArrayList;
import java.util.List;

import com.pretriage.backend.model.personas.Recepcionista;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String placeId; //id en api de google maps

    @Column(columnDefinition = "TEXT")
    private String nombre;

    @OneToMany
    @JoinColumn(name = "id_hospital", referencedColumnName = "id")
    private List<Recepcionista> recepcionistas;

    @OneToOne
    @JoinColumn(name="id_direccion", referencedColumnName = "id")
    private Direccion direccion;

    public Hospital(){
        this.recepcionistas = new ArrayList<>();
    }
}
