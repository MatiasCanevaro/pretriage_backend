package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.GuardarSalaRequest;
import com.pretriage.backend.exceptions.ConflictoDeEstadoException;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoAuditoriasHospital;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoSalas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalConfigurationServiceTest {
    @Mock StaffAccessService staffAccessService;
    @Mock RepoHospitales hospitales;
    @Mock RepoEspecialidadesMedicas especialidades;
    @Mock RepoSalas salas;
    @Mock RepoAuditoriasHospital auditorias;

    private HospitalConfigurationService service;
    private Hospital hospital;
    private EspecialidadMedica especialidad;
    private UsuarioAuth actor;

    @BeforeEach
    void setUp() {
        service = new HospitalConfigurationService(staffAccessService, hospitales, especialidades, salas, auditorias);
        hospital = new Hospital();
        hospital.setId(7L);
        hospital.setNombre("Hospital Escuela");
        especialidad = new EspecialidadMedica();
        especialidad.setId(4L);
        especialidad.setCodigo("CLINICA_MEDICA");
        especialidad.setNombre("Clínica médica");
        hospital.getEspecialidades().add(especialidad);
        actor = new UsuarioAuth();
        actor.setId("auth0|admin");
        when(staffAccessService.exigirAdminHospital(actor.getId(), hospital.getId())).thenReturn(actor);
        when(hospitales.findById(hospital.getId())).thenReturn(Optional.of(hospital));
    }

    @Test
    void creaUnaSalaParaUnaEspecialidadHabilitada() {
        when(salas.existsByHospitalIdAndNombreIgnoreCase(hospital.getId(), "Consultorio 1")).thenReturn(false);
        when(salas.save(any(Sala.class))).thenAnswer(invocation -> {
            Sala sala = invocation.getArgument(0);
            sala.setId(12L);
            return sala;
        });

        var response = service.crearSala(actor.getId(), hospital.getId(),
                new GuardarSalaRequest(" Consultorio 1 ", especialidad.getId()));

        assertEquals(12L, response.id());
        assertEquals("Consultorio 1", response.nombre());
        assertEquals(especialidad.getId(), response.especialidadId());
        assertTrue(response.activa());
        verify(auditorias).save(any());
    }

    @Test
    void noDeshabilitaUnaEspecialidadConSalasActivas() {
        when(especialidades.findById(especialidad.getId())).thenReturn(Optional.of(especialidad));
        when(salas.existsByHospitalIdAndEspecialidadIdAndActivaTrue(hospital.getId(), especialidad.getId()))
                .thenReturn(true);

        ConflictoDeEstadoException error = assertThrows(ConflictoDeEstadoException.class,
                () -> service.deshabilitarEspecialidad(actor.getId(), hospital.getId(), especialidad.getId()));

        assertEquals("Desactivá las salas de la especialidad antes de quitarla del hospital", error.getMessage());
        verify(hospitales, never()).save(any());
    }
}
