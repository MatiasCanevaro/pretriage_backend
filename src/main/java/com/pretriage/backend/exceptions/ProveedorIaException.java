package com.pretriage.backend.exceptions;

public class ProveedorIaException extends RuntimeException {
    public ProveedorIaException(Throwable cause) {
        super("No fue posible obtener una respuesta del asistente de triage", cause);
    }
}
