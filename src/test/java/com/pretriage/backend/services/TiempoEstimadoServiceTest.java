package com.pretriage.backend.services;

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
}
