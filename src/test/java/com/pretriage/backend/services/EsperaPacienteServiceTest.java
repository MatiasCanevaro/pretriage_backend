package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.hospitales.Sector;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EsperaPacienteServiceTest {
    @Mock PacienteService pacienteService;
    @Mock RepoEntradasCola repoEntradasCola;
    @Mock RepoConsultasMedicas repoConsultasMedicas;
    @Mock EstimacionAtencionService estimacionAtencionService;
    @InjectMocks EsperaPacienteService service;

    @Test
    void cancelaLaConsultaCuandoLaEsperaSuperaUnaHora() {
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setEstadoConsulta(EstadoConsulta.EN_ESPERA);
        EntradaCola entrada = new EntradaCola();
        entrada.setEstado(EstadoEntradaCola.EN_ESPERA);
        entrada.setConsultaMedica(consulta);
        entrada.setFechaHoraSalidaTemporal(LocalDateTime.now().minusMinutes(61));
        when(repoEntradasCola.findByEstadoAndFechaHoraSalidaTemporalBefore(
                any(EstadoEntradaCola.class), any(LocalDateTime.class))).thenReturn(List.of(entrada));

        service.cancelarEsperasVencidas();

        assertEquals(EstadoEntradaCola.CANCELADA, entrada.getEstado());
        assertEquals(EstadoConsulta.CANCELADA, consulta.getEstadoConsulta());
        verify(repoEntradasCola).save(entrada);
        verify(repoConsultasMedicas).save(consulta);
    }

    @Test
    void obtenerEstadoIncluyeSectorDelPaciente() {
        Paciente paciente = new Paciente(); paciente.setId(2L);
        Sector sector = new Sector(); sector.setId(8L); sector.setNombre("Guardia");
        ConsultaMedica consulta = new ConsultaMedica(); consulta.setId(5L); consulta.setPaciente(paciente);
        consulta.setSector(sector); consulta.setEstadoConsulta(EstadoConsulta.EN_COLA);
        EntradaCola entrada = new EntradaCola(); entrada.setId(1L); entrada.setConsultaMedica(consulta);
        entrada.setEstado(EstadoEntradaCola.EN_COLA);
        TiempoEstimadoAtencionResponse estimacion = new TiempoEstimadoAtencionResponse();
        when(pacienteService.obtenerPacienteConUsuarioAuthId("auth")).thenReturn(Optional.of(paciente));
        when(repoEntradasCola.findFirstByConsultaMedicaPacienteIdAndEstadoIn(eq(2L), any()))
                .thenReturn(Optional.of(entrada));
        when(estimacionAtencionService.calcularPara(consulta)).thenReturn(estimacion);

        var dto = service.obtenerEstado("auth");

        assertEquals(8L, dto.getSectorId());
        assertEquals("Guardia", dto.getNombreSector());
        assertSame(estimacion, dto.getTiempoEstimadoAtencion());
    }
}
