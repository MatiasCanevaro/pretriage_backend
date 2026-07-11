package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.FormularioTriageRecepcionRequest;
import com.pretriage.backend.controllers.dtos.TriageResultDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.model.consultas.*;
import com.pretriage.backend.model.hospitales.*;
import com.pretriage.backend.model.personas.*;
import com.pretriage.backend.model.recepcion.*;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmisionRecepcionServiceTest {
    @Mock RepoRecepcionistas repoRecepcionistas; @Mock RepoHospitales repoHospitales;
    @Mock RepoSesionesRecepcion repoSesionesRecepcion; @Mock RepoAdmisionesRecepcion repoAdmisionesRecepcion;
    @Mock RepoPacientes repoPacientes; @Mock RepoConsultasMedicas repoConsultasMedicas;
    @Mock RepoEspecialidadesMedicas repoEspecialidadesMedicas; @Mock TriageFormularioService triageFormularioService;
    @Mock IngresoColaService ingresoColaService; @Mock ObjectMapper objectMapper;
    @InjectMocks AdmisionRecepcionService service;

    @Test
    void noPermiteDosSesionesActivas() {
        Recepcionista recepcionista = new Recepcionista(); recepcionista.setId(1L);
        when(repoRecepcionistas.findRecepcionistaByUsuarioAuthId("auth")).thenReturn(Optional.of(recepcionista));
        when(repoSesionesRecepcion.existsByRecepcionistaIdAndEstado(1L, EstadoSesionRecepcion.ACTIVA)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.iniciarSesion("auth", 10L));
    }

    @Test
    void finalizaFormularioEIngresaLaMismaConsultaALaCola() throws Exception {
        Paciente paciente = new Paciente(); paciente.setId(2L);
        Hospital hospital = new Hospital(); hospital.setId(3L);
        EspecialidadMedica especialidad = new EspecialidadMedica(); especialidad.setId(4L);
        ConsultaMedica consulta = new ConsultaMedica(); consulta.setId(5L); consulta.setPaciente(paciente);
        consulta.setHospital(hospital); consulta.setEspecialidad(especialidad); consulta.setCodigoLlamado("R-ABC123");
        Recepcionista recepcionista = new Recepcionista(); recepcionista.setId(1L);
        SesionRecepcion sesion = new SesionRecepcion(); sesion.setId(6L); sesion.setRecepcionista(recepcionista);
        sesion.setHospital(hospital); sesion.setEstado(EstadoSesionRecepcion.ACTIVA);
        AdmisionRecepcion admision = new AdmisionRecepcion(); admision.setId(7L); admision.setConsultaMedica(consulta);
        admision.setSesionRecepcion(sesion); admision.setEstado(EstadoAdmisionRecepcion.INICIADA);
        FormularioTriageRecepcionRequest formulario = new FormularioTriageRecepcionRequest(
                "dolor abdominal", List.of("dolor"), "hace dos horas", "empeora", 7,
                "abdomen", false, List.of(), List.of(), List.of(), List.of(), "no aplica", "");
        TriageResultDTO resultado = new TriageResultDTO("dolor abdominal", List.of("dolor"), "dos horas",
                "empeora", 7, List.of(), List.of(), List.of(), List.of(), "no aplica", "", 3, false, "evaluar");
        when(repoAdmisionesRecepcion.findByIdAndSesionRecepcionRecepcionistaUsuarioAuthId(7L, "auth"))
                .thenReturn(Optional.of(admision));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(triageFormularioService.clasificar(formulario)).thenReturn(resultado);
        when(ingresoColaService.ingresar(consulta, NivelDeGravedad.URGENTE)).thenAnswer(inv -> {
            consulta.setNivelDeGravedadBot(NivelDeGravedad.URGENTE); consulta.setEstadoConsulta(EstadoConsulta.EN_COLA);
            return new TiempoEstimadoAtencionResponse();
        });

        var dto = service.finalizar("auth", 7L, formulario);

        assertEquals(EstadoAdmisionRecepcion.FINALIZADA, admision.getEstado());
        assertEquals(NivelDeGravedad.URGENTE, dto.prioridad());
        verify(ingresoColaService).ingresar(consulta, NivelDeGravedad.URGENTE);
    }
}
