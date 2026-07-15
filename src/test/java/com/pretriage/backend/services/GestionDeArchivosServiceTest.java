package com.pretriage.backend.services;

import com.pretriage.backend.exceptions.ArchivoS3Exception;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GestionDeArchivosServiceTest {
    @Test
    void informaQueS3EstaDeshabilitadoCuandoNoHayClienteConfigurado() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        GestionDeArchivosService service = new GestionDeArchivosService(provider);

        ArchivoS3Exception error = assertThrows(
                ArchivoS3Exception.class,
                () -> service.descargarArchivoDesdeS3("estudios/resultado.pdf"));

        assertEquals(
                "El almacenamiento de estudios clínicos en S3 está deshabilitado en este entorno",
                error.getMessage());
    }
}
