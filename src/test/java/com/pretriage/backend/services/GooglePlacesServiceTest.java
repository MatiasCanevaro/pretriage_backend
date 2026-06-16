package com.pretriage.backend.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;


import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/*
* Test de integracion donde mockeo las respuestas de la api de google usando wireMockServer
* */

@SpringBootTest
@TestPropertySource(properties = { //se sobreescriben los valores del "application.properties" solo para ejecutar este test
        "google.api.key=test-api-key",
        "google.places.base-url=http://localhost:8089/v1/places"
})
public class GooglePlacesServiceTest {

    @Autowired
    private GooglePlacesService service;

    @MockitoBean
    private DireccionService direccionService;

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    void sePuebeObtenerUnaListaDeHospitalesCercanosEnBaseALatitudLongitud(){
        wireMockServer.stubFor(post(urlEqualTo("/v1/places:searchNearby"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "places": [
                                {
                                  "id": "hospital1",
                                  "formattedAddress": "Av Siempre Viva 123",
                                  "displayName": {
                                    "text": "Hospital Italiano"
                                  }
                                }
                              ]
                            }
                            """)));

        List<HospitalCercanoDTO> hospitales =
                service.buscarHospitales(-34.6, -58.4);

        assertEquals(1, hospitales.size());

        HospitalCercanoDTO hospital = hospitales.getFirst();

        assertEquals("hospital1", hospital.getPlaceId());
        assertEquals("Hospital Italiano", hospital.getNombre());
        assertEquals("Av Siempre Viva 123", hospital.getDireccion());
    }

    @Test
    void noSePuebeObtenerUnaListaDeHospitalesCercanosEnBaseALatitudLongitudSiNoHayHospitalesEnEseRadio(){
        wireMockServer.stubFor(post(urlEqualTo("/v1/places:searchNearby"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "places": []
                            }
                            """)));

        List<HospitalCercanoDTO> hospitales =
                service.buscarHospitales(-34.6, -58.4);

        assertTrue(hospitales.isEmpty());
    }



    @Test
    void sePuedeObtenerUnHospitalDeGoogleEnBaseAlPlaceID(){


        Direccion direccion = new Direccion();

        when(direccionService.buscarOCrearDireccion(
                any(),
                any(),
                any()))
                .thenReturn(direccion);

        wireMockServer.stubFor(get(urlEqualTo("/v1/places/hospital1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                          "id":"hospital1",
                          "displayName":{
                            "text":"Hospital Italiano"
                          },
                          "addressComponents":[],
                          "location":{
                            "latitude":-34.6,
                            "longitude":-58.4
                          }
                        }
                        """)));

        Hospital hospital =
                service.obtenerHospitalDesdeGoogle("hospital1");

        assertNotNull(hospital);

        assertEquals("hospital1", hospital.getPlaceId());
        assertEquals("Hospital Italiano", hospital.getNombre());
        assertSame(direccion, hospital.getDireccion());
    }

    @Test
    void noSePuedeObtenerUnHospitalEnBaseAlPlaceIDSiNoExisteONoEsValido(){
        wireMockServer.stubFor(get(urlEqualTo("/v1/places/inexistente"))
                .willReturn(aResponse()
                        .withStatus(404)));

        Hospital hospital =
                service.obtenerHospitalDesdeGoogle("inexistente");

        assertNull(hospital);
    }


}
