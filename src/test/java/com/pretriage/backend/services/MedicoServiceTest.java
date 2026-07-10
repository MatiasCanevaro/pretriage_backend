package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.PacienteDTO;
import com.pretriage.backend.exceptions.HospitalNoEncontradoException;
import com.pretriage.backend.exceptions.MedicoNoEncontradoException;
import com.pretriage.backend.model.consultas.ConsultaMedica;
import com.pretriage.backend.model.consultas.EstadoConsulta;
import com.pretriage.backend.model.consultas.GestorDeCola;
import com.pretriage.backend.model.consultas.NivelDeGravedad;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoConsultasMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoMedico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.parameters.P;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MedicoServiceTest {

    @Mock
    private ColaService colaService;
    @Mock
    private RepoMedico repoMedico;
    @Mock
    private RepoHospitales repoHospitales;
    @Mock
    private RepoConsultasMedicas repoConsultasMedicas;

    @InjectMocks
    private MedicoService medicoService;

    @Test
    void sePuedeObtenerTodosLosPacientesParaAtender() {
        String auth0IdMedico = "auth0IdMedico";
        Medico medico = mock(Medico.class);
        Hospital hospital = mock(Hospital.class);
        Long idHospital = 1L;
        GestorDeCola gestorDeCola = mock(GestorDeCola.class);

        Paciente paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setUsuarioAuth(mock(UsuarioAuth.class));
        Paciente paciente2 = new Paciente();
        paciente2.setId(2L);
        paciente2.setUsuarioAuth(mock(UsuarioAuth.class));
        Paciente paciente3 = new Paciente();
        paciente3.setId(3L);
        paciente3.setUsuarioAuth(mock(UsuarioAuth.class));

        ConsultaMedica consultaMedica1 = new ConsultaMedica();
        consultaMedica1.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedica1.setFechaHoraCreacion(LocalDateTime.now().minusMinutes(10));
        consultaMedica1.setPaciente(paciente1);
        consultaMedica1.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);

        ConsultaMedica consultaMedica2 = new ConsultaMedica();
        consultaMedica2.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedica2.setFechaHoraCreacion(LocalDateTime.now().minusMinutes(20));
        consultaMedica2.setPaciente(paciente2);
        consultaMedica2.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);

        ConsultaMedica consultaMedica3 = new ConsultaMedica();
        consultaMedica3.setNivelDeGravedadBot(NivelDeGravedad.NORMAL);
        consultaMedica3.setFechaHoraCreacion(LocalDateTime.now().minusMinutes(30));
        consultaMedica3.setPaciente(paciente3);
        consultaMedica3.setEstadoConsulta(EstadoConsulta.HOSPITAL_SELECCIONADO);

        when(repoMedico.findByUsuarioAuth0Id(auth0IdMedico))
                .thenReturn(Optional.of(medico));

        when(repoHospitales.findById(idHospital))
                .thenReturn(Optional.of(hospital));

        when(colaService.obtenerColaDe(hospital))
                .thenReturn(gestorDeCola);

        when(gestorDeCola.obtenerPacientesParaAtender())
                .thenReturn(List.of(paciente1, paciente2, paciente3));

        when(repoConsultasMedicas.findByPacienteId(paciente1.getId()))
                .thenReturn(Optional.of(consultaMedica1));
        when(repoConsultasMedicas.findByPacienteId(paciente2.getId()))
                .thenReturn(Optional.of(consultaMedica2));
        when(repoConsultasMedicas.findByPacienteId(paciente3.getId()))
                .thenReturn(Optional.of(consultaMedica3));

        List<PacienteDTO> pacientesResult = medicoService.obtenerTodosPacientesParaAtender(idHospital, auth0IdMedico);

        assertEquals(3, pacientesResult.size());
        PacienteDTO pacienteDTO = pacientesResult.getFirst();

        assertEquals(paciente1.getId(), pacienteDTO.getIdPaciente());
        assertEquals(consultaMedica1.getNivelDeGravedadBot(), pacienteDTO.getNivelDeGravedadBot());
        assertEquals(consultaMedica1.getFechaHoraCreacion(), pacienteDTO.getFechaHoraIngresoAColaEspera());
        assertEquals(consultaMedica1.getEstadoConsulta(), pacienteDTO.getEstadoConsulta());

        pacienteDTO = pacientesResult.get(1);
        assertEquals(paciente2.getId(), pacienteDTO.getIdPaciente());
        assertEquals(consultaMedica2.getNivelDeGravedadBot(), pacienteDTO.getNivelDeGravedadBot());
        assertEquals(consultaMedica2.getFechaHoraCreacion(), pacienteDTO.getFechaHoraIngresoAColaEspera());
        assertEquals(consultaMedica2.getEstadoConsulta(), pacienteDTO.getEstadoConsulta());

        pacienteDTO = pacientesResult.get(2);
        assertEquals(paciente3.getId(), pacienteDTO.getIdPaciente());
        assertEquals(consultaMedica3.getNivelDeGravedadBot(), pacienteDTO.getNivelDeGravedadBot());
        assertEquals(consultaMedica3.getFechaHoraCreacion(), pacienteDTO.getFechaHoraIngresoAColaEspera());
        assertEquals(consultaMedica3.getEstadoConsulta(), pacienteDTO.getEstadoConsulta());
    }

    @Test
    void seLanzaHospitalNoEncontradoExceptionCuandoElHospitalNoExiste() {
        String auth0IdMedico = "auth0IdMedico";
        Medico medico = mock(Medico.class);
        Long idHospital = 1L;

        when(repoMedico.findByUsuarioAuth0Id(auth0IdMedico))
                .thenReturn(Optional.of(medico));

        when(repoHospitales.findById(idHospital))
                .thenReturn(Optional.empty());

        assertThrows(HospitalNoEncontradoException.class, ()->
                medicoService.obtenerTodosPacientesParaAtender(idHospital, auth0IdMedico)
        );
    }

    @Test
    void seLanzaMedicoNoEncontradoExceptionCuandoElMedicoNoExiste() {
        String auth0IdMedico = "auth0IdMedico";
        Long idHospital = 1L;

        when(repoMedico.findByUsuarioAuth0Id(auth0IdMedico))
                .thenReturn(Optional.empty());

        assertThrows(MedicoNoEncontradoException.class, ()->
                medicoService.obtenerTodosPacientesParaAtender(idHospital, auth0IdMedico)
        );
    }

    @Test
    void seDevuelveListaVaciaSiNoExistenPacientesParaAtender() {
        String auth0IdMedico = "auth0IdMedico";
        Medico medico = mock(Medico.class);
        Hospital hospital = mock(Hospital.class);
        Long idHospital = 1L;
        GestorDeCola gestorDeCola = mock(GestorDeCola.class);

        when(repoMedico.findByUsuarioAuth0Id(auth0IdMedico))
                .thenReturn(Optional.of(medico));

        when(repoHospitales.findById(idHospital))
                .thenReturn(Optional.of(hospital));

        when(colaService.obtenerColaDe(hospital))
                .thenReturn(gestorDeCola);

        when(gestorDeCola.obtenerPacientesParaAtender())
                .thenReturn(List.of());

        List<PacienteDTO> pacientesResult = medicoService.obtenerTodosPacientesParaAtender(idHospital, auth0IdMedico);

        assertEquals(0, pacientesResult.size());
    }

    @Test
    void seLanzaConsultaNoEncontradaExceptionCuandoLaConsultaNoExiste() {
        String auth0IdMedico = "auth0IdMedico";
        Medico medico = mock(Medico.class);
        Hospital hospital = mock(Hospital.class);
        Long idHospital = 1L;
        GestorDeCola gestorDeCola = mock(GestorDeCola.class);

        Paciente paciente1 = new Paciente();
        paciente1.setId(1L);
        paciente1.setUsuarioAuth(mock(UsuarioAuth.class));

        when(repoMedico.findByUsuarioAuth0Id(auth0IdMedico))
                .thenReturn(Optional.of(medico));

        when(repoHospitales.findById(idHospital))
                .thenReturn(Optional.of(hospital));

        when(colaService.obtenerColaDe(hospital))
                .thenReturn(gestorDeCola);

        when(gestorDeCola.obtenerPacientesParaAtender())
                .thenReturn(List.of(paciente1));

        when(repoConsultasMedicas.findByPacienteId(paciente1.getId()))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                ()-> medicoService.obtenerTodosPacientesParaAtender(idHospital, auth0IdMedico)
        );
    }

}
