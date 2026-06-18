package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.RegisterRequest;
import com.pretriage.backend.controllers.dtos.auth0.AuthRegisterTokenYUserId;
import com.pretriage.backend.model.personas.Medico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.model.personas.Recepcionista;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoMedico;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.repositories.RepoRecepcionistas;
import com.pretriage.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class AuthController {

    private AuthService authService;

    private RepoRecepcionistas repoRecepcionistas;
    private RepoMedico repoMedico;
    private RepoPacientes repoPacientes;

    @PostMapping("")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request){


        AuthRegisterTokenYUserId tokenYUserId = authService.registrarUsuarioYObtenerToken(request.getEmail(), request.getPassword());

        String auth0Id = tokenYUserId.getAuth0Id();
        String email = request.getEmail();

        switch (request.getTipoUsuario()){
            case Medico -> this.crearMedico(request, email, auth0Id);
            case Paciente -> this.crearPaciente(request, email, auth0Id);
            case Recepcionista -> this.crearRecepcionista(request, email, auth0Id);
        }

        return ResponseEntity.ok(Map.of("token", tokenYUserId.getToken()));
    }

    private UsuarioAuth crearUsuario(RegisterRequest request, String email, String auth0Id){
        UsuarioAuth usuarioAuth = new UsuarioAuth();

        usuarioAuth.setNombre(request.getNombre());

        usuarioAuth.setApellido(request.getApellido());

        usuarioAuth.setNumeroDocumento(request.getNumeroDocumento());

        usuarioAuth.setTipoDocumento(request.getTipoDocumento());

        usuarioAuth.setCorreoElectronico(email);

        usuarioAuth.setId(auth0Id);

        return usuarioAuth;
    }

    private void crearRecepcionista(RegisterRequest request, String email, String auth0Id) {
        Recepcionista recepcionista = new Recepcionista();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id);

        recepcionista.setUsuarioAuth(usuarioAuth);

        repoRecepcionistas.save(recepcionista);
    }

    private void crearPaciente(RegisterRequest request, String email, String auth0Id) {
        Paciente paciente = new Paciente();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id);

        paciente.setUsuarioAuth(usuarioAuth);

        repoPacientes.save(paciente);
    }

    private void crearMedico(RegisterRequest request, String email, String auth0Id){
        Medico medico = new Medico();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id);

        medico.setMatricula(
                request.getMatricula());

        medico.setUsuarioAuth(usuarioAuth);

        repoMedico.save(medico);
    }
}
