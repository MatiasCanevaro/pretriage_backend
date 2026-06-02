package com.pretriage.backend.model.personas;

import java.util.List;

import com.pretriage.backend.model.hospitales.Coordenada;
import com.pretriage.backend.model.hospitales.Credencial;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Paciente {
    private String nombre;
    private String apellido;
    private String numeroDocumento;
    private TipoDocumento tipoDocumento;
    private String correoElectronico;
    private List<Credencial> credenciales;
    private Coordenada coordenadaActual;
}
