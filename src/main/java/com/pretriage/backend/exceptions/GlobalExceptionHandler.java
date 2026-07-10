package com.pretriage.backend.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CredencialValidaYaExisteException.class, PacienteNoExisteException.class,

            NoSePudoCrearUsuario.class, NoSePudoEstimarElHorarioDeAtencion.class, NoSePudoObtenerHospital.class,
            AccessDeniedException.class, NoEixstenSalasActivasException.class,
            ObraSocialYaExisteException.class, ObraSocialNoExisteException.class,
            RecepcionistaNoExisteException.class, MedicoNoEncontradoException.class,
            HospitalNoEncontradoException.class})

    public ResponseEntity<Map<String, String>> handleExceptions(
            RuntimeException e) {

        return ResponseEntity.badRequest()
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
