package com.pretriage.backend.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ CredencialValidaYaExisteException.class, CredencialInvalidaException.class,
            ObraSocialSinValidadorException.class, PacienteNoExisteException.class,
            NoSePudoCrearUsuario.class, NoSePudoEstimarElHorarioDeAtencion.class, ChatFinalizadoException.class,
            NoSePudoObtenerHospital.class, ObraSocialYaExisteException.class, ObraSocialNoExisteException.class,
            RecepcionistaNoExisteException.class,
            NoSuchElementException.class, IllegalStateException.class, IllegalArgumentException.class,
            TokenCambioContraseniaInvalidoException.class, NoSePudoCambiarContraseniaException.class })
    public ResponseEntity<Map<String, String>> handleExceptions(
            RuntimeException e) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({ ConflictoDeEstadoException.class, AtencionEnCursoException.class })
    public ResponseEntity<Map<String, String>> handleStateConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({ ProveedorIaException.class, ArchivoS3Exception.class })
    public ResponseEntity<Map<String, String>> handleProviderException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RefreshTokenInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleRefreshTokenInvalido(RefreshTokenInvalidoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LimiteSolicitudesCambioContraseniaException.class)
    public ResponseEntity<Map<String, String>> handleLimiteSolicitudes(LimiteSolicitudesCambioContraseniaException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {

        Map<String, String> errores = new HashMap<>();

        e.getBindingResult().getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errores);
    }

}
