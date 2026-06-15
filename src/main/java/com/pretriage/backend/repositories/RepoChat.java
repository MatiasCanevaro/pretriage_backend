package com.pretriage.backend.repositories;

import com.pretriage.backend.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mapping.model.ClassGeneratingPropertyAccessorFactory;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoChat extends JpaRepository<Chat,Long> {

}
