package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RoleAuthorizationInterceptor;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.config.UserContextResolver;
import com.devoops.accommodation.dto.response.AvailabilityPeriodResponse;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.service.AvailabilityPeriodService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityPeriodControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AvailabilityPeriodService availabilityPeriodService;

    @InjectMocks
    private AvailabilityPeriodController availabilityPeriodController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final String BASE_PATH = "/api/accommodation/" + ACCOMMODATION_ID + "/availability";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(availabilityPeriodController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextResolver())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .build();
    }

    private AvailabilityPeriodResponse createResponse() {
        return new AvailabilityPeriodResponse(
                PERIOD_ID, ACCOMMODATION_ID,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("100.00"),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private Map<String, Object> validCreateRequest() {
        return Map.of(
                "startDate", "2026-06-01",
                "endDate", "2026-06-30",
                "pricePerDay", 100.00
        );
    }

    @Nested
    @DisplayName("POST /api/accommodation/{accommodationId}/availability")
    class CreateEndpoint {

        @Test
        @DisplayName("With valid request returns 201")
        void create_WithValidRequest_Returns201() throws Exception {
            when(availabilityPeriodService.create(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenReturn(createResponse());

            mockMvc.perform(post(BASE_PATH)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(PERIOD_ID.toString()))
                    .andExpect(jsonPath("$.accommodationId").value(ACCOMMODATION_ID.toString()));
        }

        @Test
        @DisplayName("With missing auth headers returns 401")
        void create_WithMissingAuth_Returns401() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void create_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With missing required field returns 400")
        void create_WithMissingField_Returns400() throws Exception {
            var request = Map.of(
                    "startDate", "2026-06-01",
                    "endDate", "2026-06-30"
                    // missing pricePerDay
            );

            mockMvc.perform(post(BASE_PATH)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/{accommodationId}/availability")
    class GetAllEndpoint {

        @Test
        @DisplayName("Returns 200 with list")
        void getAll_Returns200WithList() throws Exception {
            when(availabilityPeriodService.getByAccommodationId(ACCOMMODATION_ID))
                    .thenReturn(List.of(createResponse()));

            mockMvc.perform(get(BASE_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(PERIOD_ID.toString()));
        }

        @Test
        @DisplayName("With non-existing accommodation returns 404")
        void getAll_WithNonExisting_Returns404() throws Exception {
            when(availabilityPeriodService.getByAccommodationId(ACCOMMODATION_ID))
                    .thenThrow(new AccommodationNotFoundException("Not found"));

            mockMvc.perform(get(BASE_PATH))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/{accommodationId}/availability/{periodId}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("With existing ID returns 200")
        void getById_Returns200() throws Exception {
            when(availabilityPeriodService.getById(ACCOMMODATION_ID, PERIOD_ID))
                    .thenReturn(createResponse());

            mockMvc.perform(get(BASE_PATH + "/" + PERIOD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(PERIOD_ID.toString()));
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void getById_WithNonExisting_Returns404() throws Exception {
            UUID missingId = UUID.randomUUID();
            when(availabilityPeriodService.getById(ACCOMMODATION_ID, missingId))
                    .thenThrow(new AvailabilityPeriodNotFoundException("Not found"));

            mockMvc.perform(get(BASE_PATH + "/" + missingId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/accommodation/{accommodationId}/availability/{periodId}")
    class UpdateEndpoint {

        @Test
        @DisplayName("With valid request returns 200")
        void update_WithValidRequest_Returns200() throws Exception {
            when(availabilityPeriodService.update(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(), any(UserContext.class)))
                    .thenReturn(createResponse());

            var request = Map.of("pricePerDay", 150.00);

            mockMvc.perform(put(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void update_WithWrongOwner_Returns403() throws Exception {
            when(availabilityPeriodService.update(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(), any(UserContext.class)))
                    .thenThrow(new ForbiddenException("Not the owner"));

            var request = Map.of("pricePerDay", 150.00);

            mockMvc.perform(put(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With reservation conflict returns 409")
        void update_WithReservationConflict_Returns409() throws Exception {
            when(availabilityPeriodService.update(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(), any(UserContext.class)))
                    .thenThrow(new ReservationConflictException("Reservations exist"));

            var request = Map.of("pricePerDay", 150.00);

            mockMvc.perform(put(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/accommodation/{accommodationId}/availability/{periodId}")
    class DeleteEndpoint {

        @Test
        @DisplayName("With valid request returns 204")
        void delete_WithValidRequest_Returns204() throws Exception {
            doNothing().when(availabilityPeriodService)
                    .delete(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(UserContext.class));

            mockMvc.perform(delete(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void delete_WithWrongOwner_Returns403() throws Exception {
            doThrow(new ForbiddenException("Not the owner"))
                    .when(availabilityPeriodService).delete(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(UserContext.class));

            mockMvc.perform(delete(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-existing period returns 404")
        void delete_WithNonExisting_Returns404() throws Exception {
            UUID missingId = UUID.randomUUID();
            doThrow(new AvailabilityPeriodNotFoundException("Not found"))
                    .when(availabilityPeriodService).delete(eq(ACCOMMODATION_ID), eq(missingId), any(UserContext.class));

            mockMvc.perform(delete(BASE_PATH + "/" + missingId)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With reservation conflict returns 409")
        void delete_WithReservationConflict_Returns409() throws Exception {
            doThrow(new ReservationConflictException("Reservations exist"))
                    .when(availabilityPeriodService).delete(eq(ACCOMMODATION_ID), eq(PERIOD_ID), any(UserContext.class));

            mockMvc.perform(delete(BASE_PATH + "/" + PERIOD_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isConflict());
        }
    }
}
