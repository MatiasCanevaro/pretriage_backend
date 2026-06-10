package com.pretriage.backend.repositories;

import com.pretriage.backend.model.hospitales.Credencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface RepoCredenciales extends JpaRepository<Credencial, Long> {

    boolean existsByPacienteIdAndFechaVencimientoGreaterThanEqual(
            Long pacienteId,
            LocalDate fecha);


}