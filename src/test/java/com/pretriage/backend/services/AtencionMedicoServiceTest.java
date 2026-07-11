package com.pretriage.backend.services;

import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtencionMedicoServiceTest {
    @Mock RepoMedico repoMedico;
    @Mock RepoHospitales repoHospitales;
    @Mock RepoEspecialidadesMedicas repoEspecialidadesMedicas;
    @Mock RepoSalas repoSalas;
    @Mock RepoAsignacionesMedicoHospital repoAsignacionesMedicoHospital;
    @Mock RepoSesionesAtencionMedica repoSesionesAtencionMedica;
    @Mock RepoGestoresDeColas repoGestoresDeColas;
    @Mock RepoEntradasCola repoEntradasCola;
    @Mock RepoConsultasMedicas repoConsultasMedicas;
    @Mock RepoAtencionesMedicas repoAtencionesMedicas;
    @InjectMocks AtencionMedicoService service;

    @Test
    void noPermitePausarUnaSesionConConsultaTomada() {
        Medico medico = new Medico();
        medico.setId(10L);
        SesionAtencionMedica sesion = new SesionAtencionMedica();
        sesion.setId(20L);
        sesion.setMedico(medico);
        sesion.setEstado(EstadoSesionMedica.ACTIVA);

        when(repoSesionesAtencionMedica.findByIdAndMedicoUsuarioAuthId(20L, "auth0"))
                .thenReturn(Optional.of(sesion));
        when(repoEntradasCola.existsByConsultaMedicaMedicoIdAndEstadoIn(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.pausarSesion("auth0", 20L));
    }
    @Test
    void creaLaAtencionHistoricaCuandoElPacienteConfirmaPresencia() {
        Medico medico = new Medico(); medico.setId(10L);
        Sala sala = new Sala(); sala.setId(30L); sala.setNombre("Sala 1");
        Paciente paciente = new Paciente(); paciente.setId(40L);
        ConsultaMedica consulta = new ConsultaMedica();
        consulta.setId(50L); consulta.setMedico(medico); consulta.setSala(sala); consulta.setPaciente(paciente);
        EntradaCola entrada = new EntradaCola();
        entrada.setConsultaMedica(consulta); entrada.setEstado(EstadoEntradaCola.LLAMADO);
        SesionAtencionMedica sesion = new SesionAtencionMedica();
        sesion.setId(20L); sesion.setMedico(medico); sesion.setSala(sala); sesion.setEstado(EstadoSesionMedica.ACTIVA);
        when(repoSesionesAtencionMedica.findByIdAndMedicoUsuarioAuthId(20L, "auth0")).thenReturn(Optional.of(sesion));
        when(repoEntradasCola.findByConsultaMedicaId(50L)).thenReturn(Optional.of(entrada));

        service.confirmarPresente("auth0", 20L, 50L);

        org.mockito.ArgumentCaptor<AtencionMedica> captor = org.mockito.ArgumentCaptor.forClass(AtencionMedica.class);
        verify(repoAtencionesMedicas).save(captor.capture());
        assertEquals(EstadoAtencionMedica.EN_CURSO, captor.getValue().getEstado());
        assertEquals(sesion, captor.getValue().getSesionAtencionMedica());
        assertEquals(consulta, captor.getValue().getConsultaMedica());
    }
}