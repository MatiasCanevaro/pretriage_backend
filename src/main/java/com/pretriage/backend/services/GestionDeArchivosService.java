package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.ArchivoS3Exception;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class GestionDeArchivosService {


    private final ObjectProvider<S3Client> s3ClientProvider;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public byte[] descargarArchivoDesdeS3(String rutaArchivo){
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new ArchivoS3Exception(
                    "El almacenamiento de estudios clínicos en S3 está deshabilitado en este entorno");
        }

        // 1. Construir la petición de descarga para AWS S3
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(rutaArchivo)
                .build();

        // 2. Descargar el archivo desde S3 y convertirlo a un arreglo de bytes
        try (ResponseInputStream<GetObjectResponse> s3ObjectSource = s3Client.getObject(getObjectRequest)) {
            return s3ObjectSource.readAllBytes();
        } catch (S3Exception e) {
            // Error específico de la API de AWS S3 (ej. no existe el objeto en el bucket)
            throw new ArchivoS3Exception("Error al descargar el archivo desde AWS S3: " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            // Error de entrada/salida al leer los bytes del flujo
            throw new ArchivoS3Exception("Error al procesar los bytes del archivo médico :" + e.getMessage());
        }
    }
}
