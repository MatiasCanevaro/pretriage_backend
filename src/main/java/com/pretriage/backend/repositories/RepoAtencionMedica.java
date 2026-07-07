package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.AtencionMedica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoAtencionMedica extends JpaRepository<AtencionMedica, Long> {


    @Query("SELECT a FROM AtencionMedica a WHERE a.fechaHoraFinAtencion >= :fechaHora")
    Optional<List<AtencionMedica>> findAllByfechaHoraFinAtencionAfter(LocalDateTime fechaHora);
}
