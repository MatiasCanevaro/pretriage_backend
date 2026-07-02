package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CredencialRequest;
import com.pretriage.backend.controllers.dtos.CredencialResponse;
import com.pretriage.backend.exceptions.CredencialValidaYaExisteException;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CredencialService {

    private final PacienteService pacienteService;
    private final RecepcionistaService recepcionistaService;
    private final RepoObraSociales repoObraSociales;
    private final RepoCredenciales repoCredenciales;

    public void cargarCredencialPaciente(
            String auth0IdPaciente,
            CredencialRequest request) {

        Paciente paciente = obtenerPaciente(auth0IdPaciente);

        cargarCredencial(request, paciente);
    }

    public List<CredencialResponse> obtenerCredencialesPaciente(String auth0IdPaciente) {

        Paciente paciente = obtenerPaciente(auth0IdPaciente);

        return repoCredenciales
                .findByPacienteId(paciente.getId())
                .stream()
                .map(this::mapearCredencialResponse)
                .toList();
    }

    public void cargarCredencialRecepcionista(String auth0IdRecepcionista, Long idPaciente, @Valid CredencialRequest request) {

        verificarSiEsRecepcionista(auth0IdRecepcionista);

        Paciente paciente = pacienteService.obtenerPaciente(idPaciente);

        cargarCredencial(request, paciente);
    }

    private Paciente obtenerPaciente(String auth0IdPaciente) {

        Optional<Paciente> opPaciente = pacienteService
                .obtenerPacienteConUsuarioAuthId(auth0IdPaciente);
        if(opPaciente.isEmpty()){
            throw new AccessDeniedException(
                    "No tiene permisos para cargar la credencial");
        } else {
            return opPaciente.get();
        }
    }


    private void verificarSiEsRecepcionista(String auth0IdRecepcionista) {

        boolean esRecepcionista = recepcionistaService
                .esRecepcionistaConUsuarioId(auth0IdRecepcionista);
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

        //verificarCredencialVigente(paciente);

        Credencial credencial = new Credencial();
        credencial.setObraSocial(obraSocial);
        credencial.setNumeroAfiliado(request.getNumeroAfiliado());
        credencial.setPlan(request.getPlan());
        credencial.setFechaVencimiento(request.getFechaVencimiento());
        credencial.setPaciente(paciente);

        repoCredenciales.save(credencial);
    }

    private CredencialResponse mapearCredencialResponse(Credencial credencial) {

        return new CredencialResponse(
                credencial.getId(),
                credencial.getNumeroAfiliado(),
                credencial.getPlan(),
                credencial.getFechaVencimiento());
    }

    @Transactional
    public void eliminarCredencial(Long idCredencial, String auth0IdPaciente) {

        Paciente paciente = obtenerPaciente(auth0IdPaciente);

        Credencial credencial = repoCredenciales.findById(idCredencial)
                .orElseThrow(() -> new AccessDeniedException(
                        "No tiene permisos para eliminar la credencial"));

        if(!credencial.getPaciente().getId().equals(paciente.getId())) {
            throw new AccessDeniedException(
                    "No tiene permisos para eliminar esta credencial");
        }

        repoCredenciales.deleteById(idCredencial);
    }

    @Transactional
    public void eliminarCredencialRecepcionista(Long idCredencial, Long idPaciente, String auth0IdRecepcionista) {

        verificarSiEsRecepcionista(auth0IdRecepcionista);

        Paciente paciente = pacienteService.obtenerPaciente(idPaciente);

        Credencial credencial = repoCredenciales.findById(idCredencial)
                .orElseThrow(() -> new AccessDeniedException(
                        "No tiene permisos para eliminar la credencial"));

        if(!credencial.getPaciente().getId().equals(paciente.getId())) {
            throw new AccessDeniedException(
                    "No tiene permisos para eliminar esta credencial");
        }

        repoCredenciales.deleteById(idCredencial);
    }
}