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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void pacientePuedeEliminarSuCredencial() {

        UsuarioAuth usuario = new UsuarioAuth();
        String authoIdUser = "auth0|paciente";
        usuario.setId(authoIdUser);

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(authoIdUser))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        service.eliminarCredencial(1L, authoIdUser);

        verify(repoCredenciales).deleteById(1L);
    }

    @Test
    void pacienteNoPuedeEliminarCredencialDeOtroPaciente() {

        UsuarioAuth usuario = new UsuarioAuth();
        String authoIdUser1 = "auth0|paciente1";
        String authoIdUser2 = "auth0|paciente2";
        usuario.setId(authoIdUser1);

        Paciente paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setUsuarioAuth(usuario);

        UsuarioAuth usuario2 = new UsuarioAuth();
        usuario2.setId(authoIdUser2);

        Paciente paciente2 = new Paciente();
        paciente2.setId(2L);
        paciente2.setUsuarioAuth(usuario2);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente2);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(authoIdUser1))
                .thenReturn(Optional.of(paciente1));

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        assertThrows(
                AccessDeniedException.class,
                () -> service.eliminarCredencial(1L, authoIdUser1),
                "Debería lanzar AccessDeniedException al intentar eliminar credencial de otro paciente"
        );
    }

    @Test
    void recepcionistaPuedeEliminarCredencialDePaciente() {
        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth pacienteUser = new UsuarioAuth();
        pacienteUser.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(pacienteUser);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente);

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(paciente);

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        service.eliminarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id);

        verify(repoCredenciales).deleteById(1L);
    }

    @Test
    void recepcionistaNoPuedeEliminarCredencialDeOtroPaciente() {
        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth paciente1 = new UsuarioAuth();
        paciente1.setId("auth0|paciente1");

        Paciente paciente1Entity = new Paciente();
        paciente1Entity.setId(1L);
        paciente1Entity.setUsuarioAuth(paciente1);

        UsuarioAuth paciente2 = new UsuarioAuth();
        paciente2.setId("auth0|paciente2");

        Paciente paciente2Entity = new Paciente();
        paciente2Entity.setId(2L);
        paciente2Entity.setUsuarioAuth(paciente2);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente2Entity);

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(paciente1Entity);

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        assertThrows(
               AccessDeniedException.class,
                () -> service.eliminarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id),
                "Debería lanzar AccessDeniedException al intentar eliminar credencial de paciente incorrecto"
        );
    }

    @Test
    void noEsRecepcionistaNoPuedeEliminarCredencial() {
        String recepcionistaAuth0Id = "auth0|recepcionista";

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> service.eliminarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id),
                "Debería lanzar AccessDeniedException al intentar eliminar credencial sin ser recepcionista"
        );
    }

    @Test
    void eliminarCredencialCredencialNoEncontrada() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.eliminarCredencial(999L, "auth0|paciente"),
                "Debería lanzar AccessDeniedException al intentar eliminar credencial que no existe"
        );
    }

    @Test
    void eliminarCredencialRecepcionistaCredencialNoEncontrada() {

        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth paciente = new UsuarioAuth();
        paciente.setId("auth0|paciente");

        Paciente pacienteEntity = new Paciente();
        pacienteEntity.setId(1L);
        pacienteEntity.setUsuarioAuth(paciente);

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(pacienteEntity);

        when(repoCredenciales.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.eliminarCredencialRecepcionista(999L, 1L, recepcionistaAuth0Id),
                "Debería lanzar AccessDeniedException al intentar eliminar credencial inexistente como recepcionista"
        );
    }

    @Test
    void pacientePuedeEditarSuCredencial() {

        UsuarioAuth usuario = new UsuarioAuth();
        String auth0IdPaciente = "auth0|paciente";
        usuario.setId(auth0IdPaciente);

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE NUEVO");
        request.setNumeroAfiliado("654321");
        request.setPlan("321");
        request.setFechaVencimiento(LocalDate.now().plusYears(2));

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0IdPaciente))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        when(repoObraSociales.findByNombreEqualsIgnoreCase("OSDE NUEVO"))
                .thenReturn(Optional.empty());

        service.editarCredencialPaciente(1L, auth0IdPaciente, request);

        assertEquals("OSDE NUEVO", credencial.getObraSocial().getNombre());
        assertEquals("654321", credencial.getNumeroAfiliado());
        assertEquals("321", credencial.getPlan());
        assertEquals(LocalDate.now().plusYears(2), credencial.getFechaVencimiento());
        verify(repoCredenciales).save(credencial);
    }

    @Test
    void pacienteNoPuedeEditarCredencialDeOtroPaciente() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente1");

        Paciente paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setUsuarioAuth(usuario);

        UsuarioAuth usuario2 = new UsuarioAuth();
        usuario2.setId("auth0|paciente2");

        Paciente paciente2 = new Paciente();
        paciente2.setId(2L);
        paciente2.setUsuarioAuth(usuario2);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente2);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE NUEVO");

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente1"))
                .thenReturn(Optional.of(paciente1));

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        assertThrows(
                AccessDeniedException.class,
                () -> service.editarCredencialPaciente(1L, "auth0|paciente1", request),
                "Debería lanzar AccessDeniedException al intentar editar credencial de otro paciente"
        );
    }

    @Test
    void recepcionistaPuedeEditarCredencialDePaciente() {

        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth pacienteUser = new UsuarioAuth();
        pacienteUser.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(pacienteUser);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE EDITADO");
        request.setNumeroAfiliado("999888");
        request.setPlan("777");
        request.setFechaVencimiento(LocalDate.now().plusYears(1));

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(paciente);

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        when(repoObraSociales.findByNombreEqualsIgnoreCase("OSDE EDITADO"))
                .thenReturn(Optional.empty());

        service.editarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id, request);

        assertEquals("OSDE EDITADO", credencial.getObraSocial().getNombre());
        assertEquals("999888", credencial.getNumeroAfiliado());
        assertEquals("777", credencial.getPlan());
        verify(repoCredenciales).save(credencial);
    }

    @Test
    void recepcionistaNoPuedeEditarCredencialDeOtroPaciente() {

        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth paciente1 = new UsuarioAuth();
        paciente1.setId("auth0|paciente1");

        Paciente paciente1Entity = new Paciente();
        paciente1Entity.setId(1L);
        paciente1Entity.setUsuarioAuth(paciente1);

        UsuarioAuth paciente2 = new UsuarioAuth();
        paciente2.setId("auth0|paciente2");

        Paciente paciente2Entity = new Paciente();
        paciente2Entity.setId(2L);
        paciente2Entity.setUsuarioAuth(paciente2);

        Credencial credencial = new Credencial();
        credencial.setId(1L);
        credencial.setPaciente(paciente2Entity);

        CredencialRequest request = new CredencialRequest();
        request.setNombreObraSocial("OSDE NUEVO");

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(paciente1Entity);

        when(repoCredenciales.findById(1L))
                .thenReturn(Optional.of(credencial));

        assertThrows(
                AccessDeniedException.class,
                () -> service.editarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id, request),
                "Debería lanzar AccessDeniedException al intentar editar credencial de paciente incorrecto"
        );
    }

    @Test
    void noEsRecepcionistaNoPuedeEditarCredencial() {

        String recepcionistaAuth0Id = "auth0|recepcionista";

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> service.editarCredencialRecepcionista(1L, 1L, recepcionistaAuth0Id, new CredencialRequest()),
                "Debería lanzar AccessDeniedException al intentar editar credencial sin ser recepcionista"
        );
    }

    @Test
    void editarCredencialCredencialNoEncontrada() {

        UsuarioAuth usuario = new UsuarioAuth();
        usuario.setId("auth0|paciente");

        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuarioAuth(usuario);

        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth0|paciente"))
                .thenReturn(Optional.of(paciente));

        when(repoCredenciales.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.editarCredencialPaciente(999L, "auth0|paciente", new CredencialRequest()),
                "Debería lanzar AccessDeniedException al intentar editar credencial que no existe"
        );
    }

    @Test
    void editarCredencialRecepcionistaCredencialNoEncontrada() {

        String recepcionistaAuth0Id = "auth0|recepcionista";

        UsuarioAuth paciente = new UsuarioAuth();
        paciente.setId("auth0|paciente");

        Paciente pacienteEntity = new Paciente();
        pacienteEntity.setId(1L);
        pacienteEntity.setUsuarioAuth(paciente);

        when(recepcionistaService.esRecepcionistaConUsuarioId(recepcionistaAuth0Id))
                .thenReturn(true);

        when(pacienteService.obtenerPaciente(1L))
                .thenReturn(pacienteEntity);

        when(repoCredenciales.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.editarCredencialRecepcionista(999L, 1L, recepcionistaAuth0Id, new CredencialRequest()),
                "Debería lanzar AccessDeniedException al intentar editar credencial inexistente como recepcionista"
        );
    }

}