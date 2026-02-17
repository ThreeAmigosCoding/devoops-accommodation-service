package com.devoops.accommodation.integration;

import com.devoops.accommodation.grpc.ReservationGrpcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AvailabilityPeriodIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accommodation_db_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-01-31T20-20-33Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationGrpcClient reservationGrpcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String accommodationId;
    private static String periodId;
    private static String secondPeriodId;
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID OTHER_HOST_ID = UUID.randomUUID();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("minio.endpoint", minio::getS3URL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
        registry.add("minio.bucket", () -> "test-accommodation-photos");
    }

    @Test
    @Order(1)
    @DisplayName("Setup: Create accommodation for availability tests")
    void setup_CreateAccommodation() throws Exception {
        var request = Map.of(
                "name", "Availability Test Apartment",
                "address", "123 Availability St",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );

        MvcResult result = mockMvc.perform(post("/api/accommodation")
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        accommodationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private String basePath() {
        return "/api/accommodation/" + accommodationId + "/availability";
    }

    @Test
    @Order(2)
    @DisplayName("Create availability period returns 201")
    void create_WithValidRequest_Returns201() throws Exception {
        var request = Map.of(
                "startDate", "2026-06-01",
                "endDate", "2026-06-30",
                "pricePerDay", 100.00
        );

        MvcResult result = mockMvc.perform(post(basePath())
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accommodationId").value(accommodationId))
                .andExpect(jsonPath("$.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.endDate").value("2026-06-30"))
                .andExpect(jsonPath("$.pricePerDay").value(100.00))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        periodId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(3)
    @DisplayName("Create non-overlapping period returns 201")
    void create_NonOverlappingPeriod_Returns201() throws Exception {
        var request = Map.of(
                "startDate", "2026-07-01",
                "endDate", "2026-07-31",
                "pricePerDay", 120.00
        );

        MvcResult result = mockMvc.perform(post(basePath())
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        secondPeriodId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(4)
    @DisplayName("Create overlapping period returns 409")
    void create_OverlappingPeriod_Returns409() throws Exception {
        var request = Map.of(
                "startDate", "2026-06-15",
                "endDate", "2026-07-15",
                "pricePerDay", 110.00
        );

        mockMvc.perform(post(basePath())
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    @DisplayName("Get all periods returns sorted list")
    void getAll_ReturnsSortedList() throws Exception {
        mockMvc.perform(get(basePath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startDate").value("2026-06-01"))
                .andExpect(jsonPath("$[1].startDate").value("2026-07-01"));
    }

    @Test
    @Order(6)
    @DisplayName("Get period by ID returns 200")
    void getById_Returns200() throws Exception {
        mockMvc.perform(get(basePath() + "/" + periodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(periodId))
                .andExpect(jsonPath("$.pricePerDay").value(100.00));
    }

    @Test
    @Order(7)
    @DisplayName("Get non-existing period returns 404")
    void getById_NonExisting_Returns404() throws Exception {
        mockMvc.perform(get(basePath() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("Update period with no reservations returns 200")
    void update_WithNoReservations_Returns200() throws Exception {
        when(reservationGrpcClient.hasApprovedReservations(any(), any(), any())).thenReturn(false);

        var request = Map.of("pricePerDay", 150.00);

        mockMvc.perform(put(basePath() + "/" + periodId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePerDay").value(150.00))
                .andExpect(jsonPath("$.startDate").value("2026-06-01"));
    }

    @Test
    @Order(9)
    @DisplayName("Update period by non-owner returns 403")
    void update_ByNonOwner_Returns403() throws Exception {
        var request = Map.of("pricePerDay", 200.00);

        mockMvc.perform(put(basePath() + "/" + periodId)
                        .header("X-User-Id", OTHER_HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("Update period with approved reservations returns 409")
    void update_WithReservations_Returns409() throws Exception {
        when(reservationGrpcClient.hasApprovedReservations(any(), any(), any())).thenReturn(true);

        var request = Map.of("pricePerDay", 200.00);

        mockMvc.perform(put(basePath() + "/" + periodId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(11)
    @DisplayName("Delete period with no reservations returns 204")
    void delete_WithNoReservations_Returns204() throws Exception {
        when(reservationGrpcClient.hasApprovedReservations(any(), any(), any())).thenReturn(false);

        mockMvc.perform(delete(basePath() + "/" + secondPeriodId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(12)
    @DisplayName("After delete, get by ID returns 404 (soft-delete)")
    void afterDelete_GetById_Returns404() throws Exception {
        mockMvc.perform(get(basePath() + "/" + secondPeriodId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(13)
    @DisplayName("After delete, get all returns only non-deleted periods")
    void afterDelete_GetAll_ReturnsOnlyNonDeleted() throws Exception {
        mockMvc.perform(get(basePath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(periodId));
    }

    @Test
    @Order(14)
    @DisplayName("Delete period with approved reservations returns 409")
    void delete_WithReservations_Returns409() throws Exception {
        when(reservationGrpcClient.hasApprovedReservations(any(), any(), any())).thenReturn(true);

        mockMvc.perform(delete(basePath() + "/" + periodId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isConflict());
    }
}
