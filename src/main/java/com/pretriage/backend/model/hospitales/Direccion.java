package com.pretriage.backend.model.hospitales;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter
@Entity
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String calle;
    @Column(columnDefinition = "TEXT")
    private String altura;
    @Column(columnDefinition = "TEXT")
    private String piso;
    @Column(columnDefinition = "TEXT")
    private String codigoPostal;

    @OneToOne
    @JoinColumn(name="id_coordenada", referencedColumnName = "id")
    private Coordenada coordenada;
}
