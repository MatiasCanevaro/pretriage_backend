package com.pretriage.backend.controllers.dtos.googleMaps;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransitLineDTO {

    private String name; // nombre completo de la linea del transporte público

    private String nameShort; // nombre corto de la linea del transporte público
}
