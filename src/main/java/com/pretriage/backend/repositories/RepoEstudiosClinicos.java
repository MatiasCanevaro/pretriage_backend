package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.EstudioClinico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepoEstudiosClinicos extends JpaRepository<EstudioClinico, Long> {

    @Query("SELECT e FROM EstudioClinico e WHERE e.id = :idEstudioClinico AND e.paciente.id = :idPaciente")
    Optional<EstudioClinico> findByIdAndIdPaciente(Long idEstudioClinico, Long idPaciente);
}
