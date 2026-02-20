package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.request.UpdateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.dto.response.AccommodationSearchResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.AccommodationNotFoundException;
import com.devoops.accommodation.exception.ForbiddenException;
import com.devoops.accommodation.mapper.AccommodationMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import com.devoops.accommodation.repository.AvailabilityPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationMapper accommodationMapper;
    private final AvailabilityPeriodRepository availabilityPeriodRepository;

    @Transactional
    public AccommodationResponse create(CreateAccommodationRequest request, UserContext userContext) {
        validateGuestCapacity(request.minGuests(), request.maxGuests());

        Accommodation accommodation = accommodationMapper.toEntity(request);
        accommodation.setHostId(userContext.userId());

        if (request.amenities() != null) {
            accommodation.setAmenities(new ArrayList<>(request.amenities()));
        }

        accommodation = accommodationRepository.saveAndFlush(accommodation);
        return accommodationMapper.toResponse(accommodation);
    }

    @Transactional(readOnly = true)
    public AccommodationResponse getById(UUID id) {
        Accommodation accommodation = findAccommodationOrThrow(id);
        return accommodationMapper.toResponse(accommodation);
    }

    @Transactional(readOnly = true)
    public List<AccommodationResponse> getByHostId(UUID hostId) {
        List<Accommodation> accommodations = accommodationRepository.findByHostId(hostId);
        return accommodationMapper.toResponseList(accommodations);
    }

    @Transactional(readOnly = true)
    public Page<AccommodationResponse> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Accommodation> accommodations = accommodationRepository.findAll(pageable);
        return accommodations.map(accommodationMapper::toResponse);
    }

    @Transactional
    public AccommodationResponse update(UUID id, UpdateAccommodationRequest request, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(id);
        validateOwnership(accommodation, userContext);

        if (request.name() != null) {
            accommodation.setName(request.name());
        }
        if (request.address() != null) {
            accommodation.setAddress(request.address());
        }
        if (request.minGuests() != null) {
            accommodation.setMinGuests(request.minGuests());
        }
        if (request.maxGuests() != null) {
            accommodation.setMaxGuests(request.maxGuests());
        }
        if (request.pricingMode() != null) {
            accommodation.setPricingMode(request.pricingMode());
        }
        if (request.approvalMode() != null) {
            accommodation.setApprovalMode(request.approvalMode());
        }

        validateGuestCapacity(accommodation.getMinGuests(), accommodation.getMaxGuests());

        if (request.amenities() != null) {
            accommodation.setAmenities(new ArrayList<>(request.amenities()));
        }

        accommodation = accommodationRepository.saveAndFlush(accommodation);
        return accommodationMapper.toResponse(accommodation);
    }

    @Transactional
    public void delete(UUID id, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(id);
        validateOwnership(accommodation, userContext);

        accommodation.setDeleted(true);
        accommodationRepository.save(accommodation);
    }

    /**
     * Soft delete all accommodations owned by a host.
     * Used when a host deletes their account (cascade deletion).
     *
     * @param hostId the ID of the host whose accommodations should be deleted
     * @return the number of accommodations soft-deleted
     */
    @Transactional
    public int deleteAllByHostId(UUID hostId) {
        return accommodationRepository.softDeleteByHostId(hostId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Page<AccommodationSearchResponse> search(String location, int guests, LocalDate startDate, LocalDate endDate, int page, int size) {
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        List<Accommodation> candidates = accommodationRepository.searchByLocationAndGuests(location, guests);
        List<AccommodationSearchResponse> allResults = new ArrayList<>();

        for (Accommodation accommodation : candidates) {
            Optional<AvailabilityPeriod> coveringPeriod = availabilityPeriodRepository
                    .findCoveringPeriod(accommodation.getId(), startDate, endDate);

            if (coveringPeriod.isPresent()) {
                AvailabilityPeriod period = coveringPeriod.get();
                BigDecimal unitPrice = period.getPricePerDay();
                BigDecimal totalPrice;

                if (accommodation.getPricingMode() == PricingMode.PER_GUEST) {
                    totalPrice = unitPrice.multiply(BigDecimal.valueOf(nights)).multiply(BigDecimal.valueOf(guests));
                } else {
                    totalPrice = unitPrice.multiply(BigDecimal.valueOf(nights));
                }

                allResults.add(new AccommodationSearchResponse(
                        accommodation.getId(),
                        accommodation.getHostId(),
                        accommodation.getName(),
                        accommodation.getAddress(),
                        accommodation.getMinGuests(),
                        accommodation.getMaxGuests(),
                        accommodation.getPricingMode(),
                        accommodation.getApprovalMode(),
                        accommodation.getAmenities(),
                        accommodation.getCreatedAt(),
                        accommodation.getUpdatedAt(),
                        totalPrice,
                        unitPrice,
                        (int) nights
                ));
            }
        }

        int start = page * size;
        int end = Math.min(start + size, allResults.size());
        List<AccommodationSearchResponse> pageContent = start >= allResults.size()
                ? List.of()
                : allResults.subList(start, end);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), allResults.size());
    }

    private Accommodation findAccommodationOrThrow(UUID id) {
        return accommodationRepository.findById(id)
                .orElseThrow(() -> new AccommodationNotFoundException("Accommodation not found with id: " + id));
    }

    private void validateOwnership(Accommodation accommodation, UserContext userContext) {
        if (!accommodation.getHostId().equals(userContext.userId())) {
            throw new ForbiddenException("You are not the owner of this accommodation");
        }
    }

    private void validateGuestCapacity(int minGuests, int maxGuests) {
        if (minGuests > maxGuests) {
            throw new IllegalArgumentException("Minimum guests cannot exceed maximum guests");
        }
    }
}
