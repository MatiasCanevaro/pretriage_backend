package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.repositories.RepoSalas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaServiceTest {

    @Mock
    private RepoSalas repoSalas;

    @InjectMocks
    private SalaService salaService;

    @Test
    void obtieneSoloLasSalasActivasDelHospitalYEspecialidadSolicitados() {
        Long hospitalId = 1L;
        String codigoEspecialidad = "CLINICA_MEDICA";
        Sala sala = mock(Sala.class);
        when(sala.getId()).thenReturn(7L);
        when(sala.getNombre()).thenReturn("Consultorio 3");
        when(repoSalas.findByHospitalIdAndEspecialidadCodigoAndActivaTrue(
                hospitalId, codigoEspecialidad)).thenReturn(List.of(sala));

        List<SalaDTO> resultado = salaService.obtenerSalas(hospitalId, codigoEspecialidad);

        assertEquals(1, resultado.size());
        assertEquals(7L, resultado.getFirst().getId());
        assertEquals("Consultorio 3", resultado.getFirst().getNombre());
    }

    @Test
    void obtieneLaSalaDentroDelHospitalIndicado() {
        Sala sala = mock(Sala.class);
        when(repoSalas.findByIdAndHospitalId(7L, 1L)).thenReturn(Optional.of(sala));

        Sala resultado = salaService.obtenerSala(7L, 1L);

        assertSame(sala, resultado);
    }
}
