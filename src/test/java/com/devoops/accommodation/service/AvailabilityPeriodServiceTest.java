package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.request.UpdateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.response.AvailabilityPeriodResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.grpc.ReservationGrpcClient;
import com.devoops.accommodation.mapper.AvailabilityPeriodMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import com.devoops.accommodation.repository.AvailabilityPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityPeriodServiceTest {

    @Mock
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private AvailabilityPeriodMapper availabilityPeriodMapper;

    @Mock
    private ReservationGrpcClient reservationGrpcClient;

    @InjectMocks
    private AvailabilityPeriodService availabilityPeriodService;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UserContext HOST_CONTEXT = new UserContext(HOST_ID, "HOST");
    private static final LocalDate START_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 6, 30);

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

    private AvailabilityPeriod createPeriod() {
        return AvailabilityPeriod.builder()
                .id(PERIOD_ID)
                .accommodationId(ACCOMMODATION_ID)
                .startDate(START_DATE)
                .endDate(END_DATE)
                .pricePerDay(new BigDecimal("100.00"))
                .build();
    }

    private AvailabilityPeriodResponse createResponse() {
        return new AvailabilityPeriodResponse(
                PERIOD_ID, ACCOMMODATION_ID, START_DATE, END_DATE,
                new BigDecimal("100.00"), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("With valid request returns availability period response")
        void create_WithValidRequest_ReturnsResponse() {
            var request = new CreateAvailabilityPeriodRequest(START_DATE, END_DATE, new BigDecimal("100.00"));
            var accommodation = createAccommodation();
            var period = createPeriod();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findOverlapping(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(List.of());
            when(availabilityPeriodMapper.toEntity(request)).thenReturn(period);
            when(availabilityPeriodRepository.saveAndFlush(period)).thenReturn(period);
            when(availabilityPeriodMapper.toResponse(period)).thenReturn(response);

            AvailabilityPeriodResponse result = availabilityPeriodService.create(ACCOMMODATION_ID, request, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
            verify(availabilityPeriodRepository).saveAndFlush(period);
        }

        @Test
        @DisplayName("With non-existing accommodation throws AccommodationNotFoundException")
        void create_WithNonExistingAccommodation_ThrowsNotFound() {
            var request = new CreateAvailabilityPeriodRequest(START_DATE, END_DATE, new BigDecimal("100.00"));
            UUID id = UUID.randomUUID();

            when(accommodationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> availabilityPeriodService.create(id, request, HOST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void create_WithWrongOwner_ThrowsForbidden() {
            var request = new CreateAvailabilityPeriodRequest(START_DATE, END_DATE, new BigDecimal("100.00"));
            var accommodation = createAccommodation();
            var otherUser = new UserContext(UUID.randomUUID(), "HOST");

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> availabilityPeriodService.create(ACCOMMODATION_ID, request, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With invalid dates throws IllegalArgumentException")
        void create_WithInvalidDates_ThrowsIllegalArgument() {
            var request = new CreateAvailabilityPeriodRequest(END_DATE, START_DATE, new BigDecimal("100.00"));
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> availabilityPeriodService.create(ACCOMMODATION_ID, request, HOST_CONTEXT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("With overlapping period throws OverlappingAvailabilityPeriodException")
        void create_WithOverlappingPeriod_ThrowsOverlapping() {
            var request = new CreateAvailabilityPeriodRequest(START_DATE, END_DATE, new BigDecimal("100.00"));
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findOverlapping(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(List.of(createPeriod()));

            assertThatThrownBy(() -> availabilityPeriodService.create(ACCOMMODATION_ID, request, HOST_CONTEXT))
                    .isInstanceOf(OverlappingAvailabilityPeriodException.class);
        }
    }

    @Nested
    @DisplayName("GetByAccommodationId")
    class GetByAccommodationIdTests {

        @Test
        @DisplayName("Returns sorted list of periods")
        void getByAccommodationId_ReturnsSortedList() {
            var accommodation = createAccommodation();
            var periods = List.of(createPeriod());
            var responses = List.of(createResponse());

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByAccommodationIdOrderByStartDateAsc(ACCOMMODATION_ID))
                    .thenReturn(periods);
            when(availabilityPeriodMapper.toResponseList(periods)).thenReturn(responses);

            List<AvailabilityPeriodResponse> result = availabilityPeriodService.getByAccommodationId(ACCOMMODATION_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("With non-existing accommodation throws AccommodationNotFoundException")
        void getByAccommodationId_WithNonExisting_ThrowsNotFound() {
            UUID id = UUID.randomUUID();
            when(accommodationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> availabilityPeriodService.getByAccommodationId(id))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateTests {

        @Test
        @DisplayName("With valid request returns updated response")
        void update_WithValidRequest_ReturnsUpdatedResponse() {
            var request = new UpdateAvailabilityPeriodRequest(null, null, new BigDecimal("150.00"));
            var accommodation = createAccommodation();
            var period = createPeriod();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(false);
            when(availabilityPeriodRepository.findOverlappingExcluding(ACCOMMODATION_ID, START_DATE, END_DATE, PERIOD_ID))
                    .thenReturn(List.of());
            when(availabilityPeriodRepository.saveAndFlush(period)).thenReturn(period);
            when(availabilityPeriodMapper.toResponse(period)).thenReturn(response);

            AvailabilityPeriodResponse result = availabilityPeriodService.update(
                    ACCOMMODATION_ID, PERIOD_ID, request, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
            assertThat(period.getPricePerDay()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("With partial date update applies only provided fields")
        void update_WithPartialDates_AppliesOnlyProvided() {
            LocalDate newEndDate = LocalDate.of(2026, 7, 15);
            var request = new UpdateAvailabilityPeriodRequest(null, newEndDate, null);
            var accommodation = createAccommodation();
            var period = createPeriod();
            var response = createResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(false);
            when(availabilityPeriodRepository.findOverlappingExcluding(ACCOMMODATION_ID, START_DATE, newEndDate, PERIOD_ID))
                    .thenReturn(List.of());
            when(availabilityPeriodRepository.saveAndFlush(period)).thenReturn(period);
            when(availabilityPeriodMapper.toResponse(period)).thenReturn(response);

            availabilityPeriodService.update(ACCOMMODATION_ID, PERIOD_ID, request, HOST_CONTEXT);

            assertThat(period.getStartDate()).isEqualTo(START_DATE);
            assertThat(period.getEndDate()).isEqualTo(newEndDate);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void update_WithWrongOwner_ThrowsForbidden() {
            var request = new UpdateAvailabilityPeriodRequest(null, null, new BigDecimal("150.00"));
            var accommodation = createAccommodation();
            var otherUser = new UserContext(UUID.randomUUID(), "HOST");

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> availabilityPeriodService.update(
                    ACCOMMODATION_ID, PERIOD_ID, request, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With non-existing period throws AvailabilityPeriodNotFoundException")
        void update_WithNonExistingPeriod_ThrowsNotFound() {
            var request = new UpdateAvailabilityPeriodRequest(null, null, new BigDecimal("150.00"));
            var accommodation = createAccommodation();
            UUID missingPeriodId = UUID.randomUUID();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(missingPeriodId, ACCOMMODATION_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> availabilityPeriodService.update(
                    ACCOMMODATION_ID, missingPeriodId, request, HOST_CONTEXT))
                    .isInstanceOf(AvailabilityPeriodNotFoundException.class);
        }

        @Test
        @DisplayName("With existing reservations throws ReservationConflictException")
        void update_WithExistingReservations_ThrowsConflict() {
            var request = new UpdateAvailabilityPeriodRequest(null, null, new BigDecimal("150.00"));
            var accommodation = createAccommodation();
            var period = createPeriod();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(true);

            assertThatThrownBy(() -> availabilityPeriodService.update(
                    ACCOMMODATION_ID, PERIOD_ID, request, HOST_CONTEXT))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        @DisplayName("With overlapping period after update throws OverlappingAvailabilityPeriodException")
        void update_WithOverlappingPeriod_ThrowsOverlapping() {
            var request = new UpdateAvailabilityPeriodRequest(null, null, new BigDecimal("150.00"));
            var accommodation = createAccommodation();
            var period = createPeriod();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(false);
            when(availabilityPeriodRepository.findOverlappingExcluding(ACCOMMODATION_ID, START_DATE, END_DATE, PERIOD_ID))
                    .thenReturn(List.of(AvailabilityPeriod.builder().id(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> availabilityPeriodService.update(
                    ACCOMMODATION_ID, PERIOD_ID, request, HOST_CONTEXT))
                    .isInstanceOf(OverlappingAvailabilityPeriodException.class);
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("With valid owner soft-deletes period")
        void delete_WithValidOwner_SoftDeletesPeriod() {
            var accommodation = createAccommodation();
            var period = createPeriod();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(false);

            availabilityPeriodService.delete(ACCOMMODATION_ID, PERIOD_ID, HOST_CONTEXT);

            assertThat(period.isDeleted()).isTrue();
            verify(availabilityPeriodRepository).save(period);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void delete_WithWrongOwner_ThrowsForbidden() {
            var accommodation = createAccommodation();
            var otherUser = new UserContext(UUID.randomUUID(), "HOST");

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> availabilityPeriodService.delete(ACCOMMODATION_ID, PERIOD_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With non-existing period throws AvailabilityPeriodNotFoundException")
        void delete_WithNonExistingPeriod_ThrowsNotFound() {
            var accommodation = createAccommodation();
            UUID missingPeriodId = UUID.randomUUID();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(missingPeriodId, ACCOMMODATION_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> availabilityPeriodService.delete(ACCOMMODATION_ID, missingPeriodId, HOST_CONTEXT))
                    .isInstanceOf(AvailabilityPeriodNotFoundException.class);
        }

        @Test
        @DisplayName("With existing reservations throws ReservationConflictException")
        void delete_WithExistingReservations_ThrowsConflict() {
            var accommodation = createAccommodation();
            var period = createPeriod();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(availabilityPeriodRepository.findByIdAndAccommodationId(PERIOD_ID, ACCOMMODATION_ID))
                    .thenReturn(Optional.of(period));
            when(reservationGrpcClient.hasApprovedReservations(ACCOMMODATION_ID, START_DATE, END_DATE))
                    .thenReturn(true);

            assertThatThrownBy(() -> availabilityPeriodService.delete(ACCOMMODATION_ID, PERIOD_ID, HOST_CONTEXT))
                    .isInstanceOf(ReservationConflictException.class);
        }
    }
}
