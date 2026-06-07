package com.pretriage.backend.model.personas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Recepcionista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellido;
    private String numeroDocumento;

    @Enumerated(EnumType.STRING)
    private TipoDocumento tipoDocumento;
    private String correoElectronico;
}
