package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CoordenadaRequest;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.googleMaps.*;
import com.pretriage.backend.model.hospitales.Coordenada;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.repositories.RepoCoordenadas;
import com.pretriage.backend.repositories.RepoDirecciones;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GooglePlacesService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesService.class);

   private final DireccionService direccionService;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Base URL de la Places API (New)
    @Value("${google.places.base-url}")
    private String PLACES_BASE_URL ;

    // Radio de búsqueda en metros (5 km). Ajustar según necesidad.
    private static final double RADIO_BUSQUEDA_METROS = 5000.0;

    // Máximo de resultados por búsqueda (límite de la API: 20)
    private static final int MAX_RESULTADOS = 20;

    /**
     * Campo mask para Nearby Search.
     * Todos estos campos disparan el SKU "Nearby Search Pro" → 5.000 gratis/mes.
     * Ref: https://developers.google.com/maps/documentation/places/web-service/nearby-search#fieldmask
     */
    private static final String NEARBY_FIELD_MASK =
            "places.id,places.displayName,places.formattedAddress,places.location,places.types";

    /**
     * Campo mask para Place Details.
     * - formattedAddress, location, addressComponents, types → Essentials SKU
     * - displayName → sube al tier Pro SKU → 5.000 gratis/mes
     * Si se quiere máximo ahorro, eliminar displayName y quedarse solo con Essentials (10.000 gratis/mes).
     * Ref: https://developers.google.com/maps/documentation/places/web-service/place-details#fieldmask
     */
    private static final String DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location,addressComponents,types";


    @Value("${google.api.key}")
    private String apiKey;


    public List<HospitalCercanoDTO> buscarHospitales(Double latitud, Double longitud) {

        // Body del POST según la documentación oficial de Nearby Search (New)
        // https://developers.google.com/maps/documentation/places/web-service/nearby-search
        Map<String, Object> requestBody = Map.of(
                "includedTypes", List.of("hospital"),
                "maxResultCount", MAX_RESULTADOS,
                "rankPreference", "DISTANCE",  // ordena por distancia, no popularidad
                "locationRestriction", Map.of(
                        "circle", Map.of(
                                "center", Map.of(
                                        "latitude", latitud,
                                        "longitude", longitud
                                ),
                                "radius", RADIO_BUSQUEDA_METROS
                        )
                )
        );

        try {
            String responseJson = restClient.post()
                    .uri(PLACES_BASE_URL + ":searchNearby")
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    // El field mask va en header para controlar qué datos devuelve la API
                    // y así no disparar SKUs más caros innecesariamente.
                    .header("X-Goog-FieldMask", NEARBY_FIELD_MASK)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            GooglePlacesNearbyResponseDTO response =
                    objectMapper.readValue(responseJson, GooglePlacesNearbyResponseDTO.class);

            if (response == null || response.getPlaces() == null) {
                log.warn("La API de Google no devolvió resultados para lat={}, lng={}", latitud, longitud);
                return Collections.emptyList();
            }

            return response.getPlaces().stream()
                    .map(this::mapearAHospitalCercanoDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error al buscar hospitales cercanos en Google Places API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Convierte un {@link GooglePlaceDTO} devuelto por la API
     * a un {@link HospitalCercanoDTO} del dominio de la aplicación.
     */
    private HospitalCercanoDTO mapearAHospitalCercanoDTO(
            GooglePlaceDTO place) {

        String nombre = (place.getDisplayName() != null)
                ? place.getDisplayName().getText()
                : "Nombre no disponible";

        Double lat = null;
        Double lng = null;
        if (place.getLocation() != null) {
            lat = place.getLocation().getLatitude();
            lng = place.getLocation().getLongitude();
        }

        HospitalCercanoDTO hospitalCercanoDTO = new HospitalCercanoDTO();
        hospitalCercanoDTO.setDireccion(place.getFormattedAddress());
        hospitalCercanoDTO.setPlaceId(place.getId());
        hospitalCercanoDTO.setNombre(nombre);

        return hospitalCercanoDTO;
    }


    /**
     * Obtiene los detalles de un hospital a partir de su Google Place ID,
     * usando Place Details (New).
     *
     * GET https://places.googleapis.com/v1/places/{placeId}
     *
     * @param placeId el identificador único del lugar en Google (ej: "ChIJN1t_tDeuEmsRUsoyG83frY4")
     * @return un {@link Hospital} con los datos del lugar, o {@code null} si ocurre un error
     */
    public Hospital obtenerHospitalDesdeGoogle(String placeId) {

        try {
            // La URL es: GET /v1/places/{placeId}?fields=...&key=...
            // Según la documentación, el placeId va en el path, no en el body.
            // https://developers.google.com/maps/documentation/places/web-service/place-details
            String responseJson = restClient.get()
                    .uri(PLACES_BASE_URL + "/" + placeId)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", DETAILS_FIELD_MASK)
                    .retrieve()
                    .body(String.class);

            GooglePlaceDetailsResponseDTO detalles =
                    objectMapper.readValue(responseJson, GooglePlaceDetailsResponseDTO.class);

            if (detalles == null) {
                log.warn("Google Places API no devolvió detalles para placeId={}", placeId);
                throw new NoSuchElementException("Hospital inexistente");
            }

            return mapearAHospital(detalles);

        } catch (Exception e) {
            log.error("Error al obtener detalles del hospital con placeId={}: {}", placeId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convierte un {@link GooglePlaceDetailsResponseDTO} a la entidad {@link Hospital}.
     */
    private Hospital mapearAHospital(GooglePlaceDetailsResponseDTO detalles) {
        Hospital hospital = new Hospital();

        hospital.setPlaceId(detalles.getId());
        hospital.setNombre(
                detalles.getDisplayName() != null
                        ? detalles.getDisplayName().getText()
                        : "Nombre no disponible"
        );


        Direccion direccion = direccionService.buscarOCrearDireccion(detalles.getAddressComponents(), detalles.getLocation(), detalles.getId());

        hospital.setDireccion(direccion);


        // Extraer país y ciudad de los addressComponents si están disponibles
        if (detalles.getAddressComponents() != null) {
            for (AddressComponent comp : detalles.getAddressComponents()) {
                if (comp.getTypes() == null) continue;
            }
        }

        return hospital;
    }

}
