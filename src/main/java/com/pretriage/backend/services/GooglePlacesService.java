package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.CombinacionRutasDTO;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoArriboHospitalResponse;
import com.pretriage.backend.controllers.dtos.googleMaps.*;
import com.pretriage.backend.model.hospitales.Coordenada;
import com.pretriage.backend.model.hospitales.Direccion;
import com.pretriage.backend.model.hospitales.Hospital;
import com.pretriage.backend.repositories.RepoCoordenadas;
import com.pretriage.backend.repositories.RepoDirecciones;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
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

    @Value("${google.routes.base-url}")
    private String ROUTES_BASE_URL;

    // Radio de búsqueda en metros (5 km). Ajustar según necesidad.
    private static final double RADIO_BUSQUEDA_METROS = 5000.0;

    // Máximo de resultados por búsqueda (límite de la API: 20)
    private static final int MAX_RESULTADOS = 20;

    private static final Map<String, String> TRANSPORTES_PERMITIDOS_MAP = new HashMap<>( //busqueda mas rapida con un HASH MAP O(1)
            Map.of(
                    "transporte-publico", "transit",
                    "vehiculo", "driving",
                    "vehiculo-dos-ruedas", "two-wheel vehicles",
                    "caminar", "walking",
                    "bicicleta", "bicycling"));
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

    /**
     * Campo mask para Compute Routes.
     * Todos estos campos disparan el SKU "Routes: Compute Routes Essentials" → 10.000 gratis/mes.
     * Ref: https://developers.google.com/maps/documentation/routes/web-service/compute-routes#fieldmask
     */
    private static final String COMPUTE_ROUTES_FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.routeLabels,routes.legs";


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
    *  Verifica si un {@link String} transporte del sistema es válido
    * */
    public boolean esTransporteValido(String transporte){
        return TRANSPORTES_PERMITIDOS_MAP.containsKey(transporte.toLowerCase());
    }

    /**
    *  Calcula el tiempo estimado de arribo a un hospital
    * */
    @Transactional
    public List<TiempoEstimadoArriboHospitalResponse> calcularTiempoArriboHospital(
            Hospital hospital, String transporte, Double latitud, Double longitud
    ){
        Coordenada coordenadaHospital = hospital.getDireccion().getCoordenada();

        Map<String, Object> requestBody = Map.of(
                "origin", Map.of(
                        "location",
                        Map.of(
                                "latLng",
                                Map.of("latitude", latitud, "longitude", longitud))),
                "destination", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", coordenadaHospital.getLatitud(),
                                        "longitude", coordenadaHospital.getLongitud())),
                        //"placeId", hospital.getPlaceId() // si pongo latitud y longitud no es necesario poner el placeId
                ),
                "travelMode", this.traducirTransportePermitido(transporte), //necesario dado que la api está en inglés
                "units", "METRIC", // se lo pido en metros
                "computeAlternativeRoutes", true // máximo de 3 rutas
        );

        try {
            String responseJson = restClient.post()
                    .uri(ROUTES_BASE_URL)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    // El field mask va en header para controlar qué datos devuelve la API
                    // y así no disparar SKUs más caros innecesariamente.
                    .header("X-Goog-FieldMask", COMPUTE_ROUTES_FIELD_MASK)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            GooglePlacesComputeRouteResponseDTO response =
                    objectMapper.readValue(responseJson, GooglePlacesComputeRouteResponseDTO.class);

            if (response == null || response.getRoutes().isEmpty()) {
                log.warn("La API de Google no devolvió resultados para lat={}, lng={}", latitud, longitud);
                throw new IllegalArgumentException("La API de Google no devolvió resultados, intente más tarde");
            }

            return this.mappearATiempoEstimadoArriboHospitalResponse(response, transporte, hospital.getId());

        } catch (Exception e) {
            log.error("Error al buscar hospitales cercanos en Google Places API: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error en la API de Google, intente más tarde ");
        }
    }

    /**
    *  Transforma un {@link String} transporte del sistema a un {@link String} transporte de la API de Google
    * */
    private String traducirTransportePermitido(String transporteEnEspaniol) {
        if (!TRANSPORTES_PERMITIDOS_MAP.containsKey(transporteEnEspaniol.toLowerCase())) {
            throw new IllegalArgumentException("Transporte no permitido: " + transporteEnEspaniol);
        }

        return TRANSPORTES_PERMITIDOS_MAP.get(transporteEnEspaniol.toLowerCase());
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

    /**
     * Convierte un {@link GooglePlacesComputeRouteResponseDTO} a un {@link TiempoEstimadoArriboHospitalResponse}.
     */
    private List<TiempoEstimadoArriboHospitalResponse> mappearATiempoEstimadoArriboHospitalResponse(
            GooglePlacesComputeRouteResponseDTO response, String transporte, Long idHospital
    ){
        //busco la ruta mas optima que me devolvio la api
        List<RouteDTO> rutas = response.getRoutes();

        if(rutas.isEmpty()){
            throw new IllegalArgumentException("La API de Google no encontró una ruta válidá para el hospital");
        }

        List<TiempoEstimadoArriboHospitalResponse> rutasDTO = new ArrayList<>();

        rutas.forEach(ruta -> {
            TiempoEstimadoArriboHospitalResponse dto = new TiempoEstimadoArriboHospitalResponse();
            dto.setIdHospital(idHospital);
            dto.setTransporte(transporte);//transporte en español

            dto.setTiempoEstimadoArribo(
                    this.convertDurationToTime(ruta.getDuration())
            );

            dto.setDistanciaMetros(ruta.getDistanceMeters());
            dto.setPolylineCode(ruta.getPolyline().getEncodedPolyline());

            List<RouteLegDTO> tramosRuta = ruta.getLegs();

            if(!tramosRuta.isEmpty()){ //obtengo las líneas de transporte público que se usa para estimar la ruta
                List<CombinacionRutasDTO> combinaciones = new ArrayList<>();
                tramosRuta.forEach(
                        leg -> leg.getSteps()
                                .forEach(step -> {// TODO esta mal, hay que modificarlo, puede devolver caminantas hasta la parada, por lo qeu transitDetails puede ser null
                                        // tal vez conviene: stepsOverview.multiModalSegments[] en lugar de steps
                                            String lineaTransportePublico = step.getTransitDetails().getTransitLine().getName();
                                            CombinacionRutasDTO combinacionRutasDTO = new CombinacionRutasDTO();
                                            combinacionRutasDTO.setNombreLinea(lineaTransportePublico);
                                            combinaciones.add(combinacionRutasDTO);
                                        }
                                )
                );

                dto.setCombinacionesLineas(combinaciones);
            }

            rutasDTO.add(dto);
        });

        return rutasDTO;
    }

    /**
     * Convierte un {@link String} duration a un {@link LocalTime}
     * @param duration es el tiempo de viaje en segundos, por ejemplo "300s"
     */
    private LocalTime convertDurationToTime(String duration) {
        duration = duration.replace("s", "");
        int durationInt = Integer.parseInt(duration);
        return LocalTime.ofSecondOfDay(durationInt);
    }
}

