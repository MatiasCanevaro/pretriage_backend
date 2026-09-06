package com.pretriage.backend.controllers;

import com.pretriage.backend.config.SpringSecurityConfig;
import com.pretriage.backend.services.SalaAtencionNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalaAtencionController.class)
@Import(SpringSecurityConfig.class)
class SalaAtencionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SalaAtencionNotifier notifier;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void suscribirse_autenticadoDueño_200() throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        when(notifier.suscribirse(any(), eq(5L))).thenReturn(emitter);

        mockMvc.perform(get("/api/atencion/sala/suscribirse/5")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("auth0"))))
                .andExpect(status().isOk());

        verify(notifier).suscribirse("auth0", 5L);
    }

    @Test
    void suscribirse_noDueño_403() throws Exception {
        when(notifier.suscribirse(any(), eq(5L))).thenThrow(new AccessDeniedException("No tiene permisos sobre la consulta"));

        mockMvc.perform(get("/api/atencion/sala/suscribirse/5")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("auth0"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void suscribirse_sinJwt_401() throws Exception {
        mockMvc.perform(get("/api/atencion/sala/suscribirse/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void desuscribirse_autenticadoDueño_204() throws Exception {
        mockMvc.perform(get("/api/atencion/sala/desuscribirse/5")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("auth0"))))
                .andExpect(status().isNoContent());

        verify(notifier).desuscribirse("auth0", 5L);
    }

    @Test
    void desuscribirse_noDueño_403() throws Exception {
        doThrow(new AccessDeniedException("No tiene permisos sobre la consulta"))
                .when(notifier).desuscribirse(any(), eq(5L));

        mockMvc.perform(get("/api/atencion/sala/desuscribirse/5")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("auth0"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void desuscribirse_sinJwt_401() throws Exception {
        mockMvc.perform(get("/api/atencion/sala/desuscribirse/5"))
                .andExpect(status().isUnauthorized());
    }
}
