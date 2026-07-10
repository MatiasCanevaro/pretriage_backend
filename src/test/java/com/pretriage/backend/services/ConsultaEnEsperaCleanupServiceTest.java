package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConsultaEnEsperaCleanupServiceTest {

    @Mock
    private RepoConsultasMedicas repoConsultasMedicas;

    @InjectMocks
    private ConsultaEnEsperaCleanupService cleanupService;

    @Test
    void seMarcanConsultasVencidasComoPacienteNoAsistio() {
        // Configurar el valor de la propiedad mediante ReflectionTestUtils para testear
        ReflectionTestUtils.setField(cleanupService, "tiempoLimiteEnEsperaSegundos", 3600);

        Paciente paciente1 = new Paciente();
        paciente1.setId(1L);
        Paciente paciente2 = new Paciente();
        paciente2.setId(2L);

        ConsultaMedica consultaVencida1 = new ConsultaMedica();
        consultaVencida1.setId(1L);
        consultaVencida1.setPaciente(paciente1);
        consultaVencida1.setEstadoConsulta(EstadoConsulta.EN_ESPERA);
        consultaVencida1.setFechaHoraPuestaEnEspera(LocalDateTime.now().minusMinutes(70)); // > 1 hora

        ConsultaMedica consultaVencida2 = new ConsultaMedica();
        consultaVencida2.setId(2L);
        consultaVencida2.setPaciente(paciente2);
        consultaVencida2.setEstadoConsulta(EstadoConsulta.EN_ESPERA);
        consultaVencida2.setFechaHoraPuestaEnEspera(LocalDateTime.now().minusMinutes(65)); // > 1 hora

        ConsultaMedica consultaNoVencida = new ConsultaMedica();
        consultaNoVencida.setId(3L);
        consultaNoVencida.setEstadoConsulta(EstadoConsulta.EN_ESPERA);
        consultaNoVencida.setFechaHoraPuestaEnEspera(LocalDateTime.now().minusMinutes(30)); // < 1 hora

        when(repoConsultasMedicas.findAllByEstadoConsultaAndFechaHoraPuestaEnEsperaBefore(
                eq(EstadoConsulta.EN_ESPERA), any(LocalDateTime.class)))
                .thenReturn(List.of(consultaVencida1, consultaVencida2));

        cleanupService.limpiarConsultasVencidas();

        assertEquals(EstadoConsulta.PACIENTE_NO_ASISTIO, consultaVencida1.getEstadoConsulta());
        assertEquals(EstadoConsulta.PACIENTE_NO_ASISTIO, consultaVencida2.getEstadoConsulta());
        assertEquals(EstadoConsulta.EN_ESPERA, consultaNoVencida.getEstadoConsulta());

        verify(repoConsultasMedicas).saveAll(List.of(consultaVencida1, consultaVencida2));
    }

    @Test
    void noSeMarcaNadaSiNoHayConsultasVencidas() {
        // Configurar el valor de la propiedad mediante ReflectionTestUtils para testear
        ReflectionTestUtils.setField(cleanupService, "tiempoLimiteEnEsperaSegundos", 3600);

        when(repoConsultasMedicas.findAllByEstadoConsultaAndFechaHoraPuestaEnEsperaBefore(
                eq(EstadoConsulta.EN_ESPERA), any(LocalDateTime.class)))
                .thenReturn(List.of());

        cleanupService.limpiarConsultasVencidas();

        verify(repoConsultasMedicas).saveAll(List.of());
    }
}
