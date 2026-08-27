package com.pretriage.backend.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void devuelveForbiddenParaFaltaDePermisos() {
        assertEquals(HttpStatus.FORBIDDEN,
                handler.handleAccessDenied(new AccessDeniedException("sin permiso")).getStatusCode());
    }

    @Test
    void devuelveNotFoundParaRecursoInexistente() {
        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleNotFound(new RecursoNoEncontradoException("inexistente")).getStatusCode());
    }

    @Test
    void devuelveConflictParaTransicionInvalida() {
        assertEquals(HttpStatus.CONFLICT,
                handler.handleStateConflict(new ConflictoDeEstadoException("conflicto")).getStatusCode());
    }

    @Test
    void devuelveUnauthorizedParaRefreshTokenInvalido() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                handler.handleRefreshTokenInvalido(new RefreshTokenInvalidoException()).getStatusCode());
    }

    @Test
    void devuelveBadRequestParaOtrosErroresAuth() {
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleExceptions(new NoSePudoCrearUsuario("error")).getStatusCode());
    }
}
