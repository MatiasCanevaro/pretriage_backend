package com.pretriage.backend.exceptions;

public class NoEixstenSalasActivasException extends RuntimeException {
    public NoEixstenSalasActivasException() {
        super("No se encontraron salas activas");
    }
}
