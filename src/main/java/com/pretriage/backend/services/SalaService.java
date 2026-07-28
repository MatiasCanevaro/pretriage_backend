package com.pretriage.backend.services;

import com.pretriage.backend.controllers.dtos.SalaDTO;
import com.pretriage.backend.model.hospitales.Sala;
import com.pretriage.backend.repositories.RepoSalas;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SalaService {

    private final RepoSalas repoSalas;

    @Transactional
    public List<SalaDTO> obtenerSalas(Long hospitalId, String codigoEspecialidad) {
        return repoSalas.findByHospitalIdAndEspecialidadCodigoAndActivaTrue(hospitalId, codigoEspecialidad).stream()
                .map(this::mapearSala)
                .toList();
    }

    public Sala obtenerSala(Long salaId, Long hospitalId) {
        return repoSalas.findByIdAndHospitalId(salaId, hospitalId)
                .orElseThrow(() -> new NoSuchElementException("Sala inexistente en el hospital indicado"));
    }

    private SalaDTO mapearSala(Sala sala) {
        SalaDTO dto = new SalaDTO();
        dto.setId(sala.getId());
        dto.setNombre(sala.getNombre());
        return dto;
    }
}
