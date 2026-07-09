package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.AtencionMedica;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.colaDinamica.ColaModificadaEvent;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.repositories.RepoGestoresDeColas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ColaServiceTest {

    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private RepoGestoresDeColas repoGestorDeCola;
    @Mock
    private SalaService salaService;
    @Mock
    private TiempoEstimadoService tiempoService;

    @InjectMocks
    private ColaService colaService;

    @Test
    void sePuedeObtenerUnaColaDeConsulta(){
        ConsultaMedica consultaMedicaMock = mock(ConsultaMedica.class);
        GestorDeCola gestorDeColaMock = mock(GestorDeCola.class);

        Hospital hospital1 = new Hospital();
        hospital1.setId(1L);

        when(consultaMedicaMock.getHospital()).thenReturn(hospital1);
        when(repoGestorDeCola.findByHospitalId(hospital1.getId()))
                .thenReturn(Optional.of(gestorDeColaMock));

        GestorDeCola gestorDeColaResult = this.colaService.obtenerOCrearColaDeConsulta(consultaMedicaMock);

        assertEquals(gestorDeColaMock, gestorDeColaResult);
    }

    @Test
    void sePuedeCrearUnaColaDeConsulta(){
        ConsultaMedica consultaMedicaMock = mock(ConsultaMedica.class);

        Hospital hospital1 = new Hospital();
        hospital1.setId(1L);

        when(consultaMedicaMock.getHospital()).thenReturn(hospital1);
        when(repoGestorDeCola.findByHospitalId(hospital1.getId()))
                .thenReturn(Optional.empty());

        GestorDeCola gestorDeColaResult = this.colaService.obtenerOCrearColaDeConsulta(consultaMedicaMock);

        ArgumentCaptor<GestorDeCola> gestorDeColaNuevo = ArgumentCaptor.forClass(GestorDeCola.class);
        verify(repoGestorDeCola).save(gestorDeColaNuevo.capture());

        assertEquals(hospital1, gestorDeColaResult.getHospital());
        assertEquals(1, gestorDeColaResult.getConsultasEnEspera().size());
        assertEquals(consultaMedicaMock, gestorDeColaResult.getConsultasEnEspera().getFirst());
    }

    @Test
    void sePuedeAgregarConsultaMedicaEnSuColaCorrespondiente(){
        ConsultaMedica consultaMedicaMock = mock(ConsultaMedica.class);
        GestorDeCola gestorDeColaMock = mock(GestorDeCola.class);

        Hospital hospital1 = new Hospital();
        hospital1.setId(1L);

        when(consultaMedicaMock.getHospital()).thenReturn(hospital1);
        when(repoGestorDeCola.findByHospitalId(hospital1.getId()))
                .thenReturn(Optional.of(gestorDeColaMock));

        this.colaService.agregarConsulta(consultaMedicaMock);

        verify(gestorDeColaMock).agregarConsultaMedicaALaCola(consultaMedicaMock);//se agrega a la cola

        verify(salaService).obtenerAtencionesMedicasActuales(hospital1.getId()); // se obtienen las atenciones actuales

        verify(tiempoService).calcularTodos( // se recalculan los tiempos estimados de todos los afectados
                eq(gestorDeColaMock),
                anyList()
        );
        verify(publisher).publishEvent(any(ColaModificadaEvent.class)); // se publica el evento de cola modificada

        verify(repoGestorDeCola).save(gestorDeColaMock);
    }

    @Test
    void sePuedeSacarConsultaMedicaEnSuColaCorrespondiente(){
        ConsultaMedica consultaMedicaMock = mock(ConsultaMedica.class);
        GestorDeCola gestorDeColaMock = mock(GestorDeCola.class);

        Hospital hospital1 = new Hospital();
        hospital1.setId(1L);

        when(consultaMedicaMock.getHospital()).thenReturn(hospital1);
        when(repoGestorDeCola.findByHospitalId(hospital1.getId()))
                .thenReturn(Optional.of(gestorDeColaMock));

        this.colaService.sacarDeLaColaDelHospital(consultaMedicaMock);

        verify(gestorDeColaMock).sacarConsultaMedicaDeLaCola(consultaMedicaMock);//se saca de la cola

        verify(salaService).obtenerAtencionesMedicasActuales(hospital1.getId()); // se obtienen las atenciones actuales

        verify(tiempoService).calcularTodos( // se recalculan los tiempos estimados de todos los afectados
                eq(gestorDeColaMock),
                anyList()
        );
        verify(publisher).publishEvent(any(ColaModificadaEvent.class)); // se publica el evento de cola modificada

        verify(repoGestorDeCola).save(gestorDeColaMock);
    }
}
