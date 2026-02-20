package com.devoops.accommodation.repository;

import com.devoops.accommodation.entity.Accommodation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccommodationRepository extends JpaRepository<Accommodation, UUID> {

    List<Accommodation> findByHostId(UUID hostId);

    @Query("""
            SELECT a FROM Accommodation a
            WHERE LOWER(a.address) LIKE LOWER(CONCAT('%', :location, '%'))
            AND a.minGuests <= :guests
            AND a.maxGuests >= :guests
            ORDER BY a.createdAt DESC
            """)
    List<Accommodation> searchByLocationAndGuests(
            @Param("location") String location,
            @Param("guests") int guests);
}
