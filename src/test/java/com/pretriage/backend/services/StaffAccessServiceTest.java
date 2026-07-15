package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.acceso.StaffAccessDtos.ActualizarMembresiaRequest;
import com.pretriage.backend.controllers.dtos.acceso.StaffAccessDtos.CrearInvitacionRequest;
import com.pretriage.backend.exceptions.ConflictoDeEstadoException;
import com.pretriage.backend.model.acceso.*;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.personas.RolSistema;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.model.personas.TipoMatriculaProfesional;
import com.pretriage.backend.model.personas.CredencialProfesional;
import com.pretriage.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffAccessServiceTest {
    @Mock RepoUsuariosAuth usuarios;
    @Mock RepoHospitales hospitales;
    @Mock RepoMembresiasHospital membresias;
    @Mock RepoInvitacionesHospital invitaciones;
    @Mock RepoAuditoriasHospital auditorias;
    @Mock RepoRecepcionistas recepcionistas;
    @Mock RepoMedico medicos;
    @Mock RepoAsignacionesMedicoHospital asignaciones;
    @Mock RepoEspecialidadesMedicas especialidades;
    @Mock RepoCredencialesProfesionales credencialesProfesionales;
    @Mock AuthService authService;

    private StaffAccessService service;
    private UsuarioAuth admin;
    private Hospital hospital;
    private MembresiaHospital membresiaAdmin;

    @BeforeEach
    void setUp() {
        service = new StaffAccessService(usuarios, hospitales, membresias, invitaciones, auditorias,
                recepcionistas, medicos, asignaciones, especialidades, credencialesProfesionales, authService);
        admin = new UsuarioAuth();
        admin.setId("auth0|admin");
        admin.setNombre("Ada");
        admin.setApellido("Admin");
        admin.setCorreoElectronico("admin@example.com");
        admin.setRol(RolSistema.USER);
        hospital = new Hospital();
        hospital.setId(7L);
        hospital.setNombre("Hospital Escuela");
        membresiaAdmin = new MembresiaHospital();
        membresiaAdmin.setId(3L);
        membresiaAdmin.setUsuario(admin);
        membresiaAdmin.setHospital(hospital);
        membresiaAdmin.setEstado(EstadoMembresiaHospital.ACTIVA);
        membresiaAdmin.setRoles(new LinkedHashSet<>(Set.of(RolMembresiaHospital.ADMIN_HOSPITAL)));

        when(usuarios.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(recepcionistas.findRecepcionistaByUsuarioAuthId(admin.getId())).thenReturn(Optional.empty());
        when(asignaciones.findByMedicoUsuarioAuthId(admin.getId())).thenReturn(java.util.List.of());
        when(membresias.findByUsuarioIdAndHospitalId(admin.getId(), hospital.getId()))
                .thenReturn(Optional.of(membresiaAdmin));
    }

    @Test
    void entregaElSecretoUnaVezYGuardaSoloSuHash() {
        when(hospitales.findById(hospital.getId())).thenReturn(Optional.of(hospital));
        when(invitaciones.existsByHospitalIdAndEmailNormalizadoAndEstado(
                eq(hospital.getId()), eq("staff@example.com"), eq(EstadoInvitacionHospital.PENDIENTE)))
                .thenReturn(false);
        when(invitaciones.save(any())).thenAnswer(invocation -> {
            InvitacionHospital saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        var response = service.crearInvitacion(admin.getId(), hospital.getId(),
                new CrearInvitacionRequest(" Staff@Example.com ",
                        Set.of(RolMembresiaHospital.RECEPCIONISTA), null, null, null, Set.of()));

        ArgumentCaptor<InvitacionHospital> captor = ArgumentCaptor.forClass(InvitacionHospital.class);
        verify(invitaciones).save(captor.capture());
        assertNotNull(response.tokenEntregaUnica());
        assertEquals(43, response.tokenEntregaUnica().length());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertNotEquals(response.tokenEntregaUnica(), captor.getValue().getTokenHash());
        assertEquals("staff@example.com", captor.getValue().getEmailNormalizado());
        assertEquals(Boolean.FALSE, response.emailEnviado());
    }

    @Test
    void impideSuspenderAlUltimoAdministradorActivo() {
        when(membresias.findById(membresiaAdmin.getId())).thenReturn(Optional.of(membresiaAdmin));
        when(membresias.countByHospitalIdAndEstadoAndRolesContaining(hospital.getId(),
                EstadoMembresiaHospital.ACTIVA, RolMembresiaHospital.ADMIN_HOSPITAL)).thenReturn(1L);

        assertThrows(ConflictoDeEstadoException.class, () -> service.actualizarEstado(
                admin.getId(), hospital.getId(), membresiaAdmin.getId(),
                new ActualizarMembresiaRequest(EstadoMembresiaHospital.SUSPENDIDA)));
        verify(membresias, never()).save(membresiaAdmin);
    }

    @Test
    void normalizaLaJurisdiccionDeUnaMatriculaNacional() {
        EspecialidadMedica clinica = new EspecialidadMedica();
        clinica.setId(1L);
        clinica.setCodigo("CLINICA_MEDICA");
        hospital.setEspecialidades(java.util.List.of(clinica));
        when(hospitales.findById(hospital.getId())).thenReturn(Optional.of(hospital));
        when(invitaciones.existsByHospitalIdAndEmailNormalizadoAndEstado(any(), any(), any()))
                .thenReturn(false);
        when(invitaciones.save(any())).thenAnswer(invocation -> {
            InvitacionHospital saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        var response = service.crearInvitacion(admin.getId(), hospital.getId(),
                new CrearInvitacionRequest("medico@example.com", Set.of(RolMembresiaHospital.MEDICO),
                        "11111111", TipoMatriculaProfesional.NACIONAL, "Buenos Aires", Set.of(1L)));

        assertEquals(TipoMatriculaProfesional.NACIONAL, response.tipoMatricula());
        assertEquals(CredencialProfesional.JURISDICCION_NACIONAL, response.jurisdiccionMatricula());
    }
}
