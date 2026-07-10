package com.pretriage.backend.exceptions;

public class MedicoNoEncontradoException extends RuntimeException{

    public MedicoNoEncontradoException(String auth0IdMedico) {
        super("Medico no encontrado con auth0Id: " + auth0IdMedico);
    }
}
