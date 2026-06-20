package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.auth0.AuthTokenResponse;
import com.pretriage.backend.controllers.dtos.auth0.AuthUserDetailsResponse;
import com.pretriage.backend.exceptions.NoSePudoCrearUsuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${auth0.base-path}")
    private String AUTH0_BASE_PATH ;

    @Value("${auth0.client-id.machine-to-machine}")
    private String AUTH0_M2M_CLIENT_ID;

    @Value("${auth0.client-secret.machine-to-machine}")
    private String AUTH0_M2M_CLIENT_SECRET;

    @Value("${auth0.scope.machine-to-machine}")
    private String AUTH0_M2M_SCOPE;

    @Value("${auth0.client-id.app}")
    private String AUTH0_APP_CLIENT_ID;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    //contantes para evitar repetición de strings

    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final String CONNECTION_FIELD = "connection";
    private static final String CLIENT_ID_FIELD="client_id";
    private static final String AUDIENCE_FIELD="audience";
    private static final String SCOPE_FIELD="scope";
    private static final String GRANT_TYPE_FIELD ="grant_type";

    private static final String CONTENT_TYPE_HEADER_FIELD = "Content-Type";
    private static final String CONTENT_TYPE_APPLICATION_JSON= "application/json";
    private static final String REALM_NAME= "Username-Password-Authentication";


    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();



    public String registrarUsuarioYObtenerAuth0Id(String email, String password) {
            String tokenParaCrearUsuario = this.obtenerTokenParaCrearUsuario();

            Map<String, String> bodyRequest = Map.of(
                    USERNAME_FIELD,email,
                    PASSWORD_FIELD, password,
                    CONNECTION_FIELD, REALM_NAME
            );

            String responseUserDetails = this.llamarApiToken(bodyRequest,
                    AUTH0_BASE_PATH+"/api/v2/users",
                    "Authorization",
                    "Bearer: "+tokenParaCrearUsuario
                    );

        AuthUserDetailsResponse userDetailsNuevo = objectMapper.readValue(responseUserDetails, AuthUserDetailsResponse.class);

        return userDetailsNuevo.getUserId();
    }

    public String obtenerTokenParaLogearUsuario(String email, String password){
        Map<String, String> bodyTokenRequest =  Map.of(
                GRANT_TYPE_FIELD, "http://auth0.com/oauth/grant-type/password-realm",
                USERNAME_FIELD, email,
                PASSWORD_FIELD, password,
                AUDIENCE_FIELD, "http://localhost:8080",
                SCOPE_FIELD, "openid profile email",
                CLIENT_ID_FIELD, AUTH0_APP_CLIENT_ID,
                "realm", REALM_NAME
        );
        return this.llamarApiToken(bodyTokenRequest,
                AUTH0_BASE_PATH+"/oauth/token",
                CONTENT_TYPE_HEADER_FIELD,
                CONTENT_TYPE_APPLICATION_JSON);
    }

    private String obtenerTokenParaCrearUsuario() {
        Map<String, String> bodyRequest = Map.of(
                CLIENT_ID_FIELD, AUTH0_M2M_CLIENT_ID,
                "client_secret", AUTH0_M2M_CLIENT_SECRET,
                AUDIENCE_FIELD, AUTH0_BASE_PATH+"/api/v2",
                SCOPE_FIELD,AUTH0_M2M_SCOPE,
                GRANT_TYPE_FIELD, "client_credentials"
        );

        String responseJson = this.llamarApiToken(
                bodyRequest,
                AUTH0_BASE_PATH +"/oauth/token",
                CONTENT_TYPE_HEADER_FIELD,
                CONTENT_TYPE_APPLICATION_JSON
        );

        AuthTokenResponse response = objectMapper.readValue(responseJson, AuthTokenResponse.class);

        return response.getAccessToken();
    }

    private String llamarApiToken(Map<String, String> bodyRequest,
                                  String uriPath,
                                  String headerType,
                                  String headerValue){
        try{

            String jsonBody = new ObjectMapper().writeValueAsString(bodyRequest);//parseo a json

            String responseJson = restClient.post()
                    .uri(uriPath)
                    .header(headerType,headerValue)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            if(responseJson ==null){
                log.warn("no se pudo crear el usuario");
                throw new NoSePudoCrearUsuario();
            }

            return responseJson;

        } catch (Exception e){
            log.error("Error al crear el usuario con Auth0 API: {}", e.getMessage(), e);
            throw new NoSePudoCrearUsuario();
        }
    }
}
