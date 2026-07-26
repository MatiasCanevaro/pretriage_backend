package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.model.hospitales.EspecialidadMedica;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.model.personas.RolSistema;
import com.pretriage.backend.model.personas.UsuarioAuth;
import com.pretriage.backend.repositories.RepoEspecialidadesMedicas;
import com.pretriage.backend.repositories.RepoHospitales;
import com.pretriage.backend.repositories.RepoSalas;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SalaService {

    private final RepoSalas repoSalas;
    private final RepoHospitales repoHospitales;
    private final RepoEspecialidadesMedicas repoEspecialidadesMedicas;

    private final RecepcionistaService recepcionistaService;


    @Transactional
    public List<SalaDTO> obtenerSalas(Long hospitalId, String codigoEspecialidad) {
        return repoSalas.findByHospitalIdAndEspecialidadCodigoAndActivaTrue(hospitalId, codigoEspecialidad).stream()
                .map(this::mapearSala)
                .toList();
    }

    public Sala obtenerSala(Long salaId){
        return repoSalas.findByIdAndActivaTrue(salaId)
                .orElseThrow(() -> new NoSuchElementException("Sala inexistente"));
    }

    public void crearSalaAdmin(String auth0Id,
                               SalaDTO salaRequest,
                               Long hospitalId,
                               Long idEspecialidadMedica) {
        this.verificarSiEsAdmin(auth0Id);

        Hospital hospital = this.obtenerHospital(hospitalId);
        EspecialidadMedica especialidadMedica = this.obtenerEspecialidadMedica(idEspecialidadMedica); // si no existe la crea

        this.verificarSiExisteSalaConNombre(salaRequest.getNombre());

        Sala sala = new Sala();
        sala.setNombre(salaRequest.getNombre());
        sala.setEspecialidad(especialidadMedica);
        sala.setHospital(hospital);

        this.repoSalas.save(sala);
    }

    public void eliminarSalaAdmin(String auth0Id, Long salaId){
        this.verificarSiEsAdmin(auth0Id);

        Sala sala = this.obtenerSala(salaId);

        sala.setActiva(false);
        this.repoSalas.save(sala);
    }

    private SalaDTO mapearSala(Sala sala) {
        SalaDTO dto = new SalaDTO();
        dto.setId(sala.getId());
        dto.setNombre(sala.getNombre());
        return dto;
    }

    @Transactional
    private void verificarSiEsAdmin(String auth0Id){ // asumo que los admins son recepcionistas
        UsuarioAuth recepcionistaUser = recepcionistaService
                .obtenerUsuarioAuth(auth0Id);
        if(!recepcionistaUser.getRol().equals(RolSistema.ADMIN)){
            throw new AccessDeniedException(
                    "No tiene permisos para cargar la credencial");
        }
    }

    private Hospital obtenerHospital(Long idHospital){
        return this.repoHospitales.findById(idHospital)
                .orElseThrow(()-> new NoSuchElementException("No existe el hospital buscado"));
    }

    private EspecialidadMedica obtenerEspecialidadMedica(Long idEspecialidadMedica){
        return this.repoEspecialidadesMedicas.findById(idEspecialidadMedica)
                .orElseThrow(()-> new NoSuchElementException("No existe la especialidad buscada"));
    }

    private void verificarSiExisteSalaConNombre(String nombreSalaABuscar){
        this.repoSalas.findByNombre(nombreSalaABuscar)
                .ifPresent(sala -> {
                    throw new IllegalArgumentException("Ya existe una sala con el nombre: " + nombreSalaABuscar);
                });
    }

}
