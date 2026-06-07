package com.pretriage.backend.repositories;

import com.pretriage.backend.model.personas.UsuarioAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoUsuariosAuth extends JpaRepository<UsuarioAuth, String> {
}
