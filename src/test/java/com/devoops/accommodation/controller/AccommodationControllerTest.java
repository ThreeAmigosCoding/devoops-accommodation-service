package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RoleAuthorizationInterceptor;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.config.UserContextResolver;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.AccommodationNotFoundException;
import com.devoops.accommodation.exception.ForbiddenException;
import com.devoops.accommodation.exception.GlobalExceptionHandler;
import com.devoops.accommodation.service.AccommodationService;
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
class AccommodationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccommodationService accommodationService;

    @InjectMocks
    private AccommodationController accommodationController;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private AccommodationResponse createResponse() {
        return new AccommodationResponse(
                ACCOMMODATION_ID, HOST_ID, "Test Apartment", "123 Test St",
                1, 4, PricingMode.PER_GUEST, ApprovalMode.MANUAL,
                List.of(), LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private Map<String, Object> validCreateRequest() {
        return Map.of(
                "name", "Test Apartment",
                "address", "123 Test St",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );
    }

    @Nested
    @DisplayName("POST /api/accommodation")
    class CreateEndpoint {

        @Test
        @DisplayName("With valid request returns 201")
        void create_WithValidRequest_Returns201() throws Exception {
            when(accommodationService.create(any(), any(UserContext.class)))
                    .thenReturn(createResponse());

            mockMvc.perform(post("/api/accommodation")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(ACCOMMODATION_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Test Apartment"));
        }

        @Test
        @DisplayName("With missing auth headers returns 401")
        void create_WithMissingAuthHeaders_Returns401() throws Exception {
            mockMvc.perform(post("/api/accommodation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void create_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(post("/api/accommodation")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With missing name returns 400")
        void create_WithMissingName_Returns400() throws Exception {
            var request = Map.of(
                    "address", "123 Test St",
                    "minGuests", 1,
                    "maxGuests", 4,
                    "pricingMode", "PER_GUEST",
                    "approvalMode", "MANUAL"
            );

            mockMvc.perform(post("/api/accommodation")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("With existing ID returns 200")
        void getById_WithExistingId_Returns200() throws Exception {
            when(accommodationService.getById(ACCOMMODATION_ID)).thenReturn(createResponse());

            mockMvc.perform(get("/api/accommodation/{id}", ACCOMMODATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ACCOMMODATION_ID.toString()));
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void getById_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(accommodationService.getById(id))
                    .thenThrow(new AccommodationNotFoundException("Not found"));

            mockMvc.perform(get("/api/accommodation/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/host/{hostId}")
    class GetByHostIdEndpoint {

        @Test
        @DisplayName("Returns 200 with list")
        void getByHostId_Returns200WithList() throws Exception {
            when(accommodationService.getByHostId(HOST_ID))
                    .thenReturn(List.of(createResponse()));

            mockMvc.perform(get("/api/accommodation/host/{hostId}", HOST_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ACCOMMODATION_ID.toString()));
        }
    }

    @Nested
    @DisplayName("PUT /api/accommodation/{id}")
    class UpdateEndpoint {

        @Test
        @DisplayName("With valid request returns 200")
        void update_WithValidRequest_Returns200() throws Exception {
            when(accommodationService.update(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenReturn(createResponse());

            var request = Map.of("name", "Updated Name");

            mockMvc.perform(put("/api/accommodation/{id}", ACCOMMODATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void update_WithWrongOwner_Returns403() throws Exception {
            when(accommodationService.update(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenThrow(new ForbiddenException("Not the owner"));

            var request = Map.of("name", "Updated Name");

            mockMvc.perform(put("/api/accommodation/{id}", ACCOMMODATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/accommodation/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("With valid request returns 204")
        void delete_WithValidRequest_Returns204() throws Exception {
            doNothing().when(accommodationService).delete(eq(ACCOMMODATION_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/accommodation/{id}", ACCOMMODATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void delete_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new AccommodationNotFoundException("Not found"))
                    .when(accommodationService).delete(eq(id), any(UserContext.class));

            mockMvc.perform(delete("/api/accommodation/{id}", id)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }
    }
}
