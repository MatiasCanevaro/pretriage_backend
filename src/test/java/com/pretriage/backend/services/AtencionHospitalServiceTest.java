package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.AtencionEnCursoException;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
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

        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
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
        verifyNoInteractions(repoGestorDeCola);
    }
    @Test
    void seleccionarHospitalLuegoFinalizarTriageAgregaPacienteALaColaYDevuelveTiempoEstimado() {
        String auth0Id = "auth0|123";
        String placeId = "place_1";

        Paciente paciente = new Paciente();
        paciente.setId(10L);

        Hospital hospital = new Hospital();
        hospital.setId(20L);
        hospital.setPlaceId(placeId);

        ConsultaMedica consultaPaciente = new ConsultaMedica();
        consultaPaciente.setPaciente(paciente);
        consultaPaciente.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaPaciente.setFechaHoraCreacion(LocalDateTime.of(2026, 6, 29, 10, 5));

        ConsultaMedica consultaCriticaPrevia = new ConsultaMedica();
        consultaCriticaPrevia.setHospital(hospital);
        consultaCriticaPrevia.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaCriticaPrevia.setFechaHoraCreacion(LocalDateTime.of(2026, 6, 29, 10, 0));
        consultaCriticaPrevia.setNivelDeGravedadBot(NivelDeGravedad.RIESGO_VITAL_INMEDIATO);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospital);
        ReflectionTestUtils.setField(gestorDeCola, "TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE", 600L);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaCriticaPrevia);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaPaciente));
        when(repoHospitales.findByPlaceId(placeId))
                .thenReturn(Optional.of(hospital));
        when(repoGestorDeCola.findByHospitalId(hospital.getId()))
                .thenReturn(Optional.of(gestorDeCola));

        service.seleccionarHospital(auth0Id, placeId);

        LocalDateTime antesDeFinalizarTriage = LocalDateTime.now();
        TiempoEstimadoAtencionResponse response =
                service.finalizarTriageEIngresarACola(auth0Id, NivelDeGravedad.URGENTE);
        LocalDateTime despuesDeFinalizarTriage = LocalDateTime.now();

        assertSame(hospital, consultaPaciente.getHospital());
        assertEquals(EstadoConsulta.PRETRIAGE_FINALIZADO, consultaPaciente.getEstadoConsulta());
        assertEquals(NivelDeGravedad.URGENTE, consultaPaciente.getNivelDeGravedadBot());
        assertEquals(List.of(consultaCriticaPrevia, consultaPaciente), gestorDeCola.getConsultasEnEspera());
        assertFalse(response.getFechaHoraAtencionEstimada().isBefore(antesDeFinalizarTriage.plusSeconds(600)));
        assertFalse(response.getFechaHoraAtencionEstimada().isAfter(despuesDeFinalizarTriage.plusSeconds(600)));
        verify(repoConsultasMedicas, atLeastOnce()).save(consultaPaciente);
        verify(repoGestorDeCola, atLeastOnce()).save(gestorDeCola);
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

        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
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
        verifyNoInteractions(repoGestorDeCola);
        verify(repoConsultasMedicas, never()).save(any());
    }
    @Test
    void noSePuedeSeleccionarHospitalSiYaTieneUnaAtencionEnCurso() {
        String auth0Id = "auth0|123";
        String placeId = "place_1";

        Paciente paciente = new Paciente();
        paciente.setId(10L);

        ConsultaMedica consultaEnCurso = new ConsultaMedica();
        consultaEnCurso.setPaciente(paciente);
        consultaEnCurso.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaEnCurso));

        assertThrows(AtencionEnCursoException.class,
                () -> service.seleccionarHospital(auth0Id, placeId));

        verifyNoInteractions(repoHospitales, googlePlacesService, repoGestorDeCola);
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

        LocalDateTime fechaEsperada =
                LocalDateTime.of(2026, 6, 20, 15, 30);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));

        when(repoGestorDeCola.findByHospitalId(hospital.getId()))
                .thenReturn(Optional.of(gestorDeCola));

        when(gestorDeCola.calcularTiempoDeAtencionPara(consulta))
                .thenReturn(Optional.of(fechaEsperada));

        TiempoEstimadoAtencionResponse response =
                service.obtenerTiempoEstimadoDeAtencion(auth0Id);

        assertEquals(
                fechaEsperada,
                response.getFechaHoraAtencionEstimada()
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

        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
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

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id))
                .thenReturn(Optional.of(paciente));

        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));

        when(repoGestorDeCola.findByHospitalId(hospital.getId()))
                .thenReturn(Optional.of(gestorDeCola));

        when(gestorDeCola.calcularTiempoDeAtencionPara(consulta))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSePudoEstimarElHorarioDeAtencion.class,
                () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id)
        );
    }

    private boolean contieneEstadosParaConsultarTiempo(Collection<EstadoConsulta> estados) {
        return estados.contains(EstadoConsulta.HOSPITAL_SELECCIONADO)
                && estados.contains(EstadoConsulta.PRETRIAGE_EN_PROCESO)
                && estados.contains(EstadoConsulta.PRETRIAGE_FINALIZADO);
    }
}




