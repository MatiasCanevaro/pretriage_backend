package com.pretriage.backend.repositories;

import com.pretriage.backend.model.hospitales.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepoHospitales extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByPlaceId(String placeId);
}
