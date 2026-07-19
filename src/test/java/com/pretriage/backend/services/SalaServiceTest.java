package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.RolSistema;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoSalas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SalaServiceTest {

    @Mock
    private RepoSalas repoSalas;
    @Mock
    private RepoHospitales repoHospitales;
    @Mock
    private RepoEspecialidadesMedicas repoEspecialidadesMedicas;
    @Mock
    private RecepcionistaService recepcionistaService;

    @InjectMocks
    private SalaService salaService;

    @Test
    void sePuedeCrearUnaSala(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        SalaDTO dtoRequest = new SalaDTO();
        dtoRequest.setNombre("Sala 0");
        Long idHospital = 1L;
        Long idEspecialidadMedica = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);
        Hospital hospitalMock = mock(Hospital.class);
        EspecialidadMedica especialidadMedicaMock = mock(EspecialidadMedica.class);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoHospitales.findById(idHospital)).thenReturn(Optional.of(hospitalMock));
        when(repoEspecialidadesMedicas.findById(idEspecialidadMedica))
                .thenReturn(Optional.of(especialidadMedicaMock));

        when(repoSalas.findByNombre(dtoRequest.getNombre()))
                .thenReturn(Optional.empty());

        salaService.crearSalaAdmin(auth0Id,dtoRequest, idHospital, idEspecialidadMedica);

        verify(repoSalas).save(any(Sala.class));//crea la sala
    }

    @Test
    void noSePuedeCrearUnaSalaSiNoEsUsuarioRecepcionistaAdmin(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        SalaDTO dtoRequest = new SalaDTO();
        dtoRequest.setNombre("Sala 0");
        Long idHospital = 1L;
        Long idEspecialidadMedica = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.USER);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        assertThrows(AccessDeniedException.class, ()->{
            salaService.crearSalaAdmin(auth0Id,dtoRequest, idHospital, idEspecialidadMedica);
        });
    }

    @Test
    void noSePuedeCrearUnaSalaSiElHospitalNoExiste(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        SalaDTO dtoRequest = new SalaDTO();
        dtoRequest.setNombre("Sala 0");
        Long idHospital = 1L;
        Long idEspecialidadMedica = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoHospitales.findById(idHospital)).thenReturn(Optional.empty());


        assertThrows(NoSuchElementException.class, ()->{
            salaService.crearSalaAdmin(auth0Id,dtoRequest, idHospital, idEspecialidadMedica);
        });
    }

    @Test
    void noSePuedeCrearUnaSalaSiLaEspecialidadNoExiste(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        SalaDTO dtoRequest = new SalaDTO();
        dtoRequest.setNombre("Sala 0");
        Long idHospital = 1L;
        Long idEspecialidadMedica = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);
        Hospital hospitalMock = mock(Hospital.class);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoHospitales.findById(idHospital)).thenReturn(Optional.of(hospitalMock));
        when(repoEspecialidadesMedicas.findById(idEspecialidadMedica))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, ()->{
            salaService.crearSalaAdmin(auth0Id,dtoRequest, idHospital, idEspecialidadMedica);
        });
    }

    @Test
    void noSePuedeCrearUnaSalaSiYaExisteUnaSalaConElMismoNombre(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        SalaDTO dtoRequest = new SalaDTO();
        dtoRequest.setNombre("Sala 0");
        Long idHospital = 1L;
        Long idEspecialidadMedica = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);
        Hospital hospitalMock = mock(Hospital.class);
        EspecialidadMedica especialidadMedicaMock = mock(EspecialidadMedica.class);
        Sala salaMock = mock(Sala.class);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoHospitales.findById(idHospital)).thenReturn(Optional.of(hospitalMock));
        when(repoEspecialidadesMedicas.findById(idEspecialidadMedica))
                .thenReturn(Optional.of(especialidadMedicaMock));

        when(repoSalas.findByNombre(dtoRequest.getNombre()))
                .thenReturn(Optional.of(salaMock));

        assertThrows(IllegalArgumentException.class, ()->{
            salaService.crearSalaAdmin(auth0Id,dtoRequest, idHospital, idEspecialidadMedica);
        });
    }

    @Test
    void sePuedeEliminarUnaSala(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        Long idSala = 1L;
        Sala salaMock = mock(Sala.class);

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoSalas.findByIdAndActivaTrue(idSala))
                .thenReturn(Optional.of(salaMock));

        salaService.eliminarSalaAdmin(auth0Id, idSala);

        verify(salaMock).setActiva(false);
        verify(repoSalas).save(any(Sala.class));//se elimina la sala con borrado lógico
    }

    @Test
    void noSePuedeEliminarUnaSalaSiNoEsUsuarioRecepcionistaAdmin(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        Long idSala = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.USER);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);


        assertThrows(AccessDeniedException.class, ()->{
            salaService.eliminarSalaAdmin(auth0Id, idSala);
        });

    }

    @Test
    void noSePuedeEliminarUnaSalaSiNoExiste(){
        String auth0Id = "auth0Id|RecepcionistaAdmin";
        Long idSala = 1L;

        UsuarioAuth usuarioAuthMock = new UsuarioAuth();
        usuarioAuthMock.setRol(RolSistema.ADMIN);

        when(recepcionistaService
                .obtenerUsuarioAuth(auth0Id)).thenReturn(usuarioAuthMock);

        when(repoSalas.findByIdAndActivaTrue(idSala))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, ()->{
            salaService.eliminarSalaAdmin(auth0Id, idSala);
        });

    }
}
