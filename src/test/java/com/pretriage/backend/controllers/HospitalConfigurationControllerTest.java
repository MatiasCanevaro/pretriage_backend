package com.pretriage.backend.controllers;

import com.pretriage.backend.config.SpringSecurityConfig;
import com.pretriage.backend.controllers.dtos.acceso.HospitalConfigurationDtos.ConfiguracionHospitalResponse;
import com.pretriage.backend.exceptions.ConflictoDeEstadoException;
import com.pretriage.backend.repositories.RepoMedico;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.repositories.RepoRecepcionistas;
import com.pretriage.backend.services.HospitalConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HospitalConfigurationController.class,
        properties = "spring.docker.compose.enabled=false")
@Import(SpringSecurityConfig.class)
class HospitalConfigurationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HospitalConfigurationService service;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean RepoRecepcionistas repoRecepcionistas;
    @MockitoBean RepoMedico repoMedico;
    @MockitoBean RepoPacientes repoPacientes;

    @Test
    void deshabilitaEspecialidadDelHospitalSinSectorYDevuelveConfiguracion() throws Exception {
        when(service.deshabilitarEspecialidad("admin", 7L, 4L))
                .thenReturn(new ConfiguracionHospitalResponse(List.of(), List.of(), List.of()));

        mvc.perform(delete("/api/admin/hospitales/7/configuracion/especialidades/4")
                        .with(jwt().jwt(token -> token.subject("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades").isArray())
                .andExpect(jsonPath("$.salas").isArray())
                .andExpect(jsonPath("$.sectores").isArray());

        verify(service).deshabilitarEspecialidad("admin", 7L, 4L);
    }

    @Test
    void salasActivasEnHospitalDevuelvenConflicto() throws Exception {
        when(service.deshabilitarEspecialidad("admin", 7L, 4L))
                .thenThrow(new ConflictoDeEstadoException("Desactivá las salas de la especialidad"));

        mvc.perform(delete("/api/admin/hospitales/7/configuracion/especialidades/4")
                        .with(jwt().jwt(token -> token.subject("admin"))))
                .andExpect(status().isConflict());
    }

    @Test
    void bajaRequiereAutenticacion() throws Exception {
        mvc.perform(delete("/api/admin/hospitales/7/configuracion/especialidades/4"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rutaAnteriorConSectorYaNoExiste() throws Exception {
        mvc.perform(delete("/api/admin/hospitales/7/configuracion/sectores/2/especialidades/4")
                        .with(jwt().jwt(token -> token.subject("admin"))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
