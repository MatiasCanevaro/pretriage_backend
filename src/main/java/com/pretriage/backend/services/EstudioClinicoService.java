package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EstudioClinicoDTO;
import com.pretriage.backend.exceptions.ArchivoS3Exception;
import com.pretriage.backend.exceptions.PacienteNoExisteException;
import com.pretriage.backend.model.consultas.EstudioClinico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoEstudiosClinicos;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EstudioClinicoService {


    private final S3Client s3;
    private final RepoEstudiosClinicos repoEstudiosClinicos;

    private final PacienteService pacienteService;

    @Value("${aws.s3.bucket-name}")
    private String BucketName ;

    public void subirArchivoEstudioClinico(
            String auth0Id,
            MultipartFile file,
            EstudioClinicoDTO estudioClinicoRequest) {
        if(file == null){
            throw new IllegalArgumentException("El archivo no puede ser nulo");
        }

        Optional<Paciente> opPaciente = pacienteService.obtenerPacienteConUsuarioAuthId(auth0Id);
        if(opPaciente.isEmpty()){
            throw  new PacienteNoExisteException();
        }

        String fileName = file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(this.BucketName)
                .key(fileName)
                .build();
        try{
            this.s3.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException exception){
            throw new ArchivoS3Exception("No se pudo subir el archivo a s3");
        }

        String fileExtension = fileName.split("\\.")[1];

        EstudioClinico estudioClinico = this.mappearAEtudioClinico(estudioClinicoRequest);

        estudioClinico.setExtensionArchivo(fileExtension);
        estudioClinico.setNombreArchivo(fileName);
        estudioClinico.setPaciente(opPaciente.get());
        estudioClinico.setRutaArchivo("/"+fileName);
        estudioClinico.setTamanoArchivo(file.getSize());

        repoEstudiosClinicos.save(estudioClinico);

    }

    private EstudioClinico mappearAEtudioClinico(EstudioClinicoDTO dto){
        EstudioClinico estudioClinico = new EstudioClinico();

        estudioClinico.setDescripcion(dto.getDescripcion());
        estudioClinico.setFechaSubida(LocalDateTime.now());
        estudioClinico.setTipoArchivo(dto.getTipoArchivo());
        return estudioClinico;
    }
}
