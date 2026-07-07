package com.pretriage.backend.model;

import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class GestorDeColaTest {


    private Hospital hospitalMock;
    private GestorDeCola gestorDeCola;

    private final Long TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE = 7200L;

    @BeforeEach
    void setUp(){
        this.gestorDeCola = new GestorDeCola();
        this.hospitalMock = new Hospital();
        this.gestorDeCola.setHospital(this.hospitalMock);
        ReflectionTestUtils.setField(this.gestorDeCola, "TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE", TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);
    }

    @Test
    void sePuedeIngresarUnaConsultaMedicaEnLaCola(){

        ConsultaMedica consultaMedicaMock = new ConsultaMedica();
        consultaMedicaMock.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock.setHospital(this.hospitalMock);
        consultaMedicaMock.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock);

        assertEquals(1,this.gestorDeCola.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock, this.gestorDeCola.getConsultasEnEspera().getFirst());
    }

    @Test
    void alIngresarOtraConsultaMedicaSeReordenaLaColaPorPrioridad(){

        ConsultaMedica consultaMedicaMock1 = new ConsultaMedica();
        consultaMedicaMock1.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock1.setHospital(this.hospitalMock);
        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.PENDIENTE);


        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock1);

        assertEquals(1,this.gestorDeCola.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock1, this.gestorDeCola.getConsultasEnEspera().getFirst());

        //llega otro paciente a la cola
        ConsultaMedica consultaMedicaMock2 = new ConsultaMedica();
        consultaMedicaMock2.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consultaMedicaMock2.setHospital(this.hospitalMock);
        consultaMedicaMock2.setEstadoConsulta(EstadoConsulta.PENDIENTE);


        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock2);

        assertEquals(2,this.gestorDeCola.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock2, this.gestorDeCola.getConsultasEnEspera().getFirst());//prioriza al urgente
        assertEquals(consultaMedicaMock1, this.gestorDeCola.getConsultasEnEspera().getLast());
    }

    @Test
    void alIngresarOtraConsultaMedicaSeReordenaLaColaPorPrioridadYSeEliminanLosQueYaFueronAtendidos(){

        ConsultaMedica consultaMedicaMock1 = new ConsultaMedica();
        consultaMedicaMock1.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock1.setHospital(this.hospitalMock);
        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock1);

        assertEquals(1,this.gestorDeCola.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock1, this.gestorDeCola.getConsultasEnEspera().getFirst());

        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.FINALIZADA);//ya fue atendido por el médico

        //llega otro paciente a la cola
        ConsultaMedica consultaMedicaMock2 = new ConsultaMedica();
        consultaMedicaMock2.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consultaMedicaMock2.setHospital(this.hospitalMock);
        consultaMedicaMock2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock2);

        assertEquals(1,this.gestorDeCola.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock2, this.gestorDeCola.getConsultasEnEspera().getFirst());
    }

    @Test
    void seCalculaCorrectamenteElRangoDelTiempoEstimadoDeAtencion(){
        ConsultaMedica consultaMedicaMock1 = new ConsultaMedica();
        consultaMedicaMock1.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedicaMock1.setHospital(this.hospitalMock);
        consultaMedicaMock1.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        ConsultaMedica consultaMedicaMock2 = new ConsultaMedica();
        consultaMedicaMock2.setFechaHoraCreacion(LocalDateTime.now());
        consultaMedicaMock2.setNivelDeGravedadBot(NivelDeGravedad.URGENTE);
        consultaMedicaMock2.setHospital(this.hospitalMock);
        consultaMedicaMock2.setEstadoConsulta(EstadoConsulta.PENDIENTE);

        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock1);
        this.gestorDeCola.agregarConsultaMedicaALaCola(consultaMedicaMock2);

        AtencionMedica atencionMedica3 = new AtencionMedica();
        atencionMedica3.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(10));
        atencionMedica3.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(5));

        AtencionMedica atencionMedica2 = new AtencionMedica();
        atencionMedica2.setFechaHoraInicioAtencion(LocalDateTime.now().minusMinutes(14));
        atencionMedica2.setFechaHoraFinAtencion(LocalDateTime.now().plusMinutes(1));

        List<AtencionMedica> atencionesMedicasActuales = List.of(atencionMedica2, atencionMedica3);

        LocalDateTime antesDeCalculo = LocalDateTime.now().plusMinutes(1);

        List<LocalDateTime> rangoTiempoEstimado = this.gestorDeCola.calcularTiempoDeAtencionPara(consultaMedicaMock1, atencionesMedicasActuales);

        assertEquals(2, rangoTiempoEstimado.size());

        LocalDateTime tiempoMinimo = rangoTiempoEstimado.get(0);
        LocalDateTime tiempoMaximo = rangoTiempoEstimado.get(1);

        LocalDateTime tiempoEstimadoCentral = antesDeCalculo.plusSeconds(TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);

        assertEquals(tiempoEstimadoCentral.minusMinutes(10),tiempoMinimo);
        assertEquals(tiempoEstimadoCentral.plusMinutes(10),tiempoMaximo);
    }


}
