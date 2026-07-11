package com.pretriage.backend.repositories;

import com.pretriage.backend.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepoChat extends JpaRepository<Chat, Long> {
    Optional<Chat> findByIdAndPacienteUsuarioAuthId(Long id, String auth0Id);
}
