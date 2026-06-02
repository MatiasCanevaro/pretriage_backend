package com.pretriage.backend.model.hospitales;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter
public class Direccion {
    private String calle;
    private String altura;
    private String piso;
    private String codigoPostal;
    private Coordenada coordenada;
}
