package com.pretriage.backend.controllers.dtos;

import com.pretriage.backend.model.personas.Genero;
import java.time.LocalDate;

public record PacienteRecepcionDTO(Long id, String dni, String nombre, String apellido,
                                   LocalDate fechaNacimiento, Genero generoBiologico,
                                   boolean registradoEnAplicacion) {}
