package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.controllers.dtos.CredencialResponse;
import com.pretriage.backend.model.hospitales.Credencial;
import com.pretriage.backend.model.hospitales.ObraSocial;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoCredenciales;
import com.pretriage.backend.repositories.RepoObraSociales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        paciente.setId(1L);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE");
        request.setNumeroAfiliado("123456");
        request.setPlan("210");
        request.setFechaVencimiento(LocalDate.now().plusYears(1));

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoObraSociales.findByNombreEqualsIgnoreCase("OSDE"))
                .thenReturn(Optional.empty());

        service.cargarCredencialPaciente(
                "auth0|paciente",
                request);

        verify(repoCredenciales).save(any(Credencial.class));
    }

    @Test
    void permiteCargarCredencialAunqueYaTengaCredencialVigente() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        Credencial credencialVigente = new Credencial();
        credencialVigente.setPaciente(paciente);
        credencialVigente.setFechaVencimiento(LocalDate.now().plusMonths(1));
        paciente.getCredenciales().add(credencialVigente);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE");
        request.setNumeroAfiliado("123456");
        request.setPlan("210");
        request.setFechaVencimiento(LocalDate.now().plusYears(1));

        ObraSocial obraSocial = new ObraSocial();
        obraSocial.setNombre("OSDE");

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoObraSociales.findByNombreEqualsIgnoreCase("OSDE"))
                .thenReturn(Optional.of(obraSocial));

        service.cargarCredencialPaciente(
                "auth0|paciente",
                request);

        verify(repoCredenciales).save(any(Credencial.class));
    }

    @Test
    void pacientePuedeObtenerSusCredenciales() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        ObraSocial obraSocial = new ObraSocial();
        obraSocial.setId(2L);
        obraSocial.setNombre("OSDE");

        LocalDate fechaVencimiento = LocalDate.now().plusYears(1);

        Credencial credencial = new Credencial();
        credencial.setObraSocial(obraSocial);
        credencial.setNumeroAfiliado("123456");
        credencial.setPlan("210");
        credencial.setFechaVencimiento(fechaVencimiento);
        credencial.setPaciente(paciente);

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.findByPacienteId(1L))
                .thenReturn(List.of(credencial));

        List<CredencialResponse> credenciales = service.obtenerCredencialesPaciente("auth0|paciente");

        assertEquals(1, credenciales.size());
        assertEquals("123456", credenciales.getFirst().getNumeroAfiliado());
        assertEquals("210", credenciales.getFirst().getPlan());
        assertEquals(fechaVencimiento, credenciales.getFirst().getFechaVencimiento());
    }

}