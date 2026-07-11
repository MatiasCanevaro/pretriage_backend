package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
}
