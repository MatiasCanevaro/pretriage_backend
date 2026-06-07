package com.pretriage.backend.repositories;

import com.pretriage.backend.model.personas.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoMedico extends JpaRepository<Medico, Long> {
}
