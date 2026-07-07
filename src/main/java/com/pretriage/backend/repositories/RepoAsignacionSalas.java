package com.pretriage.backend.repositories;

import com.pretriage.backend.model.consultas.AsignacionSala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoAsignacionSalas extends JpaRepository<AsignacionSala, Long> {

}
