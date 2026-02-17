package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.request.UpdateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.response.AvailabilityPeriodResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.grpc.ReservationGrpcClient;
import com.devoops.accommodation.mapper.AvailabilityPeriodMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import com.devoops.accommodation.repository.AvailabilityPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityPeriodService {

    private final AvailabilityPeriodRepository availabilityPeriodRepository;
    private final AccommodationRepository accommodationRepository;
    private final AvailabilityPeriodMapper availabilityPeriodMapper;
    private final ReservationGrpcClient reservationGrpcClient;

    @Transactional
    public AvailabilityPeriodResponse create(UUID accommodationId, CreateAvailabilityPeriodRequest request,
                                              UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(accommodationId);
        validateOwnership(accommodation, userContext);
        validateDates(request.startDate(), request.endDate());

        List<AvailabilityPeriod> overlapping = availabilityPeriodRepository.findOverlapping(
                accommodationId, request.startDate(), request.endDate());
        if (!overlapping.isEmpty()) {
            throw new OverlappingAvailabilityPeriodException(
                    "The availability period overlaps with an existing period");
        }

        AvailabilityPeriod period = availabilityPeriodMapper.toEntity(request);
        period.setAccommodationId(accommodationId);

        period = availabilityPeriodRepository.saveAndFlush(period);
        log.info("Created availability period {} for accommodation {}", period.getId(), accommodationId);
        return availabilityPeriodMapper.toResponse(period);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityPeriodResponse> getByAccommodationId(UUID accommodationId) {
        findAccommodationOrThrow(accommodationId);
        List<AvailabilityPeriod> periods = availabilityPeriodRepository
                .findByAccommodationIdOrderByStartDateAsc(accommodationId);
        return availabilityPeriodMapper.toResponseList(periods);
    }

    @Transactional(readOnly = true)
    public AvailabilityPeriodResponse getById(UUID accommodationId, UUID periodId) {
        AvailabilityPeriod period = findPeriodOrThrow(accommodationId, periodId);
        return availabilityPeriodMapper.toResponse(period);
    }

    @Transactional
    public AvailabilityPeriodResponse update(UUID accommodationId, UUID periodId,
                                              UpdateAvailabilityPeriodRequest request,
                                              UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(accommodationId);
        validateOwnership(accommodation, userContext);

        AvailabilityPeriod period = findPeriodOrThrow(accommodationId, periodId);

        // Check for approved reservations in the CURRENT period dates before allowing changes
        if (reservationGrpcClient.hasApprovedReservations(accommodationId,
                period.getStartDate(), period.getEndDate())) {
            throw new ReservationConflictException(
                    "Cannot modify availability period: approved reservations exist in this date range");
        }

        // Apply partial updates
        if (request.startDate() != null) {
            period.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            period.setEndDate(request.endDate());
        }
        if (request.pricePerDay() != null) {
            period.setPricePerDay(request.pricePerDay());
        }

        validateDates(period.getStartDate(), period.getEndDate());

        // Validate new dates don't overlap with other periods
        List<AvailabilityPeriod> overlapping = availabilityPeriodRepository.findOverlappingExcluding(
                accommodationId, period.getStartDate(), period.getEndDate(), periodId);
        if (!overlapping.isEmpty()) {
            throw new OverlappingAvailabilityPeriodException(
                    "The updated availability period overlaps with an existing period");
        }

        period = availabilityPeriodRepository.saveAndFlush(period);
        log.info("Updated availability period {} for accommodation {}", periodId, accommodationId);
        return availabilityPeriodMapper.toResponse(period);
    }

    @Transactional
    public void delete(UUID accommodationId, UUID periodId, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(accommodationId);
        validateOwnership(accommodation, userContext);

        AvailabilityPeriod period = findPeriodOrThrow(accommodationId, periodId);

        // Check for approved reservations before allowing deletion
        if (reservationGrpcClient.hasApprovedReservations(accommodationId,
                period.getStartDate(), period.getEndDate())) {
            throw new ReservationConflictException(
                    "Cannot delete availability period: approved reservations exist in this date range");
        }

        period.setDeleted(true);
        availabilityPeriodRepository.save(period);
        log.info("Deleted availability period {} for accommodation {}", periodId, accommodationId);
    }

    private Accommodation findAccommodationOrThrow(UUID accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new AccommodationNotFoundException(
                        "Accommodation not found with id: " + accommodationId));
    }

    private AvailabilityPeriod findPeriodOrThrow(UUID accommodationId, UUID periodId) {
        return availabilityPeriodRepository.findByIdAndAccommodationId(periodId, accommodationId)
                .orElseThrow(() -> new AvailabilityPeriodNotFoundException(
                        "Availability period not found with id: " + periodId));
    }

    private void validateOwnership(Accommodation accommodation, UserContext userContext) {
        if (!accommodation.getHostId().equals(userContext.userId())) {
            throw new ForbiddenException("You are not the owner of this accommodation");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }
}
