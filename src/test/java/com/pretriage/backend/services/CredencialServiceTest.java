package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.exceptions.CredencialValidaYaExisteException;
import com.pretriage.backend.model.hospitales.Credencial;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoCredenciales;
import com.pretriage.backend.repositories.RepoObraSociales;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.repositories.RepoRecepcionistas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredencialServiceTest {

    @Mock
    private PacienteService pacienteService;

    @Mock
    private RecepcionistaService recepcionistaService;

    @Mock
    private RepoObraSociales repoObraSociales;

    @Mock
    private RepoCredenciales repoCredenciales;

    @InjectMocks
    private CredencialService service;

    @Test
    void pacientePuedeCargarCredencial() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setUsuarioAuth(usuario);
        paciente.setId(1L); // el paciente debe estar guardado ya al registrarse, por lo tanto ya tiene id asignado

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE");
        request.setNumeroAfiliado("123456");
        request.setPlan("210");
        request.setFechaVencimiento(LocalDate.now().plusYears(1));

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.existsByPacienteIdAndFechaVencimientoGreaterThanEqual(
                anyLong(),
                any()))
                .thenReturn(false);

        when(repoObraSociales.findByNombreEqualsIgnoreCase("OSDE"))
                .thenReturn(Optional.empty());

        service.cargarCredencialPaciente(
                "auth0|paciente",
                request);

        verify(repoCredenciales).save(any(Credencial.class));
    }

    @Test
    void debeLanzarExcepcionSiYaTieneCredencialVigente() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        ReflectionTestUtils.setField(paciente,"id",1L);
        paciente.setUsuarioAuth(usuario);

        CredencialRequest request = new CredencialRequest();

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales
                .existsByPacienteIdAndFechaVencimientoGreaterThanEqual(
                        1L,
                        LocalDate.now()))
                .thenReturn(true);

        assertThrows(
                CredencialValidaYaExisteException.class,
                () -> service.cargarCredencialPaciente(
                        "auth0|paciente",
                        request)
        );
    }

}