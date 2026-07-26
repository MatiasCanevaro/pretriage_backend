package com.pretriage.backend.controllers.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HospitalCercanoDTO {

    private Long idHospital;

    private String placeId;

    private String nombre;

    private String direccion;

    private List<EspecialidadMedicaDTO> especialidades;

}
