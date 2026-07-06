package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.LoginRequest;
import com.pretriage.backend.controllers.dtos.RegisterRequest;
import com.pretriage.backend.model.personas.*;
import com.pretriage.backend.repositories.RepoMedico;
import com.pretriage.backend.repositories.RepoPacientes;
import com.pretriage.backend.repositories.RepoRecepcionistas;
import com.pretriage.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final RepoRecepcionistas repoRecepcionistas;
    private final RepoMedico repoMedico;
    private final RepoPacientes repoPacientes;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request){


        String auth0Id = authService.registrarUsuarioYObtenerAuth0Id(request.getEmail(), request.getPassword());

        String email = request.getEmail();

        switch (request.getTipoUsuario()){
            case Medico -> this.crearMedico(request, email, auth0Id);
            case Paciente -> this.crearPaciente(request, email, auth0Id);
            case Recepcionista -> this.crearRecepcionista(request, email, auth0Id, request.getRol());//solo el recepcionista puede ser o no admin
        }

        return ResponseEntity.ok(Map.of("message", "usuario creado con éxito"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request){

        String idToken = authService.obtenerTokenParaLogearUsuario(request.getEmail(), request.getPassword());

        return ResponseEntity.ok(Map.of("token", idToken));
    }

    private UsuarioAuth crearUsuario(RegisterRequest request, String email, String auth0Id, RolSistema rol){
        UsuarioAuth usuarioAuth = new UsuarioAuth();

        usuarioAuth.setNombre(request.getNombre());

        usuarioAuth.setApellido(request.getApellido());

        usuarioAuth.setNumeroDocumento(request.getNumeroDocumento());

        usuarioAuth.setTipoDocumento(request.getTipoDocumento());

        usuarioAuth.setCorreoElectronico(email);

        usuarioAuth.setId(auth0Id);

        usuarioAuth.setRol(rol);

        return usuarioAuth;
    }

    private void crearRecepcionista(RegisterRequest request, String email, String auth0Id, RolSistema rol) {
        Recepcionista recepcionista = new Recepcionista();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id, rol);

        recepcionista.setUsuarioAuth(usuarioAuth);

        repoRecepcionistas.save(recepcionista);
    }

    private void crearPaciente(RegisterRequest request, String email, String auth0Id) {
        Paciente paciente = new Paciente();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id, RolSistema.USER);

        paciente.setUsuarioAuth(usuarioAuth);

        repoPacientes.save(paciente);
    }

    private void crearMedico(RegisterRequest request, String email, String auth0Id){
        Medico medico = new Medico();

        UsuarioAuth usuarioAuth = this.crearUsuario(request, email, auth0Id, RolSistema.USER);

        medico.setMatricula(
                request.getMatricula());

        medico.setUsuarioAuth(usuarioAuth);

        repoMedico.save(medico);
    }
}
