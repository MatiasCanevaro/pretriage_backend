package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.EstudioClinicoDTO;
import com.pretriage.backend.exceptions.ArchivoS3Exception;
import com.pretriage.backend.exceptions.PacienteNoExisteException;
import com.pretriage.backend.model.consultas.EstudioClinico;
import com.pretriage.backend.model.personas.Paciente;
import com.pretriage.backend.repositories.RepoEstudiosClinicos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EstudioClinicoServiceTest {

    @Mock
    private S3Client s3;

    @Mock
    private RepoEstudiosClinicos repoEstudiosClinicos;

    @Mock
    private PacienteService pacienteService;

    @InjectMocks
    private EstudioClinicoService estudioClinicoService;

    private static final String AUTH0_ID = "auth0|123";
    private static final String FILE_NAME = "test.pdf";
    private static final String FILE_CONTENT = "Test file content";
    private Paciente paciente;
    private EstudioClinicoDTO estudioClinicoDTO;
    private MultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        paciente = new Paciente();
        paciente.setId(1L);

        estudioClinicoDTO = new EstudioClinicoDTO();
        estudioClinicoDTO.setTipoArchivo("PDF");
        estudioClinicoDTO.setDescripcion("Test description");

        multipartFile = new MockMultipartFile(
                "file",
                FILE_NAME,
                "application/pdf",
                FILE_CONTENT.getBytes()
        );
    }

    @Test
    void subirArchivoEstudioClinico_Success() {
        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.of(paciente));

        estudioClinicoService.subirArchivoEstudioClinico(AUTH0_ID, multipartFile, estudioClinicoDTO);

        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));// se guarda en s3

        verify(repoEstudiosClinicos).save(any(EstudioClinico.class));// se guarda metadata en la db
    }

    @Test
    void subirArchivoEstudioClinico_FileNull_ThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> estudioClinicoService.subirArchivoEstudioClinico(AUTH0_ID, null, estudioClinicoDTO)
        );

        assertEquals("El archivo no puede ser nulo", exception.getMessage());
        verify(pacienteService, never()).obtenerPacienteConUsuarioAuthId(any());
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(repoEstudiosClinicos, never()).save(any(EstudioClinico.class));
    }

    @Test
    void subirArchivoEstudioClinico_PacienteNoExiste_ThrowsPacienteNoExisteException() {
        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.empty());

        assertThrows(
                PacienteNoExisteException.class,
                () -> estudioClinicoService.subirArchivoEstudioClinico(AUTH0_ID, multipartFile, estudioClinicoDTO)
        );

        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(repoEstudiosClinicos, never()).save(any(EstudioClinico.class));
    }

    @Test
    void subirArchivoEstudioClinico_IOException_ThrowsArchivoS3Exception() throws IOException {
        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.of(paciente));
        
        MultipartFile fileWithIOException = mock(MultipartFile.class);
        when(fileWithIOException.getOriginalFilename()).thenReturn(FILE_NAME);
        when(fileWithIOException.getBytes()).thenThrow(new IOException("Test IO exception"));

        ArchivoS3Exception exception = assertThrows(
                ArchivoS3Exception.class,
                () -> estudioClinicoService.subirArchivoEstudioClinico(AUTH0_ID, fileWithIOException, estudioClinicoDTO)
        );

        assertEquals("No se pudo subir el archivo a s3", exception.getMessage());
        verify(pacienteService).obtenerPacienteConUsuarioAuthId(AUTH0_ID);
        verify(repoEstudiosClinicos, never()).save(any(EstudioClinico.class));
    }

    @Test
    void eliminarArchivoEstudioClinico_Success() {
        Long idEstudio = 1L;
        EstudioClinico estudioClinicoMock = mock(EstudioClinico.class);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.of(paciente));
        when(repoEstudiosClinicos.findById(idEstudio)).thenReturn(Optional.of(estudioClinicoMock));
        when(estudioClinicoMock.getRutaArchivo()).thenReturn("/"+FILE_NAME);

        estudioClinicoService.eliminarArchivoEstudioClinico(AUTH0_ID,idEstudio);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        verify(estudioClinicoMock).setActivo(false);
        verify(repoEstudiosClinicos).save(estudioClinicoMock);
    }

    @Test
    void eliminarArchivoEstudioClinico_PacienteNoExiste_ThrowsPacienteNoExisteException() {
        Long idEstudio = 1L;
        EstudioClinico estudioClinicoMock = mock(EstudioClinico.class);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.empty());

        assertThrows(PacienteNoExisteException.class,
                ()-> estudioClinicoService.eliminarArchivoEstudioClinico(AUTH0_ID,idEstudio));

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(estudioClinicoMock, never()).setActivo(false);
        verify(repoEstudiosClinicos, never()).save(estudioClinicoMock);
    }

    @Test
    void eliminarArchivoEstudioClinico_EstudioNoExiste_ThrowsNoSuchElementException() {
        Long idEstudio = 1L;
        EstudioClinico estudioClinicoMock = mock(EstudioClinico.class);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.of(paciente));
        when(repoEstudiosClinicos.findById(idEstudio)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                ()-> estudioClinicoService.eliminarArchivoEstudioClinico(AUTH0_ID,idEstudio));

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(estudioClinicoMock, never()).setActivo(false);
        verify(repoEstudiosClinicos, never()).save(estudioClinicoMock);
    }

    @Test
    void eliminarArchivoEstudioClinico_NoTieneRuta_ThrowsIllegalArgumentException() {
        Long idEstudio = 1L;
        EstudioClinico estudioClinicoMock = mock(EstudioClinico.class);

        when(pacienteService.obtenerPacienteConUsuarioAuthId(AUTH0_ID)).thenReturn(Optional.of(paciente));
        when(repoEstudiosClinicos.findById(idEstudio)).thenReturn(Optional.of(estudioClinicoMock));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                ()-> estudioClinicoService.eliminarArchivoEstudioClinico(AUTH0_ID,idEstudio));

        assertEquals("No se encontra cargada la ruta del archivo", exception.getMessage());

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(estudioClinicoMock, never()).setActivo(false);
        verify(repoEstudiosClinicos, never()).save(estudioClinicoMock);
    }

}
