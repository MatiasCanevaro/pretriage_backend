package com.pretriage.backend.repositories;

import com.pretriage.backend.model.hospitales.Sala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepoSalas extends JpaRepository<Sala, Long> {

    List<Sala> findByHospitalIdAndEspecialidadCodigoAndActivaTrue(Long idHospital, String codigoEspecialidad);
}
