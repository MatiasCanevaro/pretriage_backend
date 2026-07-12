package com.pretriage.backend.controllers.dtos;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EstudioClinicoDTO {
    private Long id;
    private Long pacienteId;
    private String nombreArchivo;
    private String tipoArchivo;
    private String extensionArchivo;
    private String descripcion;
    private LocalDateTime fechaSubida;
    private Long tamanoArchivo;
    private String rutaArchivo;
}
