package com.pretriage.backend.model.personas;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Recepcionista {
    private String nombre;
    private String apellido;
    private String numeroDocumento;
    private TipoDocumento tipoDocumento;
    private String correoElectronico;
}
