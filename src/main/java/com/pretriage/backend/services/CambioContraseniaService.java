package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.LimiteSolicitudesCambioContraseniaException;
import com.pretriage.backend.exceptions.NoSePudoCambiarContraseniaException;
import com.pretriage.backend.exceptions.TokenCambioContraseniaInvalidoException;
import com.pretriage.backend.model.personas.CambioContraseniaToken;
import com.pretriage.backend.model.personas.EstadoCambioContrasenia;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoCambioContraseniaToken;
import com.pretriage.backend.repositories.RepoUsuariosAuth;
import com.pretriage.backend.services.ports.PasswordResetEmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CambioContraseniaService {

    private static final String MENSAJE_GENERICO = "Si el correo existe, le enviamos un token al correo indicado, por favor, revise su mail e ingrese el token";
    private static final String REALM_NAME = "Username-Password-Authentication";

    private final RepoUsuariosAuth repoUsuariosAuth;
    private final RepoCambioContraseniaToken repoCambioContraseniaToken;
    private final TokenService tokenService;
    private final PasswordResetEmailPort passwordResetEmailPort;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${auth0.base-path}")
    private String auth0BasePath;

    @Value("${auth0.client-id.machine-to-machine}")
    private String auth0M2mClientId;

    @Value("${auth0.client-secret.machine-to-machine}")
    private String auth0M2mClientSecret;

    @Value("${auth0.scope.machine-to-machine}")
    private String auth0M2mScope;

    @Value("${pretriage.cambio-contrasenia.expiracion-minutos:15}")
    private long expiracionMinutos;

    @Value("${pretriage.cambio-contrasenia.max-solicitudes-por-hora:3}")
    private int maxSolicitudesPorHora;

    @Value("${pretriage.cambio-contrasenia.ventana-horas:1}")
    private long ventanaHoras;

    // --- API pública según diagrama ---

    @Transactional
    public String obtenerTokenCambioContraseña(String email) {
        String emailNormalizado = normalizarEmail(email);
        Optional<UsuarioAuth> usuarioOpt = repoUsuariosAuth.findByCorreoElectronicoIgnoreCase(emailNormalizado);

        // Privacidad: siempre devolver mensaje genérico para no enumerar usuarios
        if (usuarioOpt.isEmpty()) {
            log.info("Solicitud de token para email no existente: {}", emailNormalizado);
            return MENSAJE_GENERICO;
        }

        UsuarioAuth usuario = usuarioOpt.get();

        // rate limit
        if (superaLimiteCambioContrasenia(usuario.getId())) {
            throw new LimiteSolicitudesCambioContraseniaException();
        }

        // invalidar tokens pendientes previos
        invalidarTokensPendientesPrevios(usuario.getId());

        String token = tokenService.generarToken();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime expiracion = ahora.plusMinutes(expiracionMinutos);

        CambioContraseniaToken nuevo = new CambioContraseniaToken(usuario, token, ahora, expiracion);
        repoCambioContraseniaToken.save(nuevo);

        // envío de email (no bloquea la transacción con excepción)
        try {
            passwordResetEmailPort.enviarToken(emailNormalizado, token, expiracion);
        } catch (Exception e) {
            log.error("Error al enviar email de restablecimiento a {}", emailNormalizado, e);
        }

        return MENSAJE_GENERICO;
    }

    @Transactional(readOnly = true)
    public void validarTokenCambioContrasenia(String token) {
        CambioContraseniaToken cambio = obtenerCambioContraseniaToken(token);
        validarEstadoYExpiracion(cambio);
    }

    @Transactional
    public void cambiarContraseña(String nuevaPass, String tokenCambioContrasenia) {
        CambioContraseniaToken cambio = obtenerCambioContraseniaToken(tokenCambioContrasenia);
        validarEstadoYExpiracion(cambio);

        UsuarioAuth usuario = cambio.getUsuario();
        String auth0Id = usuario.getId();

        llamarApiCambioContrasenia(auth0Id, nuevaPass);

        // marcar como cambiado
        cambio.setEstado(EstadoCambioContrasenia.CAMBIADO);
        repoCambioContraseniaToken.save(cambio);

        // invalidar otros pendientes del usuario (por si quedaron)
        invalidarOtrosPendientes(usuario.getId(), cambio.getId());

        // intentar invalidar sesiones / refresh tokens en Auth0 (best-effort)
        intentarInvalidarTokensAuth0(auth0Id);
    }

    // --- helpers privados ---

    private boolean superaLimiteCambioContrasenia(String usuarioId) {
        LocalDateTime desde = LocalDateTime.now().minusHours(ventanaHoras);
        long totalRecientes = repoCambioContraseniaToken
                .countByUsuarioIdAndFechaHoraCreacionAfter(usuarioId, desde);
        return totalRecientes >= maxSolicitudesPorHora;
    }

    private void validarEstadoYExpiracion(CambioContraseniaToken cambio) {
        if (cambio.getEstado() != EstadoCambioContrasenia.PENDIENTE) {
            throw new TokenCambioContraseniaInvalidoException();
        }
        if (cambio.expiro()) {
            cambio.setEstado(EstadoCambioContrasenia.EXPIRO);
            repoCambioContraseniaToken.save(cambio);
            throw new TokenCambioContraseniaInvalidoException();
        }
    }

    private CambioContraseniaToken obtenerCambioContraseniaToken(String token) {
        return repoCambioContraseniaToken.findByToken(token)
                .orElseThrow(TokenCambioContraseniaInvalidoException::new);
    }

    private void invalidarTokensPendientesPrevios(String usuarioId) {
        List<CambioContraseniaToken> pendientes = repoCambioContraseniaToken
                .findByUsuarioIdAndEstado(usuarioId, EstadoCambioContrasenia.PENDIENTE);
        for (CambioContraseniaToken t : pendientes) {
            t.setEstado(EstadoCambioContrasenia.INVALIDADO);
        }
        if (!pendientes.isEmpty()) {
            repoCambioContraseniaToken.saveAll(pendientes);
        }
    }

    private void invalidarOtrosPendientes(String usuarioId, Long exceptoId) {
        List<CambioContraseniaToken> pendientes = repoCambioContraseniaToken
                .findByUsuarioIdAndEstado(usuarioId, EstadoCambioContrasenia.PENDIENTE);
        for (CambioContraseniaToken t : pendientes) {
            if (!t.getId().equals(exceptoId)) {
                t.setEstado(EstadoCambioContrasenia.INVALIDADO);
            }
        }
        if (!pendientes.isEmpty()) {
            repoCambioContraseniaToken.saveAll(pendientes);
        }
    }

    private void llamarApiCambioContrasenia(String auth0Id, String nuevaPass) {
        try {
            String m2mToken = obtenerTokenM2M();

            // Auth0 Management API PATCH /api/v2/users/{id}
            Map<String, String> body = Map.of(
                    "password", nuevaPass,
                    "connection", REALM_NAME);

            restClient.patch()
                    .uri(auth0BasePath + "/api/v2/users/" + auth0Id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + m2mToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Contraseña cambiada en Auth0 para {}", auth0Id);

        } catch (RestClientResponseException e) {
            log.error("Auth0 PATCH password falló para {} con status {}", auth0Id, e.getStatusCode(), e);
            String mensaje = mensajeSeguroAuth0(e);
            throw new NoSePudoCambiarContraseniaException(mensaje);
        } catch (NoSePudoCambiarContraseniaException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al cambiar contraseña Auth0 para {}", auth0Id, e);
            throw new NoSePudoCambiarContraseniaException();
        }
    }

    private void intentarInvalidarTokensAuth0(String auth0Id) {
        // Según Auth0 docs, el cambio de password via Management API ya invalida la
        // sesión (cookie)
        // pero los refresh tokens permanecen válidos y deben revocarse explícitamente.
        // Intentamos best-effort revocar grants/refresh tokens.
        try {
            String m2mToken = obtenerTokenM2M();
            // Intento 1: DELETE /api/v2/grants?user_id=...
            try {
                restClient.delete()
                        .uri(auth0BasePath + "/api/v2/grants?user_id=" + auth0Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + m2mToken)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Grants revocados para {}", auth0Id);
                return;
            } catch (Exception ex) {
                log.debug("DELETE /grants falló para {}, probando alternativa: {}", auth0Id, ex.getMessage());
            }

            // Intento 2: POST /oauth/revoke no disponible sin token, intentamos DELETE
            // users refresh-tokens (si existe)
            // No fallar si no existe el endpoint
            try {
                restClient.delete()
                        .uri(auth0BasePath + "/api/v2/users/" + auth0Id + "/refresh-tokens")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + m2mToken)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Refresh tokens revocados via /users/refresh-tokens para {}", auth0Id);
            } catch (Exception ex2) {
                log.debug("DELETE /users/refresh-tokens no disponible o falló para {}: {}", auth0Id, ex2.getMessage());
            }

        } catch (Exception e) {
            log.warn("No se pudieron invalidar tokens Auth0 para {} tras cambio de contraseña (no crítico): {}",
                    auth0Id, e.getMessage());
        }
    }

    private String obtenerTokenM2M() {
        Map<String, String> bodyRequest = Map.of(
                "client_id", auth0M2mClientId,
                "client_secret", auth0M2mClientSecret,
                "audience", auth0BasePath + "/api/v2/",
                "scope", auth0M2mScope,
                "grant_type", "client_credentials");

        String responseJson = restClient.post()
                .uri(auth0BasePath + "/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyRequest)
                .retrieve()
                .body(String.class);

        if (responseJson == null) {
            throw new NoSePudoCambiarContraseniaException("No se pudo obtener token M2M de Auth0");
        }

        try {
            // Reusa formato AuthTokenResponse pero parse manual para evitar dependencia
            // circular
            Map<?, ?> map = objectMapper.readValue(responseJson, Map.class);
            Object access = map.get("access_token");
            if (access == null) {
                throw new NoSePudoCambiarContraseniaException();
            }
            return access.toString();
        } catch (NoSePudoCambiarContraseniaException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parseando token M2M", e);
            throw new NoSePudoCambiarContraseniaException();
        }
    }

    private String mensajeSeguroAuth0(RestClientResponseException exception) {
        String response = "";
        try {
            response = exception.getResponseBodyAsString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
        }

        if (response.contains("passwordstrengtherror") || response.contains("password is too weak")) {
            return "La contraseña es demasiado débil. Usá mayúsculas, minúsculas, números y símbolos.";
        }
        if (response.contains("password is too common") || response.contains("passwordistoocommon")) {
            return "La contraseña es demasiado común. Elegí otra más segura.";
        }
        if (response.contains("user not found") || response.contains("404")) {
            return "Usuario no encontrado en Auth0.";
        }
        return "No se pudo cambiar la contraseña. Revisá la política de contraseñas e intentá nuevamente.";
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
