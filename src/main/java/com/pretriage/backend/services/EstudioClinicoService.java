package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EstudioClinicoDTO;
import com.pretriage.backend.exceptions.ArchivoS3Exception;
import com.pretriage.backend.exceptions.PacienteNoExisteException;
import com.pretriage.backend.model.consultas.EstudioClinico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoEstudiosClinicos;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EstudioClinicoService {

    private final RepoEstudiosClinicos repoEstudiosClinicos;

    private final PacienteService pacienteService;
    private final GestionDeArchivosService gestionDeArchivosService;


    public void subirArchivoEstudioClinico(
            String auth0Id,
            MultipartFile file,
            EstudioClinicoDTO estudioClinicoRequest) {
        if(file == null){
            throw new IllegalArgumentException("El archivo no puede ser nulo");
        }

        Paciente paciente = this.validadYObtenerPacienteConAuth0(auth0Id);

        String fileName = file.getOriginalFilename();
        if(fileName == null){
            throw new IllegalArgumentException("El nombre del archivo no puede ser nulo");
        }

        this.gestionDeArchivosService.subirArchivo(file, fileName);

        String fileExtension = fileName.split("\\.")[1];

        EstudioClinico estudioClinico = this.mappearAEtudioClinico(estudioClinicoRequest);

        estudioClinico.setExtensionArchivo(fileExtension);
        estudioClinico.setNombreArchivo(fileName);
        estudioClinico.setPaciente(paciente);
        estudioClinico.setRutaArchivo(fileName);
        estudioClinico.setTamanoArchivo(file.getSize());

        repoEstudiosClinicos.save(estudioClinico);

    }

    public void eliminarArchivoEstudioClinico(String auth0Id, Long idEstudio) {
        this.validadYObtenerPacienteConAuth0(auth0Id);

        Optional<EstudioClinico> opEstudioClinico = repoEstudiosClinicos.findById(idEstudio);
        if(opEstudioClinico.isEmpty()){
            throw new NoSuchElementException("No se encontro el estudio clinico con id: " + idEstudio);
        }
        EstudioClinico estudioAEliminar = opEstudioClinico.get();

        String rutaArchivoAEliminar = estudioAEliminar.getRutaArchivo();

        if(rutaArchivoAEliminar == null){
            throw new IllegalArgumentException("No se encontra cargada la ruta del archivo");
        }

        //primero intento borrar de s3
            // si falla no llega a hacer el borrado lógico, como si no hubiera pasado nada
        gestionDeArchivosService.eliminarArchivo(rutaArchivoAEliminar);

        estudioAEliminar.setActivo(false); // borrado lógico
        repoEstudiosClinicos.save(estudioAEliminar);
    }

    public List<EstudioClinicoDTO> obtenerTodosEstudiosClinicos(String auth0Id){
        Paciente paciente = this.validadYObtenerPacienteConAuth0(auth0Id);

        return repoEstudiosClinicos.findAllByPacienteAndActivoTrue(paciente)
                .stream()
                .map(this::mappearAEstudioClinicoDTO)
                .toList();
    }

    public EstudioClinicoDTO obtenerEstudioClinico(String auth0Id, Long idEstudioClinico){
        Paciente paciente = this.validadYObtenerPacienteConAuth0(auth0Id);

        return this.obtenerEstudioClinicoDePaciente(paciente, idEstudioClinico);
    }

    public EstudioClinicoDTO obtenerEstudioClinicoDePaciente(Paciente paciente, Long idEstudioClinico){
        Optional<EstudioClinico> opEstudioClinico = this.repoEstudiosClinicos.findByIdAndPacienteAndActivoTrue(idEstudioClinico, paciente);

        if(opEstudioClinico.isEmpty()){
            throw new NoSuchElementException(
                    "No se encontro el estudio clinico con id: " + idEstudioClinico + " para el paciente con id " + paciente.getId());
        }

        EstudioClinico estudioClinico = opEstudioClinico.get();

        return this.mappearAEstudioClinicoDTO(estudioClinico);
    }

    public byte[] descargarEstudioClinico(String auth0Id, Long idEstudioClinico){
        Paciente paciente = this.validadYObtenerPacienteConAuth0(auth0Id);

        return this.descargarEstudioClinicoDePaciente(paciente, idEstudioClinico);
    }

    public byte[] descargarEstudioClinicoDePaciente(Paciente paciente, Long idEstudioClinico){
        Optional<EstudioClinico> opEstudioClinico = this.repoEstudiosClinicos.findByIdAndPacienteAndActivoTrue(idEstudioClinico, paciente);

        if(opEstudioClinico.isEmpty()){
            throw new NoSuchElementException(
                    "No se encontro el estudio clinico con id: " + idEstudioClinico + " para el paciente con id " + paciente.getId());
        }

        EstudioClinico estudioClinico = opEstudioClinico.get();

        String rutaArchivoADescargar = estudioClinico.getRutaArchivo();

        if(rutaArchivoADescargar == null){
            throw new IllegalArgumentException("No se encontra cargada la ruta del archivo");
        }

        return gestionDeArchivosService.descargarArchivoDesdeS3(rutaArchivoADescargar);
    }

    private EstudioClinico mappearAEtudioClinico(EstudioClinicoDTO dto){
        EstudioClinico estudioClinico = new EstudioClinico();

        estudioClinico.setDescripcion(dto.getDescripcion());
        estudioClinico.setFechaSubida(LocalDateTime.now());
        estudioClinico.setTipoArchivo(dto.getTipoArchivo());
        return estudioClinico;
    }

    @Transactional
    private EstudioClinicoDTO mappearAEstudioClinicoDTO(EstudioClinico estudioClinico){
        EstudioClinicoDTO dto = new EstudioClinicoDTO();

        dto.setId(estudioClinico.getId());
        dto.setDescripcion(estudioClinico.getDescripcion());
        dto.setNombreArchivo(estudioClinico.getNombreArchivo());
        dto.setExtensionArchivo(estudioClinico.getExtensionArchivo());
        dto.setPacienteId(estudioClinico.getPaciente().getId());
        dto.setTamanoArchivo(estudioClinico.getTamanoArchivo());
        dto.setFechaSubida(estudioClinico.getFechaSubida());
        dto.setRutaArchivo(estudioClinico.getRutaArchivo());
        dto.setTipoArchivo(estudioClinico.getTipoArchivo());

        return dto;
    }

    private Paciente validadYObtenerPacienteConAuth0(String auth0Id){
        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id); // valido que sea paciente y usuario válido

        if(opPaciente.isEmpty()){
            throw new PacienteNoExisteException();
        }
        return opPaciente.get();
    }
}
