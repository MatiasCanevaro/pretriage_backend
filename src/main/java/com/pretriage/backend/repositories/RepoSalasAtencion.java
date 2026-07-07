package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.SalaAtencion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoSalasAtencion extends JpaRepository<SalaAtencion, Long> {
}
