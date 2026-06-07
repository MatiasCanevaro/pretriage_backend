package com.pretriage.backend.controllers.dtos;

import com.pretriage.backend.model.personas.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Es obligatorio ingresar tu nombre")
    private String nombre;

    @NotBlank(message = "Es obligatorio ingresar tu apellido")
    private String apellido;


    @NotBlank(message = "Es obligatorio ingresar tu numero de documento")
    private String numeroDocumento;

    @NotNull(message = "Es obligatorio ingresar el tipo de documento")
    private TipoDocumento tipoDocumento;

    private TipoUsuario tipoUsuario;

    private String matricula;
}
