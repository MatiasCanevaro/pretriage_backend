package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngresoColaServiceTest {
    @Mock RepoConsultasMedicas repoConsultasMedicas; @Mock RepoGestoresDeColas repoGestoresDeColas;
    @Mock RepoEntradasCola repoEntradasCola; @Mock EstimacionAtencionService estimacionAtencionService;
    @InjectMocks IngresoColaService service;

    @Test
    void ambosCanalesIngresanMedianteEntradaCola() {
        Hospital hospital = new Hospital(); hospital.setId(1L);
        EspecialidadMedica especialidad = new EspecialidadMedica(); especialidad.setId(2L);
        ConsultaMedica consulta = new ConsultaMedica(); consulta.setId(3L); consulta.setHospital(hospital); consulta.setEspecialidad(especialidad);
        GestorDeCola gestor = new GestorDeCola(); gestor.setId(4L); gestor.setHospital(hospital); gestor.setEspecialidad(especialidad);
        when(repoGestoresDeColas.findByHospitalIdAndEspecialidadId(1L, 2L)).thenReturn(Optional.of(gestor));
        when(repoEntradasCola.findByConsultaMedicaId(3L)).thenReturn(Optional.empty());
        when(repoEntradasCola.findFirstByGestorDeColaIdOrderByOrdenRelativoDesc(4L)).thenReturn(Optional.empty());
        when(repoEntradasCola.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TiempoEstimadoAtencionResponse esperado = new TiempoEstimadoAtencionResponse();
        when(estimacionAtencionService.calcularPara(consulta)).thenReturn(esperado);

        assertSame(esperado, service.ingresar(consulta, NivelDeGravedad.URGENTE));

        ArgumentCaptor<EntradaCola> captor = ArgumentCaptor.forClass(EntradaCola.class);
        verify(repoEntradasCola).save(captor.capture());
        assertEquals(EstadoEntradaCola.EN_COLA, captor.getValue().getEstado());
        assertEquals(3, captor.getValue().getPrioridad());
        assertEquals(1L, captor.getValue().getOrdenRelativo());
        assertEquals(EstadoConsulta.EN_COLA, consulta.getEstadoConsulta());
    }

    @Test
    void consultaYaEnColaActualizaLaPrioridadDeLaEntradaExistente() {
        Hospital hospital = new Hospital(); hospital.setId(1L);
        EspecialidadMedica especialidad = new EspecialidadMedica(); especialidad.setId(2L);
        ConsultaMedica consulta = new ConsultaMedica(); consulta.setId(3L); consulta.setHospital(hospital); consulta.setEspecialidad(especialidad);
        GestorDeCola gestor = new GestorDeCola(); gestor.setId(4L); gestor.setHospital(hospital); gestor.setEspecialidad(especialidad);
        EntradaCola existente = new EntradaCola();
        existente.setGestorDeCola(gestor);
        existente.setConsultaMedica(consulta);
        existente.setEstado(EstadoEntradaCola.EN_COLA);
        existente.setPrioridad(2);
        existente.setOrdenRelativo(7L);
        when(repoGestoresDeColas.findByHospitalIdAndEspecialidadId(1L, 2L)).thenReturn(Optional.of(gestor));
        when(repoEntradasCola.findByConsultaMedicaId(3L)).thenReturn(Optional.of(existente));
        when(repoEntradasCola.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TiempoEstimadoAtencionResponse esperado = new TiempoEstimadoAtencionResponse();
        when(estimacionAtencionService.calcularPara(consulta)).thenReturn(esperado);

        assertSame(esperado, service.ingresar(consulta, NivelDeGravedad.URGENTE));

        ArgumentCaptor<EntradaCola> captor = ArgumentCaptor.forClass(EntradaCola.class);
        verify(repoEntradasCola).save(captor.capture());
        assertEquals(EstadoEntradaCola.EN_COLA, captor.getValue().getEstado());
        assertEquals(3, captor.getValue().getPrioridad());
        assertEquals(7L, captor.getValue().getOrdenRelativo());
        assertEquals(EstadoConsulta.EN_COLA, consulta.getEstadoConsulta());
        assertEquals(NivelDeGravedad.URGENTE, consulta.getNivelDeGravedadBot());
        verify(repoEntradasCola, never()).findFirstByGestorDeColaIdOrderByOrdenRelativoDesc(any());
    }
}
