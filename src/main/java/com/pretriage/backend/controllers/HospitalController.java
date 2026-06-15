package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.CoordenadaRequest;
import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.SeleccionHospitalRequest;
import com.pretriage.backend.services.AtencionHospitalService;
import com.pretriage.backend.services.GooglePlacesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HospitalController {


    private final GooglePlacesService googlePlacesService;

    private final AtencionHospitalService atencionHospitalService;

    //todo test estos endpoints

    @PostMapping("/api/hospitales/cercanos")
    public ResponseEntity<List<HospitalCercanoDTO>> obtenerHospitalesCercanos(
            @RequestBody CoordenadaRequest request
    ){
        return ResponseEntity.ok(googlePlacesService.buscarHospitales(
                request.getLatitud(),
                request.getLongitud()));
    }

    @PostMapping("/api/atencion/hospital")
    public ResponseEntity<Map<String, String>> seleccionarHospital(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid SeleccionHospitalRequest request
    ){
        atencionHospitalService.seleccionarHospital(
                jwt.getSubject(),
                request.getPlaceId());

        return ResponseEntity.ok(
                Map.of(
                        "mensaje",
                        "Hospital seleccionado correctamente"));
    }

}
