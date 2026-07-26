package com.pretriage.backend.repositories;

import com.pretriage.backend.model.hospitales.Sala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoSalas extends JpaRepository<Sala, Long> {

    List<Sala> findByHospitalIdAndEspecialidadCodigoAndActivaTrue(Long idHospital, String codigoEspecialidad);
    List<Sala> findByHospitalIdOrderByNombreAsc(Long hospitalId);
    Optional<Sala> findByIdAndHospitalId(Long id, Long hospitalId);
    boolean existsByHospitalIdAndNombreIgnoreCase(Long hospitalId, String nombre);
    boolean existsByHospitalIdAndNombreIgnoreCaseAndIdNot(Long hospitalId, String nombre, Long id);
    boolean existsByHospitalIdAndEspecialidadIdAndActivaTrue(Long hospitalId, Long especialidadId);
}
