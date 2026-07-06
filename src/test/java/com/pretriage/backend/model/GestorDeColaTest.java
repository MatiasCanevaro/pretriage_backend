package com.pretriage.backend.model;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

        LocalDateTime antesDeCalculo = LocalDateTime.now();

        List<LocalDateTime> rangoTiempoEstimado = this.gestorDeCola.calcularTiempoDeAtencionPara(consultaMedicaMock1);

        assertEquals(2, rangoTiempoEstimado.size());

        LocalDateTime tiempoMinimo = rangoTiempoEstimado.get(0);
        LocalDateTime tiempoMaximo = rangoTiempoEstimado.get(1);

        LocalDateTime tiempoEstimadoCentral = antesDeCalculo.plusSeconds(TIEMPO_ESTIMADO_DE_ATENCION_TRIAGE);

        long diferenciaMinima = ChronoUnit.MINUTES.between(tiempoEstimadoCentral, tiempoMinimo);
        long diferenciaMaxima = ChronoUnit.MINUTES.between(tiempoEstimadoCentral, tiempoMaximo);

        assertTrue(diferenciaMinima >= -11 && diferenciaMinima <= -9);
        assertTrue(diferenciaMaxima >= 9 && diferenciaMaxima <= 11);
    }


}
