package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.RegisterRequest;
import com.pretriage.backend.controllers.dtos.TipoUsuario;
import com.pretriage.backend.model.personas.TipoDocumento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerTests {

    @LocalServerPort
    private int port;

    @Value("${auth0.username.test}")
    private String username;
    @Value("${auth0.password.test}")
    private String password;
    @Value("${auth0.client-id.test}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void deberiaRegistrarUnPaciente() {

        String token = obtenerTokenAuth0();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        RegisterRequest request = new RegisterRequest();
        request.setNombre("Franco");
        request.setApellido("Callero");
        request.setNumeroDocumento("12345678");
        request.setTipoDocumento(TipoDocumento.DNI);
        request.setTipoUsuario(TipoUsuario.Paciente);

        HttpEntity<RegisterRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<Void> response =
                restTemplate.exchange(
                        "http://localhost:" + port + "/api/register",
                        HttpMethod.POST,
                        entity,
                        Void.class
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String obtenerTokenAuth0() {

        String dominio =
                "https://dev-bktuk8spi6fy0cal.us.auth0.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();

        body.put(
                "grant_type",
                "http://auth0.com/oauth/grant-type/password-realm");

        body.put(
                "username",
                username);

        body.put(
                "password",
                password);

        body.put(
                "realm",
                "Username-Password-Authentication");

        body.put(
                "audience",
                "http://localhost:8080");

        body.put(
                "scope",
                "openid profile email");

        body.put(
                "client_id",
                clientId);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        dominio + "/oauth/token",
                        HttpMethod.POST,
                        request,
                        Map.class
                );

        return (String) response.getBody().get("id_token");
    }
}
