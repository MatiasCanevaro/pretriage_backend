package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class TiempoEstimadoServiceTest {


    private final Long TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE = 7200L;

    @InjectMocks
    private TiempoEstimadoService service;

    @BeforeEach
    void setUp(){
        ReflectionTestUtils.setField(this.service, "TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE", TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
    }

    @Test
    void seCalculaCorrectamenteElRangoDelTiempoEstimadoDeAtencion(){
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consultaMedicaMock1 = new ConsultaMedica();
        consultaMedicaMock1.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock1.setHospital(hospitalMock);
        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consultaMedicaMock2 = new ConsultaMedica();
        consultaMedicaMock2.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consultaMedicaMock2.setHospital(hospitalMock);
        consultaMedicaMock2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock1);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock2);

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(10));
        atencionMedica3.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(5));

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(14));
        atencionMedica2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(1));

        List<AtencionMedica> atencionesMedicasActuales = List.of(atencionMedica2, atencionMedica3);

        LocalDateTime antesDeCalculo = LocalDateTime.now().plusMinutes(1);

        List<LocalDateTime> rangoTiempoEstimado = this.service.calcularTiempoDeAtencionPara(
                consultaMedicaMock1,
                atencionesMedicasActuales,
                gestorDeCola);

        assertEquals(2, rangoTiempoEstimado.size());

        LocalDateTime tiempoMinimo = rangoTiempoEstimado.get(0);
        LocalDateTime tiempoMaximo = rangoTiempoEstimado.get(1);

        LocalDateTime tiempoEstimadoCentral = antesDeCalculo.plusSeconds(TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);

        assertEquals(tiempoEstimadoCentral.minusMinutes(10),tiempoMinimo);
        assertEquals(tiempoEstimadoCentral.plusMinutes(10),tiempoMaximo);
    }

    @Test
    void noSeCalculaCorrectamenteElRangoDelTiempoEstimadoDeAtencionSiNoEstaEnLaColaLaConsulta(){
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consultaMedicaMock1 = new ConsultaMedica();
        consultaMedicaMock1.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock1.setHospital(hospitalMock);
        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consultaMedicaMock2 = new ConsultaMedica();
        consultaMedicaMock2.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consultaMedicaMock2.setHospital(hospitalMock);
        consultaMedicaMock2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock2);

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(10));
        atencionMedica3.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(5));

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(14));
        atencionMedica2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(1));

        List<AtencionMedica> atencionesMedicasActuales = List.of(atencionMedica2, atencionMedica3);

        List<LocalDateTime> rangoTiempoEstimado = this.service.calcularTiempoDeAtencionPara(
                consultaMedicaMock1,
                atencionesMedicasActuales,
                gestorDeCola);

        assertTrue(rangoTiempoEstimado.isEmpty());
    }

    @Test
    void calcularTodos_CalculaCorrectamenteTiempoEstimadoParaTodasLasConsultas() {

        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consulta1 = new ConsultaMedica();
        consulta1.setId(1L);
        consulta1.setFechaHoraCreacion(LocalDateTime.now());
        consulta1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta1.setHospital(hospitalMock);
        consulta1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consulta2 = new ConsultaMedica();
        consulta2.setId(2L);
        consulta2.setFechaHoraCreacion(LocalDateTime.now());
        consulta2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consulta2.setHospital(hospitalMock);
        consulta2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consulta3 = new ConsultaMedica();
        consulta3.setId(3L);
        consulta3.setFechaHoraCreacion(LocalDateTime.now());
        consulta3.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta3.setHospital(hospitalMock);
        consulta3.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta1);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta2);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta3);

        AtencionMedica atencion1 = new AtencionMedica();
        atencion1.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(10));
        atencion1.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(5));

        AtencionMedica atencion2 = new AtencionMedica();
        atencion2.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(14));
        atencion2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(1));

        List<AtencionMedica> atenciones = List.of(atencion1, atencion2);

        List<TiempoEstimadoAtencionResponse> resultados = service.calcularTodos(gestorDeCola, atenciones);

        assertEquals(3, resultados.size());

        LocalDateTime proximaSala = atencion2.getFechaHoraFinAtencion();

        TiempoEstimadoAtencionResponse resultado1 = resultados.getFirst();
        assertEquals(2L, resultado1.getIdConsulta());

        assertEqualsTiempoEstimado(resultado1.getFechaHoraAtencionEstimadaDesde(),proximaSala.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado1.getFechaHoraAtencionEstimadaHasta(),proximaSala.plusMinutes(10));

        LocalDateTime tiempoPosicion1 = proximaSala.plusSeconds(TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
        TiempoEstimadoAtencionResponse resultado2 = resultados.get(1);
        assertEquals(1L, resultado2.getIdConsulta());
        assertEqualsTiempoEstimado(resultado2.getFechaHoraAtencionEstimadaDesde(),tiempoPosicion1.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado2.getFechaHoraAtencionEstimadaHasta(),tiempoPosicion1.plusMinutes(10));


        LocalDateTime tiempoPosicion2 = proximaSala.plusSeconds(2 * TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
        TiempoEstimadoAtencionResponse resultado3 = resultados.get(2);
        assertEquals(3L, resultado3.getIdConsulta());
        assertEqualsTiempoEstimado(resultado3.getFechaHoraAtencionEstimadaDesde(),tiempoPosicion2.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado3.getFechaHoraAtencionEstimadaHasta(),tiempoPosicion2.plusMinutes(10));
    }

    @Test
    void calcularTodos_ColaVacia_RetornaListaVacia() {
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);

        List<AtencionMedica> atenciones = List.of();

        List<TiempoEstimadoAtencionResponse> resultados = service.calcularTodos(gestorDeCola, atenciones);

        assertNotNull(resultados);
        assertTrue(resultados.isEmpty());
    }

    @Test
    void calcularTodos_SinAtencionesActivas_UsaTiempoActual() {
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consulta1 = new ConsultaMedica();
        consulta1.setId(1L);
        consulta1.setFechaHoraCreacion(LocalDateTime.now());
        consulta1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta1.setHospital(hospitalMock);
        consulta1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consulta2 = new ConsultaMedica();
        consulta2.setId(2L);
        consulta2.setFechaHoraCreacion(LocalDateTime.now());
        consulta2.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta2.setHospital(hospitalMock);
        consulta2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta1);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta2);

        List<AtencionMedica> atenciones = List.of();

        LocalDateTime tiempoEstimado = LocalDateTime.now();

        List<TiempoEstimadoAtencionResponse> resultados = service.calcularTodos(gestorDeCola, atenciones);

        assertEquals(2, resultados.size());

        TiempoEstimadoAtencionResponse resultado = resultados.getFirst();
        assertEquals(1L, resultado.getIdConsulta());

        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaDesde(),tiempoEstimado.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaHasta(),tiempoEstimado.plusMinutes(10));

        tiempoEstimado = tiempoEstimado.plusSeconds(TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
        resultado = resultados.get(1);
        assertEquals(2L, resultado.getIdConsulta());

        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaDesde(),tiempoEstimado.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaHasta(),tiempoEstimado.plusMinutes(10));
    }

    @Test
    void calcularTodos_AtencionesConFechaFinNula_UsaTiempoActual() {
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consulta1 = new ConsultaMedica();
        consulta1.setId(1L);
        consulta1.setFechaHoraCreacion(LocalDateTime.now());
        consulta1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta1.setHospital(hospitalMock);
        consulta1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta1);

        AtencionMedica atencion1 = new AtencionMedica();
        atencion1.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(10));
        atencion1.setFechaHoraFinAtencion(null);

        List<AtencionMedica> atenciones = List.of(atencion1);

        LocalDateTime tiempoEstimado = LocalDateTime.now();
        List<TiempoEstimadoAtencionResponse> resultados = service.calcularTodos(gestorDeCola, atenciones);

        assertEquals(1, resultados.size());

        TiempoEstimadoAtencionResponse resultado = resultados.getFirst();
        assertEquals(1L, resultado.getIdConsulta());
        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaDesde(),tiempoEstimado.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaHasta(),tiempoEstimado.plusMinutes(10));
    }

    @Test
    void calcularTodos_ProximaSalaEnElPasado_UsaTiempoActual() {
        Hospital hospitalMock = new Hospital();
        hospitalMock.setId(1L);

        ConsultaMedica consulta1 = new ConsultaMedica();
        consulta1.setId(1L);
        consulta1.setFechaHoraCreacion(LocalDateTime.now());
        consulta1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consulta1.setHospital(hospitalMock);
        consulta1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        GestorDeCola gestorDeCola = new GestorDeCola();
        gestorDeCola.setHospital(hospitalMock);
        gestorDeCola.agregarConsultaMedicaALaCola(consulta1);

        AtencionMedica atencion1 = new AtencionMedica();
        atencion1.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(20));
        atencion1.setFechaHoraFinAtencion(LocalDateTime.now().minusMinutes(5));

        List<AtencionMedica> atenciones = List.of(atencion1);

        LocalDateTime tiempoEstimado = LocalDateTime.now();

        List<TiempoEstimadoAtencionResponse> resultados = service.calcularTodos(gestorDeCola, atenciones);

        assertEquals(1, resultados.size());

        TiempoEstimadoAtencionResponse resultado = resultados.getFirst();
        assertEquals(1L, resultado.getIdConsulta());

        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaDesde(),tiempoEstimado.minusMinutes(10));
        assertEqualsTiempoEstimado(resultado.getFechaHoraAtencionEstimadaHasta(),tiempoEstimado.plusMinutes(10));
    }


    private  void assertEqualsTiempoEstimado(LocalDateTime resultado, LocalDateTime tiempoEstimado) {
        assertEquals(tiempoEstimado.getHour(), resultado.getHour());
        assertEquals(tiempoEstimado.getMinute(), resultado.getMinute());
        assertEquals(tiempoEstimado.getSecond(), resultado.getSecond());
        assertEquals(tiempoEstimado.getDayOfMonth(), resultado.getDayOfMonth());
    }
}
