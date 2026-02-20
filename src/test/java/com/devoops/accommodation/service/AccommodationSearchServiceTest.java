package com.devoops.accommodation.service;

import com.devoops.accommodation.dto.response.AccommodationSearchResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.mapper.AccommodationMapper;
import com.devoops.accommodation.repository.AccommodationRepository;
import com.devoops.accommodation.repository.AvailabilityPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccommodationSearchServiceTest {

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private AccommodationMapper accommodationMapper;

    @Mock
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @InjectMocks
    private AccommodationService accommodationService;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();

    private Accommodation createAccommodation(PricingMode pricingMode) {
        return Accommodation.builder()
                .id(ACCOMMODATION_ID)
                .hostId(HOST_ID)
                .name("Test Apartment")
                .address("123 Belgrade St")
                .minGuests(1)
                .maxGuests(4)
                .pricingMode(pricingMode)
                .approvalMode(ApprovalMode.MANUAL)
                .build();
    }

    private AvailabilityPeriod createPeriod(BigDecimal pricePerDay) {
        return AvailabilityPeriod.builder()
                .id(UUID.randomUUID())
                .accommodationId(ACCOMMODATION_ID)
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 3, 31))
                .pricePerDay(pricePerDay)
                .build();
    }

    @Nested
    @DisplayName("Search")
    class SearchTests {

        @Test
        @DisplayName("With matching location and guests and covering period returns results")
        void search_WithMatchingCriteria_ReturnsResults() {
            var accommodation = createAccommodation(PricingMode.PER_GUEST);
            var period = createPeriod(new BigDecimal("50.00"));
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 2))
                    .thenReturn(List.of(accommodation));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period));

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 2, startDate, endDate, 0, 12);

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).id()).isEqualTo(ACCOMMODATION_ID);
            assertThat(results.getContent().get(0).name()).isEqualTo("Test Apartment");
            assertThat(results.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("With PER_GUEST pricing calculates total correctly")
        void search_WithPerGuestPricing_CalculatesTotalCorrectly() {
            var accommodation = createAccommodation(PricingMode.PER_GUEST);
            var period = createPeriod(new BigDecimal("50.00"));
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10); // 5 nights

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 2))
                    .thenReturn(List.of(accommodation));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period));

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 2, startDate, endDate, 0, 12);

            assertThat(results.getContent().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
            // 50 * 5 nights * 2 guests = 500
            assertThat(results.getContent().get(0).totalPrice()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(results.getContent().get(0).numberOfNights()).isEqualTo(5);
        }

        @Test
        @DisplayName("With PER_UNIT pricing calculates total without guest multiplier")
        void search_WithPerUnitPricing_CalculatesTotalWithoutGuestMultiplier() {
            var accommodation = createAccommodation(PricingMode.PER_UNIT);
            var period = createPeriod(new BigDecimal("100.00"));
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10); // 5 nights

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 3))
                    .thenReturn(List.of(accommodation));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period));

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 3, startDate, endDate, 0, 12);

            assertThat(results.getContent().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
            // 100 * 5 nights = 500 (no guest multiplier)
            assertThat(results.getContent().get(0).totalPrice()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(results.getContent().get(0).numberOfNights()).isEqualTo(5);
        }

        @Test
        @DisplayName("With no covering period excludes accommodation from results")
        void search_WithNoCoveringPeriod_ExcludesAccommodation() {
            var accommodation = createAccommodation(PricingMode.PER_GUEST);
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 2))
                    .thenReturn(List.of(accommodation));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.empty());

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 2, startDate, endDate, 0, 12);

            assertThat(results.getContent()).isEmpty();
            assertThat(results.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("With no matching location returns empty page")
        void search_WithNoMatchingLocation_ReturnsEmptyPage() {
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationRepository.searchByLocationAndGuests("Nowhere", 2))
                    .thenReturn(List.of());

            Page<AccommodationSearchResponse> results = accommodationService.search("Nowhere", 2, startDate, endDate, 0, 12);

            assertThat(results.getContent()).isEmpty();
            assertThat(results.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("With end date before start date throws IllegalArgumentException")
        void search_WithEndDateBeforeStartDate_ThrowsIllegalArgumentException() {
            var startDate = LocalDate.of(2026, 3, 10);
            var endDate = LocalDate.of(2026, 3, 5);

            assertThatThrownBy(() -> accommodationService.search("Belgrade", 2, startDate, endDate, 0, 12))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("With equal start and end date throws IllegalArgumentException")
        void search_WithEqualDates_ThrowsIllegalArgumentException() {
            var date = LocalDate.of(2026, 3, 10);

            assertThatThrownBy(() -> accommodationService.search("Belgrade", 2, date, date, 0, 12))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("With multiple candidates returns only those with availability")
        void search_WithMultipleCandidates_ReturnsOnlyAvailable() {
            var accommodation1 = createAccommodation(PricingMode.PER_GUEST);
            var id2 = UUID.randomUUID();
            var accommodation2 = Accommodation.builder()
                    .id(id2)
                    .hostId(HOST_ID)
                    .name("Second Apartment")
                    .address("456 Belgrade Ave")
                    .minGuests(1)
                    .maxGuests(4)
                    .pricingMode(PricingMode.PER_UNIT)
                    .approvalMode(ApprovalMode.AUTOMATIC)
                    .build();
            var period = createPeriod(new BigDecimal("75.00"));
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 2))
                    .thenReturn(List.of(accommodation1, accommodation2));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period));
            when(availabilityPeriodRepository.findCoveringPeriod(id2, startDate, endDate))
                    .thenReturn(Optional.empty());

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 2, startDate, endDate, 0, 12);

            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).id()).isEqualTo(ACCOMMODATION_ID);
            assertThat(results.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("With single night stay calculates price correctly")
        void search_WithSingleNight_CalculatesPriceCorrectly() {
            var accommodation = createAccommodation(PricingMode.PER_GUEST);
            var period = createPeriod(new BigDecimal("80.00"));
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 6); // 1 night

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 1))
                    .thenReturn(List.of(accommodation));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period));

            Page<AccommodationSearchResponse> results = accommodationService.search("Belgrade", 1, startDate, endDate, 0, 12);

            assertThat(results.getContent().get(0).numberOfNights()).isEqualTo(1);
            // 80 * 1 night * 1 guest = 80
            assertThat(results.getContent().get(0).totalPrice()).isEqualByComparingTo(new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("Pagination returns correct page slice")
        void search_WithPagination_ReturnsCorrectSlice() {
            var accommodation1 = createAccommodation(PricingMode.PER_GUEST);
            var id2 = UUID.randomUUID();
            var accommodation2 = Accommodation.builder()
                    .id(id2).hostId(HOST_ID).name("Second").address("Belgrade")
                    .minGuests(1).maxGuests(4).pricingMode(PricingMode.PER_GUEST)
                    .approvalMode(ApprovalMode.MANUAL).build();
            var period1 = createPeriod(new BigDecimal("50.00"));
            var period2 = AvailabilityPeriod.builder()
                    .id(UUID.randomUUID()).accommodationId(id2)
                    .startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 31))
                    .pricePerDay(new BigDecimal("60.00")).build();
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationRepository.searchByLocationAndGuests("Belgrade", 2))
                    .thenReturn(List.of(accommodation1, accommodation2));
            when(availabilityPeriodRepository.findCoveringPeriod(ACCOMMODATION_ID, startDate, endDate))
                    .thenReturn(Optional.of(period1));
            when(availabilityPeriodRepository.findCoveringPeriod(id2, startDate, endDate))
                    .thenReturn(Optional.of(period2));

            // Page 0, size 1 — should return first result only
            Page<AccommodationSearchResponse> page0 = accommodationService.search("Belgrade", 2, startDate, endDate, 0, 1);
            assertThat(page0.getContent()).hasSize(1);
            assertThat(page0.getTotalElements()).isEqualTo(2);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.isLast()).isFalse();

            // Page 1, size 1 — should return second result only
            Page<AccommodationSearchResponse> page1 = accommodationService.search("Belgrade", 2, startDate, endDate, 1, 1);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getTotalElements()).isEqualTo(2);
            assertThat(page1.isLast()).isTrue();
        }
    }
}
