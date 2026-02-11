package com.devoops.accommodation.repository;

import com.devoops.accommodation.entity.Accommodation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccommodationRepository extends JpaRepository<Accommodation, UUID> {

    List<Accommodation> findByHostId(UUID hostId);
}
