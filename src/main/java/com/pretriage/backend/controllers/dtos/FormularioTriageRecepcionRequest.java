package com.pretriage.backend.controllers.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FormularioTriageRecepcionRequest(
        @NotBlank String motivoConsulta,
        @NotNull @Size(min = 1) List<String> sintomas,
        @NotBlank String inicio,
        @NotBlank String evolucion,
        @Min(0) @Max(10) Integer intensidadDolor,
        String localizacionDolor,
        Boolean fiebre,
        List<String> signosAlarma,
        List<String> antecedentesRelevantes,
        List<String> medicamentos,
        List<String> alergias,
        String posibilidadEmbarazo,
        String observaciones) {}
