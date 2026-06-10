package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.exceptions.CredencialValidaYaExisteException;
import com.pretriage.backend.exceptions.PacienteNoExisteException;
import com.pretriage.backend.model.hospitales.Credencial;
import com.pretriage.backend.model.hospitales.ObraSocial;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CredencialService {

    private final RepoPacientes repoPacientes;
    private final RepoRecepcionistas repoRecepcionistas;
    private final RepoObraSociales repoObraSociales;
    private final RepoCredenciales repoCredenciales;

    public void cargarCredencialPaciente(
            String auth0IdPaciente,
            CredencialRequest request) {

        Paciente paciente = obtenerPaciente(auth0IdPaciente);

        cargarCredencial(request,paciente);
    }


    public void cargarCredencialRecepcionista(String auth0IdRecepcionista, Long idPaciente, @Valid CredencialRequest request) {

        verificarSiEsRecepcionista(auth0IdRecepcionista);

        Optional<Paciente> opPaciente = repoPacientes.findById(idPaciente);

        if(opPaciente.isEmpty()){
            throw new PacienteNoExisteException();
        }

        Paciente paciente = opPaciente.get();

        cargarCredencial(request,paciente);
    }

    private Paciente obtenerPaciente(String auth0IdPaciente) {

        Optional<Paciente> opPaciente = repoPacientes
                .findByUsuarioAuthId(auth0IdPaciente);
        if(opPaciente.isEmpty()){
            throw new AccessDeniedException(
                    "No tiene permisos para cargar la credencial");
        } else {
            return opPaciente.get();
        }
    }


    private void verificarSiEsRecepcionista(String auth0IdRecepcionista) {

        boolean esRecepcionista = repoRecepcionistas
                .existsByUsuarioAuthId(auth0IdRecepcionista);
        if(!esRecepcionista){
            throw new AccessDeniedException(
                    "No tiene permisos para cargar la credencial");
        }
    }

    private ObraSocial obtenerOCrearObraSocial(String nombre) {

        return repoObraSociales
                .findByNombreEqualsIgnoreCase(nombre)
                .orElseGet(() -> {

                    ObraSocial obraSocial = new ObraSocial();
                    obraSocial.setNombre(nombre);

                    return repoObraSociales.save(obraSocial);
                });
    }

    private void verificarCredencialVigente(Paciente paciente) {

        boolean existeCredencialVigente =
                repoCredenciales
                        .existsByPacienteIdAndFechaVencimientoGreaterThanEqual(
                                paciente.getId(),
                                LocalDate.now());

        if(existeCredencialVigente){
            throw new CredencialValidaYaExisteException();
        }
    }


    private void cargarCredencial(CredencialRequest request, Paciente paciente){
        ObraSocial obraSocial = obtenerOCrearObraSocial(
                request.getNombreObraSocial());

        verificarCredencialVigente(paciente);

        Credencial credencial = new Credencial();
        credencial.setObraSocial(obraSocial);
        credencial.setNumeroAfiliado(request.getNumeroAfiliado());
        credencial.setPlan(request.getPlan());
        credencial.setFechaVencimiento(request.getFechaVencimiento());
        credencial.setPaciente(paciente);

        repoCredenciales.save(credencial);
    }

}
