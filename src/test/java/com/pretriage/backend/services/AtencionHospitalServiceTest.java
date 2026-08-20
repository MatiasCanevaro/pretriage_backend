package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.HospitalSeleccionadoResponse;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.exceptions.AtencionEnCursoException;
import com.pretriage.backend.exceptions.NoSePudoEstimarElHorarioDeAtencion;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
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
import java.time.LocalTime;
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
    private RepoEntradasCola repoEntradasCola;
    @Mock
    private RepoEspecialidadesMedicas repoEspecialidadesMedicas;
    @Mock
    private EstimacionAtencionService estimacionAtencionService;
    @Mock
    private PacienteService pacienteService;
    @Mock
    private IngresoColaService ingresoColaService;
    @Mock
    private GooglePlacesService googlePlacesService;

    @InjectMocks
    private AtencionHospitalService service;

    @Test
    void sePuedeSeleccionarUnHospitalConEspecialidad(){
        String auth0Id = "auth0|123";
        String placeId = "place_1";
        String codigoEspecialidad = "PEDIATRIA";

        Paciente paciente = crearPaciente(10L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospital = crearHospital(20L, placeId, especialidad);
        ConsultaMedica consultaMedica = crearConsultaPendiente(paciente);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaMedica));
        when(repoHospitales.findByPlaceId(placeId)).thenReturn(Optional.of(hospital));
        when(ingresoColaService.ingresar(consultaMedica, NivelDeGravedad.NORMAL)).thenAnswer(inv -> {
            consultaMedica.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
            consultaMedica.setEstadoConsulta(EstadoConsulta.EN_COLA);
            return new TiempoEstimadoAtencionResponse();
        });

        service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad);

        assertSame(hospital, consultaMedica.getHospital());
        assertSame(especialidad, consultaMedica.getEspecialidad());
        assertEquals(EstadoConsulta.EN_COLA, consultaMedica.getEstadoConsulta());
        verify(ingresoColaService).ingresar(consultaMedica, NivelDeGravedad.NORMAL);
    }

    @Test
    void noSePuedeSeleccionarHospitalSiNoAtiendeLaEspecialidad(){
        String auth0Id = "auth0|123";
        String placeId = "place_1";
        String codigoEspecialidad = "PEDIATRIA";

        Paciente paciente = crearPaciente(10L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospital = crearHospital(20L, placeId, crearEspecialidad(31L, "CARDIOLOGIA"));
        ConsultaMedica consultaMedica = crearConsultaPendiente(paciente);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaMedica));
        when(repoHospitales.findByPlaceId(placeId)).thenReturn(Optional.of(hospital));

        assertThrows(NoSuchElementException.class,
                () -> service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad));

        verify(repoConsultasMedicas, never()).save(any());
    }

    @Test
    void seleccionarHospitalLuegoFinalizarTriageAgregaPacienteALaColaDeLaEspecialidadYDevuelveTiempoEstimado() {
        String auth0Id = "auth0|123";
        String placeId = "place_1";
        String codigoEspecialidad = "PEDIATRIA";

        Paciente paciente = crearPaciente(10L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospital = crearHospital(20L, placeId, especialidad);
        ConsultaMedica consultaPaciente = crearConsultaPendiente(paciente);
        consultaPaciente.setFechaHoraCreacion(LocalDateTime.of(2026, 6, 29, 10, 5));

        ConsultaMedica consultaCriticaPrevia = new ConsultaMedica();
        consultaCriticaPrevia.setHospital(hospital);
        consultaCriticaPrevia.setEspecialidad(especialidad);
        consultaCriticaPrevia.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaCriticaPrevia.setFechaHoraCreacion(LocalDateTime.of(2026, 6, 29, 10, 0));
        consultaCriticaPrevia.setNivelDeGravedadBot(NivelDeGravedad.RIESGO_VITAL_INMEDIATO);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setId(40L);
        gestorDeCola.setHospital(hospital);
        gestorDeCola.setEspecialidad(especialidad);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaCriticaPrevia);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaPaciente));
        when(repoHospitales.findByPlaceId(placeId)).thenReturn(Optional.of(hospital));

        TiempoEstimadoAtencionResponse responseEsperada = new TiempoEstimadoAtencionResponse();
        responseEsperada.setPosicionEnCola(2);
        responseEsperada.setPacientesAntes(1);
        when(ingresoColaService.ingresar(consultaPaciente, NivelDeGravedad.NORMAL)).thenAnswer(inv -> {
            consultaPaciente.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
            consultaPaciente.setEstadoConsulta(EstadoConsulta.EN_COLA);
            return new TiempoEstimadoAtencionResponse();
        });
        when(ingresoColaService.ingresar(consultaPaciente, NivelDeGravedad.URGENTE)).thenAnswer(inv -> {
            consultaPaciente.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
            consultaPaciente.setEstadoConsulta(EstadoConsulta.EN_COLA);
            return responseEsperada;
        });

        service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad);

        TiempoEstimadoAtencionResponse response =
                service.finalizarTriageEIngresarACola(auth0Id, NivelDeGravedad.URGENTE);

        assertSame(hospital, consultaPaciente.getHospital());
        assertSame(especialidad, consultaPaciente.getEspecialidad());
        assertEquals(EstadoConsulta.EN_COLA, consultaPaciente.getEstadoConsulta());
        assertEquals(NivelDeGravedad.URGENTE, consultaPaciente.getNivelDeGravedadBot());
        assertSame(responseEsperada, response);
        verify(ingresoColaService).ingresar(consultaPaciente, NivelDeGravedad.NORMAL);
        verify(ingresoColaService).ingresar(consultaPaciente, NivelDeGravedad.URGENTE);
    }

    @Test
    void hospitalesCercanosSeFiltranPorEspecialidadManteniendoElOrdenDeDistancia(){
        String auth0idPaciente = "auth0|Paciente";
        String codigoEspecialidad = "PEDIATRIA";
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospitalDisponible = crearHospital(20L, "hospital2", especialidad);

        HospitalCercanoDTO hospital1 = crearHospitalCercano("hospital1");
        HospitalCercanoDTO hospital2 = crearHospitalCercano("hospital2");

        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(googlePlacesService.buscarHospitales(-34.6, -58.4)).thenReturn(List.of(hospital1, hospital2));
        when(repoHospitales.findByPlaceIdInAndEspecialidadesCodigo(List.of("hospital1", "hospital2"), codigoEspecialidad))
                .thenReturn(List.of(hospitalDisponible));

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0idPaciente))
                .thenReturn(Optional.of(new Paciente()));

        List<HospitalCercanoDTO> hospitales = service.buscarHospitalesCercanos(-34.6, -58.4, codigoEspecialidad,
                "caminar", auth0idPaciente);

        assertEquals(1, hospitales.size());
        assertEquals("hospital2", hospitales.getFirst().getPlaceId());
        assertEquals(codigoEspecialidad, hospitales.getFirst().getEspecialidades().getFirst().getCodigo());
    }

    @Test
    void seCompletaElTiempoEstimadoArriboMejorRutaEnHospitalesCercanos(){
        String auth0idPaciente = "auth0|Paciente";
        String codigoEspecialidad = "PEDIATRIA";
        String transporte = "vehiculo";
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospitalDisponible = crearHospital(20L, "hospital2", especialidad);

        HospitalCercanoDTO hospital2 = crearHospitalCercano("hospital2");

        LocalTime tiempoEsperado = LocalTime.of(0, 25, 0);

        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(googlePlacesService.buscarHospitales(-34.6, -58.4)).thenReturn(List.of(hospital2));
        when(repoHospitales.findByPlaceIdInAndEspecialidadesCodigo(List.of("hospital2"), codigoEspecialidad))
                .thenReturn(List.of(hospitalDisponible));
        when(googlePlacesService.calcularTiempoEstimadoArriboMejorRuta(hospitalDisponible, transporte, -34.6, -58.4))
                .thenReturn(tiempoEsperado);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0idPaciente))
                .thenReturn(Optional.of(new Paciente()));

        List<HospitalCercanoDTO> hospitales = service.buscarHospitalesCercanos(-34.6, -58.4, codigoEspecialidad,
                transporte, auth0idPaciente);

        assertEquals(1, hospitales.size());
        assertEquals(tiempoEsperado, hospitales.getFirst().getTiempoEstimadoArriboMejorRuta());
    }

    @Test
    void siNoSeEspecificaTransporteSeUsaElPredeterminado(){
        String auth0idPaciente = "auth0|Paciente";
        String codigoEspecialidad = "PEDIATRIA";
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospitalDisponible = crearHospital(20L, "hospital2", especialidad);

        HospitalCercanoDTO hospital2 = crearHospitalCercano("hospital2");

        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(googlePlacesService.buscarHospitales(-34.6, -58.4)).thenReturn(List.of(hospital2));
        when(repoHospitales.findByPlaceIdInAndEspecialidadesCodigo(List.of("hospital2"), codigoEspecialidad))
                .thenReturn(List.of(hospitalDisponible));
        when(googlePlacesService.calcularTiempoEstimadoArriboMejorRuta(hospitalDisponible, "transporte-publico", -34.6,
                -58.4)).thenReturn(LocalTime.of(0, 15, 0));

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0idPaciente))
                .thenReturn(Optional.of(new Paciente()));

        List<HospitalCercanoDTO> hospitales = service.buscarHospitalesCercanos(-34.6, -58.4, codigoEspecialidad,
                null, auth0idPaciente);

        assertEquals(1, hospitales.size());
        assertEquals(LocalTime.of(0, 15, 0), hospitales.getFirst().getTiempoEstimadoArriboMejorRuta());
    }

    @Test
    void siNoSePuedeCalcularElTiempoDeArriboElCampoQuedaNull(){
        String auth0idPaciente = "auth0|Paciente";
        String codigoEspecialidad = "PEDIATRIA";
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        Hospital hospitalDisponible = crearHospital(20L, "hospital2", especialidad);

        HospitalCercanoDTO hospital2 = crearHospitalCercano("hospital2");

        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(googlePlacesService.buscarHospitales(-34.6, -58.4)).thenReturn(List.of(hospital2));
        when(repoHospitales.findByPlaceIdInAndEspecialidadesCodigo(List.of("hospital2"), codigoEspecialidad))
                .thenReturn(List.of(hospitalDisponible));
        when(googlePlacesService.calcularTiempoEstimadoArriboMejorRuta(hospitalDisponible, "transporte-publico", -34.6,
                -58.4)).thenReturn(null);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0idPaciente))
                .thenReturn(Optional.of(new Paciente()));

        List<HospitalCercanoDTO> hospitales = service.buscarHospitalesCercanos(-34.6, -58.4, codigoEspecialidad,
                "transporte-publico", auth0idPaciente);

        assertEquals(1, hospitales.size());
        assertNull(hospitales.getFirst().getTiempoEstimadoArriboMejorRuta());
    }

    @Test
    void noSePuedeSeleccionarUnHospitalQueNoExiste(){
        String auth0Id = "auth0|123";
        String placeId = "place_inexistente";
        String codigoEspecialidad = "PEDIATRIA";

        Paciente paciente = crearPaciente(10L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        ConsultaMedica consultaMedica = crearConsultaPendiente(paciente);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaMedica));
        when(repoHospitales.findByPlaceId(placeId)).thenReturn(Optional.empty());
        when(googlePlacesService.obtenerHospitalDesdeGoogle(placeId))
                .thenThrow(new NoSuchElementException("Hospital inexistente"));

        assertThrows(NoSuchElementException.class,
                () -> service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad));

        verify(repoHospitales, never()).save(any());
        verifyNoInteractions(repoGestorDeCola, ingresoColaService);
        verify(repoConsultasMedicas, never()).save(any());
    }

    @Test
    void noSePuedeSeleccionarHospitalSiYaTieneUnaAtencionEnCurso() {
        String auth0Id = "auth0|123";
        String placeId = "place_1";
        String codigoEspecialidad = "PEDIATRIA";

        Paciente paciente = crearPaciente(10L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, codigoEspecialidad);
        ConsultaMedica consultaEnCurso = new ConsultaMedica();
        consultaEnCurso.setPaciente(paciente);
        consultaEnCurso.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoEspecialidadesMedicas.findByCodigo(codigoEspecialidad)).thenReturn(Optional.of(especialidad));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consultaEnCurso));

        assertThrows(AtencionEnCursoException.class,
                () -> service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad));

        verifyNoInteractions(repoHospitales, googlePlacesService, repoGestorDeCola);
        verify(repoConsultasMedicas, never()).save(any());
    }

    @Test
    void noSePuedeSeleccionarUnHospitalSinotienePermisos(){
        String auth0Id = "auth0|sin-permiso";
        String placeId = "place_1";
        String codigoEspecialidad = "PEDIATRIA";

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.seleccionarHospital(auth0Id, placeId, codigoEspecialidad));

        verifyNoInteractions(repoConsultasMedicas, repoHospitales, googlePlacesService, repoEspecialidadesMedicas);
    }

    @Test
    void sePuedeObtenerTiempoEstimadoDeAtencion() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, "PEDIATRIA");
        Hospital hospital = crearHospital(10L, "place_1", especialidad);
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);
        consulta.setEspecialidad(especialidad);

        TiempoEstimadoAtencionResponse responseEsperada = new TiempoEstimadoAtencionResponse();
        responseEsperada.setFechaHoraAtencionEstimada(LocalDateTime.of(2026, 6, 20, 15, 30));

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));
        when(estimacionAtencionService.calcularPara(consulta)).thenReturn(responseEsperada);

        TiempoEstimadoAtencionResponse response = service.obtenerTiempoEstimadoDeAtencion(auth0Id);

        assertSame(responseEsperada, response);
    }
    @Test
    void noSePuedeObtenerTiempoEstimadoSinPermisos() {
        String auth0Id = "auth0|123";
        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id));

        verifyNoInteractions(repoConsultasMedicas);
    }

    @Test
    void noSePuedeObtenerTiempoEstimadoSinHospitalSeleccionado() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id));
    }

    @Test
    void sePuedeObtenerHospitalSeleccionadoConDireccionFormateada() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, "PEDIATRIA");
        Hospital hospital = crearHospital(10L, "place_1", especialidad);
        hospital.setNombre("Hospital Central");

        Direccion direccion = new Direccion();
        direccion.setCalle("Av. Siempre Viva");
        direccion.setAltura("742");
        direccion.setCiudad("CABA");
        direccion.setProvincia("Buenos Aires");
        hospital.setDireccion(direccion);

        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));

        HospitalSeleccionadoResponse response = service.obtenerHospitalSeleccionado(auth0Id);

        assertEquals(10L, response.getIdHospital());
        assertEquals("place_1", response.getPlaceId());
        assertEquals("Hospital Central", response.getNombre());
        assertEquals("Av. Siempre Viva 742, CABA, Buenos Aires", response.getDireccion());
    }

    @Test
    void seDevuelveDireccionNullSiElHospitalSeleccionadoNoTieneDireccion() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, "PEDIATRIA");
        Hospital hospital = crearHospital(10L, "place_1", especialidad);
        hospital.setNombre("Hospital Central");

        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));

        HospitalSeleccionadoResponse response = service.obtenerHospitalSeleccionado(auth0Id);

        assertEquals("Hospital Central", response.getNombre());
        assertNull(response.getDireccion());
    }

    @Test
    void noSePuedeObtenerHospitalSeleccionadoSinHospitalElegido() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.obtenerHospitalSeleccionado(auth0Id));
    }

    @Test
    void lanzaExcepcionSiNoSePuedeEstimarElHorario() {
        String auth0Id = "auth0|123";
        Paciente paciente = crearPaciente(1L);
        EspecialidadMedica especialidad = crearEspecialidad(30L, "PEDIATRIA");
        Hospital hospital = crearHospital(10L, "place_1", especialidad);
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setHospital(hospital);
        consulta.setEspecialidad(especialidad);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id)).thenReturn(Optional.of(paciente));
        when(repoConsultasMedicas.findFirstByPacienteIdAndEstadoConsultaIn(eq(paciente.getId()), any()))
                .thenReturn(Optional.of(consulta));
        when(estimacionAtencionService.calcularPara(consulta)).thenThrow(new NoSePudoEstimarElHorarioDeAtencion());

        assertThrows(NoSePudoEstimarElHorarioDeAtencion.class,
                () -> service.obtenerTiempoEstimadoDeAtencion(auth0Id));
    }
    private Paciente crearPaciente(Long id) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        return paciente;
    }

    private ConsultaMedica crearConsultaPendiente(Paciente paciente) {
        ConsultaMedica consultaMedica = new ConsultaMedica();
        consultaMedica.setPaciente(paciente);
        consultaMedica.setEstadoConsulta(EstadoConsulta.PENDIENTE);
        consultaMedica.setFechaHoraCreacion(LocalDateTime.now().minusHours(1));
        return consultaMedica;
    }

    private Hospital crearHospital(Long id, String placeId, EspecialidadMedica especialidad) {
        Hospital hospital = new Hospital();
        hospital.setId(id);
        hospital.setPlaceId(placeId);
        hospital.getEspecialidades().add(especialidad);
        return hospital;
    }

    private EspecialidadMedica crearEspecialidad(Long id, String codigo) {
        EspecialidadMedica especialidad = new EspecialidadMedica();
        especialidad.setId(id);
        especialidad.setCodigo(codigo);
        especialidad.setNombre(codigo);
        return especialidad;
    }

    private HospitalCercanoDTO crearHospitalCercano(String placeId) {
        HospitalCercanoDTO hospital = new HospitalCercanoDTO();
        hospital.setPlaceId(placeId);
        hospital.setNombre(placeId);
        hospital.setDireccion("direccion " + placeId);
        return hospital;
    }
}




