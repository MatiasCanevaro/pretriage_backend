package com.pretriage.backend.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoArriboHospitalResponse;
import com.pretriage.backend.model.hospitales.Coordenada;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.Hospital;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalTime;
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
        "google.places.base-url=http://localhost:8089/v1/places",
        "google.routes.base-url=http://localhost:8089/directions/v2:computeRoutes",
        // auth0 no se usan en este test
        "auth0.client-id.machine-to-machine=AUTH0_M2M_CLIENT_ID",
        "auth0.client-secret.machine-to-machine=AUTH0_M2M_CLIENT_SECRET",
        "auth0.scope.machine-to-machine=AUTH0_M2M_SCOPE",
        "auth0.base-path=http://localhost:8089",
        "auth0.client-id.app=ATUH0_APP_CLIENT_ID"
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

    @Test
    void sePuedeCalcularTiempoEstimadoArriboHospitalConExito(){
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setPlaceId("hospital1");

        Coordenada coordenada = new Coordenada();
        coordenada.setLatitud(-34.6);
        coordenada.setLongitud(-58.4);

        Direccion direccion = new Direccion();
        direccion.setCoordenada(coordenada);
        hospital.setDireccion(direccion);

        wireMockServer.stubFor(post(urlEqualTo("/directions/v2:computeRoutes"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                   "routes":[
                                      {
                                         "duration":"300s",
                                         "distanceMeters":1500,
                                         "polyline":{
                                            "encodedPolyline":"encoded_polyline_string"
                                         },
                                         "routeLabels":[
                                            "DEFAULT_ROUTE"
                                         ],
                                         "legs":[
                                            {
                                               "duration":"120s",
                                               "distanceMeters":148,
                                               "polyline":{
                                                  "encodedPolyline":"encoded_polyline_string_leg1_step1"
                                               },
                                               "steps":[
                                                  {
                                                     "transitDetails":{
                                                        "transitLine":{
                                                           "name":"Linea 85 A",
                                                           "nameShort":"85 A"
                                                        }
                                                     },
                                                     "duration":"100s",
                                                     "distanceMeters":130,
                                                     "polyline":{
                                                        "encodedPolyline":"encoded_polyline_string_leg1"
                                                     }
                                                  }
                                               ]
                                            }
                                         ]
                                      }
                                   ]
                                }
                                """)));

        List<TiempoEstimadoArriboHospitalResponse> responseList =
                service.calcularTiempoArriboHospital(hospital, "transporte-publico", -34.61, -58.41);

        assertNotNull(responseList);
        assertEquals(1, responseList.size());

        TiempoEstimadoArriboHospitalResponse response = responseList.getFirst();
        assertEquals(1L, response.getIdHospital());
        assertEquals("transporte-publico", response.getTransporte());
        assertEquals(LocalTime.of(0, 5, 0), response.getTiempoEstimadoArribo());
        assertEquals(1500, response.getDistanciaMetros());
        assertEquals("encoded_polyline_string", response.getPolylineCode());
    }

    @Test
    void fallaCalcularTiempoEstimadoArriboHospitalCuandoApiNoDevuelveRutas(){
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setPlaceId("hospital1");

        Coordenada coordenada = new Coordenada();
        coordenada.setLatitud(-34.6);
        coordenada.setLongitud(-58.4);

        Direccion direccion = new Direccion();
        direccion.setCoordenada(coordenada);
        hospital.setDireccion(direccion);

        wireMockServer.stubFor(post(urlEqualTo("/directions/v2:computeRoutes"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "routes": []
                            }
                            """)));

        assertThrows(IllegalArgumentException.class, () ->
                service.calcularTiempoArriboHospital(hospital, "transporte-publico", -34.61, -58.41));
    }

    @Test
    void fallaCalcularTiempoEstimadoArriboHospitalCuandoApiDevuelveError(){
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setPlaceId("hospital1");

        Coordenada coordenada = new Coordenada();
        coordenada.setLatitud(-34.6);
        coordenada.setLongitud(-58.4);

        Direccion direccion = new Direccion();
        direccion.setCoordenada(coordenada);
        hospital.setDireccion(direccion);

        wireMockServer.stubFor(post(urlEqualTo("/directions/v2:computeRoutes"))
                .willReturn(aResponse()
                        .withStatus(500)));

        assertThrows(IllegalArgumentException.class, () ->
                service.calcularTiempoArriboHospital(hospital, "transporte-publico", -34.61, -58.41));
    }

    @Test
    void fallaCalcularTiempoEstimadoArriboHospitalCuandoTransporteNoEsValido(){
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setPlaceId("hospital1");

        Coordenada coordenada = new Coordenada();
        coordenada.setLatitud(-34.6);
        coordenada.setLongitud(-58.4);

        Direccion direccion = new Direccion();
        direccion.setCoordenada(coordenada);
        hospital.setDireccion(direccion);

        assertThrows(IllegalArgumentException.class, () ->
                service.calcularTiempoArriboHospital(hospital, "transporte-invalido", -34.61, -58.41));
    }

    @Test
    void fallaCalcularTiempoEstimadoArriboHospitalCuandoNoHayRutaDefault(){
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setPlaceId("hospital1");

        Coordenada coordenada = new Coordenada();
        coordenada.setLatitud(-34.6);
        coordenada.setLongitud(-58.4);

        Direccion direccion = new Direccion();
        direccion.setCoordenada(coordenada);
        hospital.setDireccion(direccion);

        wireMockServer.stubFor(post(urlEqualTo("/directions/v2:computeRoutes"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "routes": [
                                {
                                  "duration": "300s",
                                  "distanceMeters": 1500,
                                  "polyline": {
                                    "encodedPolyline": "encoded_polyline_string"
                                  },
                                  "routeLabels": ["FUEL_EFFICIENT"]
                                }
                              ]
                            }
                            """)));

        assertThrows(IllegalArgumentException.class, () ->
                service.calcularTiempoArriboHospital(hospital, "transporte-publico", -34.61, -58.41));
    }

    @Test
    void sePuedeVerificarTransporteValido(){
        assertTrue(service.esTransporteValido("transporte-publico"));
        assertTrue(service.esTransporteValido("vehiculo"));
        assertTrue(service.esTransporteValido("vehiculo-dos-ruedas"));
        assertTrue(service.esTransporteValido("caminar"));
        assertTrue(service.esTransporteValido("bicicleta"));
    }

    @Test
    void sePuedeVerificarTransporteInvalido(){
        assertFalse(service.esTransporteValido("transporte-invalido"));
        assertFalse(service.esTransporteValido("avion"));
        assertFalse(service.esTransporteValido(""));
    }


}
