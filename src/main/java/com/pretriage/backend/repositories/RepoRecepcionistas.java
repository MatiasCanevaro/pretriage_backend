package com.pretriage.backend.repositories;

import com.pretriage.backend.model.personas.Recepcionista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoRecepcionistas  extends JpaRepository<Recepcionista, Long> {
    boolean existsByUsuarioAuthId(String auth0Id);

}
