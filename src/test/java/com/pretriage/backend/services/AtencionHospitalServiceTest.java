package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AtencionHospitalServiceTest {

    @Mock
    private RepoConsultasMedicas repoConsultasMedicas;
    @Mock
    private RepoHospitales repoHospitales;
    @Mock
    private PacienteService pacienteService;
    @Mock
    private GooglePlacesService googlePlacesService;

    @InjectMocks
    private AtencionHospitalService service;

    @Test
    void sePuedeSeleccionarUnHospital(){
        String auth0Id = "auth0|123";
        String placeId = "place_1";

        Paciente paciente = new Paciente();
        paciente.setId(10L);

        ConsultaMedica consultaMedica = new ConsultaMedica();
        consultaMedica.setPaciente(paciente);
        consultaMedica.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaMedica.setFechaHoraCreacion(LocalDateTime.now().minusHours(1));

        Hospital hospital = new Hospital();
        hospital.setPlaceId(placeId);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(), EstadoConsulta.PENDIENTE))
                .thenReturn(Optional.of(consultaMedica));

        when(repoHospitales.findByPlaceId(placeId))
                .thenReturn(Optional.of(hospital));


        service.seleccionarHospital(auth0Id, placeId);


        ArgumentCaptor<ConsultaMedica> consultaCaptor = ArgumentCaptor.forClass(ConsultaMedica.class);
        verify(repoConsultasMedicas).save(consultaCaptor.capture());

        ConsultaMedica consultaGuardada = consultaCaptor.getValue();
        assertSame(consultaMedica, consultaGuardada);
        assertSame(hospital, consultaGuardada.getHospital());
        assertEquals(EstadoConsulta.HOSPITAL_SELECCIONADO, consultaGuardada.getEstadoConsulta());

        verify(googlePlacesService, never()).obtenerHospitalDesdeGoogle(anyString());
        verify(repoHospitales, never()).save(any());
    }


    @Test
    void noSePuedeSeleccionarUnHospitalQueNoExiste(){
        String auth0Id = "auth0|123";
        String placeId = "place_inexistente";

        Paciente paciente = new Paciente();
        paciente.setId(10L);

        ConsultaMedica consultaMedica = new ConsultaMedica();
        consultaMedica.setPaciente(paciente);
        consultaMedica.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaMedica.setFechaHoraCreacion(LocalDateTime.now().minusHours(1));

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(), EstadoConsulta.PENDIENTE))
                .thenReturn(Optional.of(consultaMedica));

        when(repoHospitales.findByPlaceId(placeId))
                .thenReturn(Optional.empty());

        // Asumimos que el servicio externo falla si no encuentra el hospital.
        when(googlePlacesService.obtenerHospitalDesdeGoogle(placeId))
                .thenThrow(new NoSuchElementException("Hospital inexistente"));

        assertThrows(NoSuchElementException.class,
                () -> service.seleccionarHospital(auth0Id, placeId));

        verify(googlePlacesService).obtenerHospitalDesdeGoogle(placeId);
        verify(repoHospitales, never()).save(any());
        verify(repoConsultasMedicas, never()).save(any());
    }

    @Test
    void noSePuedeSeleccionarUnHospitalSinotienePermisos(){
        String auth0Id = "auth0|sin-permiso";
        String placeId = "place_1";

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.seleccionarHospital(auth0Id, placeId));

        verifyNoInteractions(repoConsultasMedicas, repoHospitales, googlePlacesService);
    }
}
