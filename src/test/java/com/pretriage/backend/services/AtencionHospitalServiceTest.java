package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import com.pretriage.backend.repositories.RepoHospitales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
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
    private RepoGestoresDeColas repoGestorDeCola;
    @Mock
    private PacienteService pacienteService;
    @Mock
    private GooglePlacesService googlePlacesService;
    @Mock
    private SalaService salaService;

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

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospital);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(), EstadoConsulta.PENDIENTE))
                .thenReturn(Optional.of(consultaMedica));

        when(repoHospitales.findByPlaceId(placeId))
                .thenReturn(Optional.of(hospital));

        when(repoGestorDeCola.findByHospitalId(hospital.getId())).thenReturn(Optional.of(gestorDeCola));

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


    @Test
    void sePuedeObtenerTiempoEstimadoDeAtencion() {

        String auth0Id = "auth0|123";

        Paciente paciente = new Paciente();
        paciente.setId(1L);

        Hospital hospital = new Hospital();
        hospital.setId(10L);

        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);

        GestorDeCola gestorDeCola = mock(GestorDeCola.class);

        LocalDateTime fechaEsperadaDesde =
                LocalDateTime.of(2026, 6, 20, 15, 30);

        LocalDateTime fechaEsperadaHasta =
                LocalDateTime.of(2026, 6, 20, 15, 40);

        AtencionMedica atencionMedica1 = new AtencionMedica();
        atencionMedica1.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica1.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(15));

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(30));

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica3.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(50));

        List<AtencionMedica> atencionesMedicas = List.of(atencionMedica1, atencionMedica2, atencionMedica3);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(),
                EstadoConsulta.HOSPITAL_SELECCIONADO))
                .thenReturn(Optional.of(consulta));

        when(repoGestorDeCola.findByHospitalId(hospital.getId()))
                .thenReturn(Optional.of(gestorDeCola));

        when(salaService.obtenerAtencionesMedicasActuales())
                .thenReturn(atencionesMedicas);

        when(gestorDeCola.calcularTiempoDeAtencionPara(consulta, atencionesMedicas))
                .thenReturn(List.of(fechaEsperadaDesde, fechaEsperadaHasta));

        TiempoEstimadoAtencionResponse response =
                service.obtenerTiempoEstimadoDeAtencion(auth0Id);

        assertEquals(
                fechaEsperadaDesde,
                response.getFechaHoraAtencionEstimadaDesde()
        );

        assertEquals(
                fechaEsperadaHasta,
                response.getFechaHoraAtencionEstimadaHasta()
        );
    }

    @Test
    void noSePuedeObtenerTiempoEstimadoSinPermisos() {

        String auth0Id = "auth0|123";

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id)
        );

        verifyNoInteractions(repoConsultasMedicas);
    }

    @Test
    void noSePuedeObtenerTiempoEstimadoSinHospitalSeleccionado() {

        String auth0Id = "auth0|123";

        Paciente paciente = new Paciente();
        paciente.setId(1L);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(),
                EstadoConsulta.HOSPITAL_SELECCIONADO))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id)
        );
    }

    @Test
    void lanzaExcepcionSiNoSePuedeEstimarElHorario() {

        String auth0Id = "auth0|123";

        Paciente paciente = new Paciente();
        paciente.setId(1L);

        Hospital hospital = new Hospital();
        hospital.setId(10L);

        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);

        GestorDeCola gestorDeCola = mock(GestorDeCola.class);

        AtencionMedica atencionMedica1 = new AtencionMedica();
        atencionMedica1.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica1.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(15));

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(30));

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(LocalDateTime.now());
        atencionMedica3.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(50));

        List<AtencionMedica> atencionesMedicas = List.of(atencionMedica1, atencionMedica2, atencionMedica3);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(),
                EstadoConsulta.HOSPITAL_SELECCIONADO))
                .thenReturn(Optional.of(consulta));

        when(repoGestorDeCola.findByHospitalId(hospital.getId()))
                .thenReturn(Optional.of(gestorDeCola));

        when(salaService.obtenerAtencionesMedicasActuales())
                .thenReturn(atencionesMedicas);

        when(gestorDeCola.calcularTiempoDeAtencionPara(consulta, atencionesMedicas))
                .thenReturn(List.of());

        assertThrows(
                NoSePudoEstimarElHorarioDeAtencion.class,
                () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id)
        );
    }

    @Test
    void sePuedeCambiarDeHospitalYActualizaLaColaDinamica() {
        String auth0Id = "auth0|123";
        String placeIdAnterior = "place_1";
        String placeIdNuevo = "place_2";

        Paciente paciente = new Paciente();
        paciente.setId(10L);

        Hospital hospitalAnterior = new Hospital();
        hospitalAnterior.setId(1L);
        hospitalAnterior.setPlaceId(placeIdAnterior);

        Hospital hospitalNuevo = new Hospital();
        hospitalNuevo.setId(2L);
        hospitalNuevo.setPlaceId(placeIdNuevo);

        ConsultaMedica consultaMedica = new ConsultaMedica();
        consultaMedica.setId(100L);
        consultaMedica.setPaciente(paciente);
        consultaMedica.setHospital(hospitalAnterior);
        consultaMedica.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);
        consultaMedica.setFechaHoraCreacion(LocalDateTime.now().minusHours(1));

        GestorDeCola gestorDeColaAnterior = new GestorDeCola();
        gestorDeColaAnterior.setHospital(hospitalAnterior);
        gestorDeColaAnterior.getConsultasEnEspera().add(consultaMedica);

        GestorDeCola gestorDeColaNuevo = new GestorDeCola();
        gestorDeColaNuevo.setHospital(hospitalNuevo);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(), EstadoConsulta.PENDIENTE))
                .thenReturn(Optional.empty());

        when(repoConsultasMedicas.findByPacienteIdAndEstadoConsultaEquals(
                paciente.getId(), EstadoConsulta.HOSPITAL_SELECCIONADO))
                .thenReturn(Optional.of(consultaMedica));

        when(repoHospitales.findByPlaceId(placeIdNuevo))
                .thenReturn(Optional.of(hospitalNuevo));

        when(repoGestorDeCola.findByHospitalId(hospitalAnterior.getId()))
                .thenReturn(Optional.of(gestorDeColaAnterior));

        when(repoGestorDeCola.findByHospitalId(hospitalNuevo.getId()))
                .thenReturn(Optional.of(gestorDeColaNuevo));

        service.seleccionarHospital(auth0Id, placeIdNuevo);

        ArgumentCaptor<ConsultaMedica> consultaCaptor = ArgumentCaptor.forClass(ConsultaMedica.class);
        verify(repoConsultasMedicas).save(consultaCaptor.capture());

        ConsultaMedica consultaGuardada = consultaCaptor.getValue();
        assertSame(hospitalNuevo, consultaGuardada.getHospital());
        assertEquals(EstadoConsulta.HOSPITAL_SELECCIONADO, consultaGuardada.getEstadoConsulta());

        assertFalse(gestorDeColaAnterior.getConsultasEnEspera().contains(consultaMedica));
        assertTrue(gestorDeColaNuevo.getConsultasEnEspera().contains(consultaMedica));

        ArgumentCaptor<GestorDeCola> gestorCaptor = ArgumentCaptor.forClass(GestorDeCola.class);
        verify(repoGestorDeCola, times(2)).save(gestorCaptor.capture());

        List<GestorDeCola> gestoresGuardados = gestorCaptor.getAllValues();
        assertTrue(gestoresGuardados.contains(gestorDeColaAnterior));
        assertTrue(gestoresGuardados.contains(gestorDeColaNuevo));
    }

}
