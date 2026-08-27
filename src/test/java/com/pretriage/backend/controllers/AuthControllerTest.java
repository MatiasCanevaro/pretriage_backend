package com.pretriage.backend.controllers;

import com.pretriage.backend.config.SpringSecurityConfig;
import com.pretriage.backend.controllers.dtos.LoginResponseDTO;
import com.pretriage.backend.exceptions.RefreshTokenInvalidoException;
import com.pretriage.backend.repositories.RepoMedico;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.repositories.RepoRecepcionistas;
import com.pretriage.backend.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SpringSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RepoRecepcionistas repoRecepcionistas;

    @MockitoBean
    private RepoMedico repoMedico;

    @MockitoBean
    private RepoPacientes repoPacientes;

    @Test
    void renovar_retorna200ConTokenRotado() throws Exception {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setToken("newAccessToken");
        dto.setRefreshToken("newRefreshToken");
        dto.setRenovarTokenEn(86400L);

        when(authService.renovarTokenUsuario("oldRefreshToken")).thenReturn(dto);

        String body = """
                {"refreshToken":"oldRefreshToken"}
                """;

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("newAccessToken"))
                .andExpect(jsonPath("$.refreshToken").value("newRefreshToken"))
                .andExpect(jsonPath("$.renovarTokenEn").value(86400));
    }

    @Test
    void renovar_esPublico_noRequiereJwt() throws Exception {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setToken("token");
        dto.setRefreshToken("newRefresh");
        dto.setRenovarTokenEn(86400L);
        when(authService.renovarTokenUsuario("valid")).thenReturn(dto);

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void renovar_conRefreshTokenVacio_retorna400() throws Exception {
        String body = """
                {"refreshToken":""}
                """;

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void renovar_conRefreshTokenNull_retorna400() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void renovar_conRefreshTokenSoloEspacios_retorna400() throws Exception {
        String body = """
                {"refreshToken":"   "}
                """;

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renovar_conRefreshInvalido_retorna401() throws Exception {
        when(authService.renovarTokenUsuario("invalid")).thenThrow(new RefreshTokenInvalidoException());

        mockMvc.perform(post("/api/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("El refresh token es inválido o expiró. Iniciá sesión nuevamente."));
    }

    @Test
    void renovar_conBodySinContentType_retorna400O415() throws Exception {
        // sin contentType el endpoint no matchea JSON -> 400/415 dependiendo de validación
        mockMvc.perform(post("/api/renovar")
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().is4xxClientError());
    }
}
