package com.pretriage.backend.repositories;

import com.pretriage.backend.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoChat extends JpaRepository<Chat, Long> {
    Optional<Chat> findByIdAndPacienteUsuarioAuthId(Long id, String auth0Id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chat c where c.id = :id and c.paciente.usuarioAuth.id = :auth0Id")
    Optional<Chat> findForUpdate(@Param("id") Long id, @Param("auth0Id") String auth0Id);
    Optional<Chat> findFirstByPacienteUsuarioAuthIdOrderByFechaHoraCreacionDescIdDesc(String auth0Id);

    Optional<Chat> findFirstByPacienteUsuarioAuthIdAndFinalizadoFalse(String auth0Id);

    List<Chat> findAllByPacienteUsuarioAuthIdAndFinalizadoFalse(String auth0Id);
}
