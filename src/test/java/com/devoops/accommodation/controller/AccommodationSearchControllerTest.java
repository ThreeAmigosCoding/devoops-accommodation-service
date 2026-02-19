package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RoleAuthorizationInterceptor;
import com.devoops.accommodation.config.UserContextResolver;
import com.devoops.accommodation.dto.response.AccommodationSearchResponse;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.GlobalExceptionHandler;
import com.devoops.accommodation.service.AccommodationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccommodationSearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccommodationService accommodationService;

    @InjectMocks
    private AccommodationController accommodationController;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accommodationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextResolver())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .build();
    }

    private AccommodationSearchResponse createSearchResponse() {
        return new AccommodationSearchResponse(
                ACCOMMODATION_ID, HOST_ID, "Test Apartment", "123 Belgrade St",
                1, 4, PricingMode.PER_GUEST, ApprovalMode.MANUAL,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                new BigDecimal("500.00"), new BigDecimal("50.00"), 5
        );
    }

    @Nested
    @DisplayName("GET /api/accommodation/search")
    class SearchEndpoint {

        @Test
        @DisplayName("With valid parameters returns 200 with results")
        void search_WithValidParams_Returns200WithResults() throws Exception {
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationService.search("Belgrade", 2, startDate, endDate))
                    .thenReturn(List.of(createSearchResponse()));

            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Belgrade")
                            .param("guests", "2")
                            .param("startDate", "2026-03-05")
                            .param("endDate", "2026-03-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ACCOMMODATION_ID.toString()))
                    .andExpect(jsonPath("$[0].name").value("Test Apartment"))
                    .andExpect(jsonPath("$[0].totalPrice").value(500.00))
                    .andExpect(jsonPath("$[0].unitPrice").value(50.00))
                    .andExpect(jsonPath("$[0].numberOfNights").value(5));
        }

        @Test
        @DisplayName("With no results returns 200 with empty list")
        void search_WithNoResults_Returns200WithEmptyList() throws Exception {
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationService.search("Nowhere", 2, startDate, endDate))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Nowhere")
                            .param("guests", "2")
                            .param("startDate", "2026-03-05")
                            .param("endDate", "2026-03-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Without auth headers still returns 200 (public endpoint)")
        void search_WithoutAuthHeaders_Returns200() throws Exception {
            var startDate = LocalDate.of(2026, 3, 5);
            var endDate = LocalDate.of(2026, 3, 10);

            when(accommodationService.search("Belgrade", 2, startDate, endDate))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Belgrade")
                            .param("guests", "2")
                            .param("startDate", "2026-03-05")
                            .param("endDate", "2026-03-10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("With missing location parameter returns 400")
        void search_WithMissingLocation_Returns400() throws Exception {
            mockMvc.perform(get("/api/accommodation/search")
                            .param("guests", "2")
                            .param("startDate", "2026-03-05")
                            .param("endDate", "2026-03-10"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With missing guests parameter returns 400")
        void search_WithMissingGuests_Returns400() throws Exception {
            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Belgrade")
                            .param("startDate", "2026-03-05")
                            .param("endDate", "2026-03-10"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With missing date parameters returns 400")
        void search_WithMissingDates_Returns400() throws Exception {
            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Belgrade")
                            .param("guests", "2"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With invalid date returns IllegalArgumentException")
        void search_WithInvalidDates_Returns400() throws Exception {
            var startDate = LocalDate.of(2026, 3, 10);
            var endDate = LocalDate.of(2026, 3, 5);

            when(accommodationService.search("Belgrade", 2, startDate, endDate))
                    .thenThrow(new IllegalArgumentException("End date must be after start date"));

            mockMvc.perform(get("/api/accommodation/search")
                            .param("location", "Belgrade")
                            .param("guests", "2")
                            .param("startDate", "2026-03-10")
                            .param("endDate", "2026-03-05"))
                    .andExpect(status().isBadRequest());
        }
    }
}
