package com.pretriage.backend.exceptions;

public class HospitalNoEncontradoException extends RuntimeException{

    public HospitalNoEncontradoException(Long idHospital) {
        super("Hospital no encontrado con id "+ idHospital);
    }
}
