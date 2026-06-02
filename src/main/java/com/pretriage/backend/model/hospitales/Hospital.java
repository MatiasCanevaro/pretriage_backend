package com.pretriage.backend.model.hospitales;

import java.util.List;

import com.pretriage.backend.model.personas.Recepcionista;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Hospital {
    private String nombre;
    private List<Recepcionista> recepcionistas;
    private Direccion direccion;
}
