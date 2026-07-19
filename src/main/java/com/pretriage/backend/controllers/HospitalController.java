package com.pretriage.backend.controllers;

import com.pretriage.backend.controllers.dtos.HospitalCercanoDTO;
import com.pretriage.backend.controllers.dtos.SeleccionHospitalRequest;
import com.pretriage.backend.controllers.dtos.TiempoEstimadoAtencionResponse;
import com.pretriage.backend.services.AtencionHospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HospitalController {

    private final AtencionHospitalService atencionHospitalService;

    @GetMapping("/api/hospitales/cercanos")
    public ResponseEntity<List<HospitalCercanoDTO>> obtenerHospitalesCercanos(
            @RequestParam Double latitud,
            @RequestParam Double longitud,
            @RequestParam String codigoEspecialidad,
            @AuthenticationPrincipal Jwt jwt
    ){
        return ResponseEntity.ok(atencionHospitalService
                .buscarHospitalesCercanos(latitud, longitud, codigoEspecialidad, jwt.getSubject()));
    }

    @PostMapping("/api/atencion/hospital")
    public ResponseEntity<Void> seleccionarHospital(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid SeleccionHospitalRequest request
    ){
        String auth0Id = jwt.getSubject();

        atencionHospitalService.seleccionarHospital(
                auth0Id,
                request.getPlaceId(),
                request.getCodigoEspecialidad());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/atencion/tiempo-estimado")
    public ResponseEntity<TiempoEstimadoAtencionResponse> obtenerTiempoEstimadoDeAtencion(
            @AuthenticationPrincipal Jwt jwt
    ){
        String auth0Id = jwt.getSubject();
        return ResponseEntity.ok(atencionHospitalService.obtenerTiempoEstimadoDeAtencion(auth0Id));
    }
}
