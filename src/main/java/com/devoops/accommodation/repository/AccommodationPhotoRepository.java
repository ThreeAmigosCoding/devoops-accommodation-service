package com.devoops.accommodation.repository;

import com.devoops.accommodation.entity.AccommodationPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccommodationPhotoRepository extends JpaRepository<AccommodationPhoto, UUID> {

    List<AccommodationPhoto> findByAccommodationIdOrderByDisplayOrderAsc(UUID accommodationId);

    Optional<AccommodationPhoto> findByIdAndAccommodationId(UUID id, UUID accommodationId);

    long countByAccommodationId(UUID accommodationId);

    @Query("SELECT COALESCE(MAX(p.displayOrder), -1) FROM AccommodationPhoto p WHERE p.accommodationId = :accommodationId")
    Integer findMaxDisplayOrder(@Param("accommodationId") UUID accommodationId);
}
