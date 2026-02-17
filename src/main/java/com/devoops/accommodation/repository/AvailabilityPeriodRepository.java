package com.devoops.accommodation.repository;

import com.devoops.accommodation.entity.AvailabilityPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityPeriodRepository extends JpaRepository<AvailabilityPeriod, UUID> {

    List<AvailabilityPeriod> findByAccommodationIdOrderByStartDateAsc(UUID accommodationId);

    Optional<AvailabilityPeriod> findByIdAndAccommodationId(UUID id, UUID accommodationId);

    @Query("""
            SELECT ap FROM AvailabilityPeriod ap
            WHERE ap.accommodationId = :accommodationId
            AND ap.startDate < :endDate
            AND ap.endDate > :startDate
            """)
    List<AvailabilityPeriod> findOverlapping(
            @Param("accommodationId") UUID accommodationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT ap FROM AvailabilityPeriod ap
            WHERE ap.accommodationId = :accommodationId
            AND ap.id != :excludeId
            AND ap.startDate < :endDate
            AND ap.endDate > :startDate
            """)
    List<AvailabilityPeriod> findOverlappingExcluding(
            @Param("accommodationId") UUID accommodationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId
    );
}
