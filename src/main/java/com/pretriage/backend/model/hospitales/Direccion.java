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

    private String calle;
    private String altura;
    private String piso;
    private String codigoPostal;

    @OneToOne
    @JoinColumn(name="id_coordenada", referencedColumnName = "id")
    private Coordenada coordenada;
}
