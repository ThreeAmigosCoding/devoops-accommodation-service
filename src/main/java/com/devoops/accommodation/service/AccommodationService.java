package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.request.UpdateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.Amenity;
import com.devoops.accommodation.entity.AmenityType;
import com.devoops.accommodation.exception.AccommodationNotFoundException;
import com.devoops.accommodation.exception.ForbiddenException;
import com.devoops.accommodation.mapper.AccommodationMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationMapper accommodationMapper;
    private final EntityManager entityManager;

    @Transactional
    public AccommodationResponse create(CreateAccommodationRequest request, UserContext userContext) {
        validateGuestCapacity(request.minGuests(), request.maxGuests());

        Accommodation accommodation = accommodationMapper.toEntity(request);
        accommodation.setHostId(userContext.userId());

        if (request.amenities() != null) {
            for (AmenityType type : request.amenities()) {
                Amenity amenity = Amenity.builder().type(type).build();
                accommodation.addAmenity(amenity);
            }
        }

        accommodation = accommodationRepository.save(accommodation);
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
            accommodation.getAmenities().clear();
            entityManager.flush();
            for (AmenityType type : request.amenities()) {
                Amenity amenity = Amenity.builder().type(type).build();
                accommodation.addAmenity(amenity);
            }
        }

        accommodation = accommodationRepository.save(accommodation);
        return accommodationMapper.toResponse(accommodation);
    }

    @Transactional
    public void delete(UUID id, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(id);
        validateOwnership(accommodation, userContext);

        accommodation.setDeleted(true);
        accommodation.getAmenities().forEach(amenity -> amenity.setDeleted(true));
        accommodationRepository.save(accommodation);
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
