package com.pretriage.backend.repositories;

import com.pretriage.backend.model.hospitales.ObraSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepoObraSociales extends JpaRepository<ObraSocial, Long> {

    Optional<ObraSocial> findByNombreEqualsIgnoreCase(String nombre);

}