package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.request.UpdateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AmenityType;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.AccommodationNotFoundException;
import com.devoops.accommodation.exception.ForbiddenException;
import com.devoops.accommodation.mapper.AccommodationMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private AccommodationMapper accommodationMapper;

    @InjectMocks
    private AccommodationService accommodationService;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UserContext HOST_CONTEXT = new UserContext(HOST_ID, "HOST");

    private Accommodation createAccommodation() {
        return Accommodation.builder()
                .id(ACCOMMODATION_ID)
                .hostId(HOST_ID)
                .name("Test Apartment")
                .address("123 Test St")
                .minGuests(1)
                .maxGuests(4)
                .pricingMode(PricingMode.PER_GUEST)
                .approvalMode(ApprovalMode.MANUAL)
                .build();
    }

    private AccommodationResponse createResponse() {
        return new AccommodationResponse(
                ACCOMMODATION_ID, HOST_ID, "Test Apartment", "123 Test St",
                1, 4, PricingMode.PER_GUEST, ApprovalMode.MANUAL,
                List.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("With valid request returns accommodation response")
        void create_WithValidRequest_ReturnsAccommodationResponse() {
            var request = new CreateAccommodationRequest(
                    "Test Apartment", "123 Test St", 1, 4,
                    PricingMode.PER_GUEST, ApprovalMode.MANUAL, null);
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationMapper.toEntity(request)).thenReturn(accommodation);
            when(accommodationRepository.saveAndFlush(accommodation)).thenReturn(accommodation);
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            AccommodationResponse result = accommodationService.create(request, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
            verify(accommodationRepository).saveAndFlush(accommodation);
        }

        @Test
        @DisplayName("With amenities sets amenities on entity")
        void create_WithAmenities_SetsAmenitiesOnEntity() {
            var amenities = Set.of(AmenityType.WIFI, AmenityType.PARKING);
            var request = new CreateAccommodationRequest(
                    "Test", "Addr", 1, 4,
                    PricingMode.PER_GUEST, ApprovalMode.MANUAL, amenities);
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationMapper.toEntity(request)).thenReturn(accommodation);
            when(accommodationRepository.saveAndFlush(accommodation)).thenReturn(accommodation);
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            accommodationService.create(request, HOST_CONTEXT);

            assertThat(accommodation.getAmenities()).containsExactlyInAnyOrderElementsOf(amenities);
        }

        @Test
        @DisplayName("With null amenities does not set amenities")
        void create_WithNullAmenities_DoesNotSetAmenities() {
            var request = new CreateAccommodationRequest(
                    "Test", "Addr", 1, 4,
                    PricingMode.PER_GUEST, ApprovalMode.MANUAL, null);
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationMapper.toEntity(request)).thenReturn(accommodation);
            when(accommodationRepository.saveAndFlush(accommodation)).thenReturn(accommodation);
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            accommodationService.create(request, HOST_CONTEXT);

            assertThat(accommodation.getAmenities()).isEmpty();
        }

        @Test
        @DisplayName("With min guests exceeding max throws IllegalArgumentException")
        void create_WithMinGuestsExceedingMax_ThrowsIllegalArgument() {
            var request = new CreateAccommodationRequest(
                    "Test", "Addr", 5, 2,
                    PricingMode.PER_GUEST, ApprovalMode.MANUAL, null);

            assertThatThrownBy(() -> accommodationService.create(request, HOST_CONTEXT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minimum guests cannot exceed maximum guests");
        }
    }

    @Nested
    @DisplayName("GetById")
    class GetByIdTests {

        @Test
        @DisplayName("With existing ID returns accommodation response")
        void getById_WithExistingId_ReturnsAccommodationResponse() {
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            AccommodationResponse result = accommodationService.getById(ACCOMMODATION_ID);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("With non-existing ID throws AccommodationNotFoundException")
        void getById_WithNonExistingId_ThrowsAccommodationNotFoundException() {
            UUID id = UUID.randomUUID();
            when(accommodationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accommodationService.getById(id))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("GetByHostId")
    class GetByHostIdTests {

        @Test
        @DisplayName("With existing host returns accommodation list")
        void getByHostId_WithExistingHost_ReturnsAccommodationList() {
            var accommodations = List.of(createAccommodation());
            var responses = List.of(createResponse());

            when(accommodationRepository.findByHostId(HOST_ID)).thenReturn(accommodations);
            when(accommodationMapper.toResponseList(accommodations)).thenReturn(responses);

            List<AccommodationResponse> result = accommodationService.getByHostId(HOST_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("With no accommodations returns empty list")
        void getByHostId_WithNoAccommodations_ReturnsEmptyList() {
            UUID hostId = UUID.randomUUID();
            when(accommodationRepository.findByHostId(hostId)).thenReturn(List.of());
            when(accommodationMapper.toResponseList(List.of())).thenReturn(List.of());

            List<AccommodationResponse> result = accommodationService.getByHostId(hostId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateTests {

        @Test
        @DisplayName("With valid request returns updated response")
        void update_WithValidRequest_ReturnsUpdatedResponse() {
            var request = new UpdateAccommodationRequest(
                    "Updated Name", "New Address", 2, 6,
                    PricingMode.PER_UNIT, ApprovalMode.AUTOMATIC, null);
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(accommodationRepository.saveAndFlush(accommodation)).thenReturn(accommodation);
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            AccommodationResponse result = accommodationService.update(ACCOMMODATION_ID, request, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
            assertThat(accommodation.getName()).isEqualTo("Updated Name");
            assertThat(accommodation.getAddress()).isEqualTo("New Address");
            assertThat(accommodation.getMinGuests()).isEqualTo(2);
            assertThat(accommodation.getMaxGuests()).isEqualTo(6);
        }

        @Test
        @DisplayName("With partial request only updates non-null fields")
        void update_WithPartialRequest_OnlyUpdatesNonNullFields() {
            var request = new UpdateAccommodationRequest(
                    "New Name", null, null, null, null, null, null);
            var accommodation = createAccommodation();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(accommodationRepository.saveAndFlush(accommodation)).thenReturn(accommodation);
            when(accommodationMapper.toResponse(accommodation)).thenReturn(response);

            accommodationService.update(ACCOMMODATION_ID, request, HOST_CONTEXT);

            assertThat(accommodation.getName()).isEqualTo("New Name");
            assertThat(accommodation.getAddress()).isEqualTo("123 Test St");
            assertThat(accommodation.getMinGuests()).isEqualTo(1);
            assertThat(accommodation.getMaxGuests()).isEqualTo(4);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void update_WithWrongOwner_ThrowsForbiddenException() {
            var request = new UpdateAccommodationRequest(
                    "Name", null, null, null, null, null, null);
            var accommodation = createAccommodation();
            var otherUser = new UserContext(UUID.randomUUID(), "HOST");

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> accommodationService.update(ACCOMMODATION_ID, request, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With non-existing ID throws AccommodationNotFoundException")
        void update_WithNonExistingId_ThrowsAccommodationNotFoundException() {
            UUID id = UUID.randomUUID();
            var request = new UpdateAccommodationRequest(
                    "Name", null, null, null, null, null, null);

            when(accommodationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accommodationService.update(id, request, HOST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }

        @Test
        @DisplayName("With min guests exceeding max after partial update throws IllegalArgumentException")
        void update_WithMinGuestsExceedingMax_ThrowsIllegalArgument() {
            var request = new UpdateAccommodationRequest(
                    null, null, 10, null, null, null, null);
            var accommodation = createAccommodation(); // maxGuests=4

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> accommodationService.update(ACCOMMODATION_ID, request, HOST_CONTEXT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Minimum guests cannot exceed maximum guests");
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("With valid owner soft-deletes accommodation")
        void delete_WithValidOwner_SoftDeletesAccommodation() {
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            accommodationService.delete(ACCOMMODATION_ID, HOST_CONTEXT);

            assertThat(accommodation.isDeleted()).isTrue();
            verify(accommodationRepository).save(accommodation);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void delete_WithWrongOwner_ThrowsForbiddenException() {
            var accommodation = createAccommodation();
            var otherUser = new UserContext(UUID.randomUUID(), "HOST");

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> accommodationService.delete(ACCOMMODATION_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With non-existing ID throws AccommodationNotFoundException")
        void delete_WithNonExistingId_ThrowsAccommodationNotFoundException() {
            UUID id = UUID.randomUUID();
            when(accommodationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accommodationService.delete(id, HOST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }
    }
}
