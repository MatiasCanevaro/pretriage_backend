package com.pretriage.backend.model.hospitales;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Credencial {
    private String numeroAfiliado;
    private String plan;
    private LocalDate fechaVencimiento;
    private ObraSocial obraSocial;
}
