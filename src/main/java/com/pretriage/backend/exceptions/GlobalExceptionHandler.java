package com.pretriage.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CredencialValidaYaExisteException.class, PacienteNoExisteException.class,
            NoSePudoCrearUsuario.class, NoSePudoEstimarElHorarioDeAtencion.class, ChatFinalizadoException.class,
            NoSePudoObtenerHospital.class, ObraSocialYaExisteException.class, ObraSocialNoExisteException.class,
            RecepcionistaNoExisteException.class,
            NoSuchElementException.class, IllegalStateException.class, IllegalArgumentException.class})
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

    @ExceptionHandler(ConflictoDeEstadoException.class)
    public ResponseEntity<Map<String, String>> handleStateConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(AtencionEnCursoException.class)
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({ProveedorIaException.class, ArchivoS3Exception.class})
    public ResponseEntity<Map<String, String>> handleProviderException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {

        Map<String, String> errores = new HashMap<>();

        e.getBindingResult().getFieldErrors()
                .forEach(error ->
                        errores.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errores);
    }

}
