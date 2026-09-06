package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.NotificacionSalaDTO;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EntradaCola;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.EstadoEntradaCola;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoEntradasCola;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionSalaServiceTest {

    @Mock PacienteService pacienteService;
    @Mock RepoEntradasCola repoEntradasCola;
    @Mock RepoConsultasMedicas repoConsultasMedicas;
    @Mock EstimacionAtencionService estimacionAtencionService;
    @InjectMocks EsperaPacienteService service;

    @Test
    void obtenerNotificacionSalaDe_llamado_retornaCodigoSalaSinEstimar() {
        Sala sala = new Sala();
        sala.setNombre("Consultorio 1");
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setId(10L);
        consulta.setSala(sala);
        consulta.setEstadoConsulta(EstadoConsulta.LLAMADO);

        EntradaCola entrada = new EntradaCola();
        entrada.setConsultaMedica(consulta);
        entrada.setEstado(EstadoEntradaCola.LLAMADO);

        when(repoConsultasMedicas.findById(10L)).thenReturn(Optional.of(consulta));
        when(repoEntradasCola.findByConsultaMedicaId(10L)).thenReturn(Optional.of(entrada));

        NotificacionSalaDTO dto = service.obtenerNotificacionSalaDe(10L);

        assertEquals(10L, dto.getConsultaId());
        assertEquals(EstadoConsulta.LLAMADO, dto.getEstadoConsulta());
        assertEquals(EstadoEntradaCola.LLAMADO, dto.getEstadoEntradaCola());
        assertEquals("Consultorio 1", dto.getCodigoSala());
    }

    @Test
    void obtenerNotificacionSalaDe_enCola_retornaCodigoSala() {
        Sala sala = new Sala();
        sala.setNombre("Sala 2");
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setId(11L);
        consulta.setSala(sala);
        consulta.setEstadoConsulta(EstadoConsulta.EN_COLA);

        EntradaCola entrada = new EntradaCola();
        entrada.setConsultaMedica(consulta);
        entrada.setEstado(EstadoEntradaCola.EN_COLA);

        when(repoConsultasMedicas.findById(11L)).thenReturn(Optional.of(consulta));
        when(repoEntradasCola.findByConsultaMedicaId(11L)).thenReturn(Optional.of(entrada));

        NotificacionSalaDTO dto = service.obtenerNotificacionSalaDe(11L);

        assertEquals("Sala 2", dto.getCodigoSala());
        assertEquals(EstadoEntradaCola.EN_COLA, dto.getEstadoEntradaCola());
    }

    @Test
    void obtenerNotificacionSalaDe_sinSala_retornaNull() {
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setId(12L);
        consulta.setSala(null);
        consulta.setEstadoConsulta(EstadoConsulta.EN_COLA);

        EntradaCola entrada = new EntradaCola();
        entrada.setConsultaMedica(consulta);
        entrada.setEstado(EstadoEntradaCola.EN_COLA);

        when(repoConsultasMedicas.findById(12L)).thenReturn(Optional.of(consulta));
        when(repoEntradasCola.findByConsultaMedicaId(12L)).thenReturn(Optional.of(entrada));

        NotificacionSalaDTO dto = service.obtenerNotificacionSalaDe(12L);

        assertNull(dto.getCodigoSala());
    }

    @Test
    void obtenerNotificacionSalaDe_consultaInexistente_lanzaExcepcion() {
        when(repoConsultasMedicas.findById(99L)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.obtenerNotificacionSalaDe(99L));
    }

    @Test
    void obtenerNotificacionSalaDe_sinEntrada_lanzaExcepcion() {
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setId(13L);
        when(repoConsultasMedicas.findById(13L)).thenReturn(Optional.of(consulta));
        when(repoEntradasCola.findByConsultaMedicaId(13L)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.obtenerNotificacionSalaDe(13L));
    }
}
