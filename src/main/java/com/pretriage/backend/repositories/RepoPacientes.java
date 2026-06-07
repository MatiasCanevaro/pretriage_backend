package com.pretriage.backend.repositories;

import com.pretriage.backend.model.personas.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoPacientes extends JpaRepository<Paciente, Long> {
}
