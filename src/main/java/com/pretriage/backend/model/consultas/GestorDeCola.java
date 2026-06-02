package com.pretriage.backend.model.consultas;

import java.util.List;

import com.pretriage.backend.model.hospitales.Hospital;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GestorDeCola {
    private Hospital hospital;
    private List<ConsultaMedica> consultasEnEspera;
}
