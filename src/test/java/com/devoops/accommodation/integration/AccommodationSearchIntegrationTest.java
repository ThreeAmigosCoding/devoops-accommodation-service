package com.devoops.accommodation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccommodationSearchIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accommodation_db_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-01-31T20-20-33Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String accommodationId;
    private static String accommodationIdPerUnit;
    private static final UUID HOST_ID = UUID.randomUUID();

    private static final String BASE_PATH = "/api/accommodation";
    private static final String SEARCH_PATH = BASE_PATH + "/search";

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
    @DisplayName("Setup: Create PER_GUEST accommodation in Belgrade")
    void setup_CreatePerGuestAccommodation() throws Exception {
        var request = Map.of(
                "name", "Belgrade Apartment",
                "address", "123 Belgrade Center, Serbia",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );

        MvcResult result = mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        accommodationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Setup: Create availability period for PER_GUEST accommodation")
    void setup_CreateAvailabilityPeriod() throws Exception {
        var request = Map.of(
                "startDate", "2026-03-01",
                "endDate", "2026-03-31",
                "pricePerDay", 50.00
        );

        mockMvc.perform(post(BASE_PATH + "/" + accommodationId + "/availability")
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    @DisplayName("Setup: Create PER_UNIT accommodation in Belgrade")
    void setup_CreatePerUnitAccommodation() throws Exception {
        var request = Map.of(
                "name", "Belgrade Studio",
                "address", "456 Belgrade New, Serbia",
                "minGuests", 1,
                "maxGuests", 2,
                "pricingMode", "PER_UNIT",
                "approvalMode", "AUTOMATIC"
        );

        MvcResult result = mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        accommodationIdPerUnit = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(4)
    @DisplayName("Setup: Create availability period for PER_UNIT accommodation")
    void setup_CreatePerUnitAvailabilityPeriod() throws Exception {
        var request = Map.of(
                "startDate", "2026-03-01",
                "endDate", "2026-03-31",
                "pricePerDay", 100.00
        );

        mockMvc.perform(post(BASE_PATH + "/" + accommodationIdPerUnit + "/availability")
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(5)
    @DisplayName("Search with matching criteria returns results with prices")
    void search_WithMatchingCriteria_ReturnsResultsWithPrices() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].totalPrice").exists())
                .andExpect(jsonPath("$[*].unitPrice").exists())
                .andExpect(jsonPath("$[*].numberOfNights").exists());
    }

    @Test
    @Order(6)
    @DisplayName("Search PER_GUEST accommodation calculates price with guest multiplier")
    void search_PerGuestAccommodation_CalculatesWithGuestMultiplier() throws Exception {
        // 50/day * 5 nights * 2 guests = 500
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade Center")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Belgrade Apartment"))
                .andExpect(jsonPath("$[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$[0].totalPrice").value(500.00))
                .andExpect(jsonPath("$[0].numberOfNights").value(5));
    }

    @Test
    @Order(7)
    @DisplayName("Search PER_UNIT accommodation calculates price without guest multiplier")
    void search_PerUnitAccommodation_CalculatesWithoutGuestMultiplier() throws Exception {
        // 100/day * 5 nights = 500 (no guest multiplier)
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade New")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Belgrade Studio"))
                .andExpect(jsonPath("$[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$[0].totalPrice").value(500.00))
                .andExpect(jsonPath("$[0].numberOfNights").value(5));
    }

    @Test
    @Order(8)
    @DisplayName("Search with non-matching location returns empty list")
    void search_WithNonMatchingLocation_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Paris")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(9)
    @DisplayName("Search with too many guests returns empty list")
    void search_WithTooManyGuests_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade")
                        .param("guests", "10")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(10)
    @DisplayName("Search with dates outside availability returns empty list")
    void search_WithDatesOutsideAvailability_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade")
                        .param("guests", "2")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(11)
    @DisplayName("Search without auth headers returns 200 (public endpoint)")
    void search_WithoutAuth_Returns200() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(12)
    @DisplayName("Search with missing parameters returns 400")
    void search_WithMissingParams_Returns400() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("Search with end date before start date returns 400")
    void search_WithInvalidDates_Returns400() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade")
                        .param("guests", "2")
                        .param("startDate", "2026-03-10")
                        .param("endDate", "2026-03-05"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    @DisplayName("Search returns all accommodation fields")
    void search_ReturnsAllAccommodationFields() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("location", "Belgrade Center")
                        .param("guests", "2")
                        .param("startDate", "2026-03-05")
                        .param("endDate", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(accommodationId))
                .andExpect(jsonPath("$[0].hostId").value(HOST_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Belgrade Apartment"))
                .andExpect(jsonPath("$[0].address").value("123 Belgrade Center, Serbia"))
                .andExpect(jsonPath("$[0].minGuests").value(1))
                .andExpect(jsonPath("$[0].maxGuests").value(4))
                .andExpect(jsonPath("$[0].pricingMode").value("PER_GUEST"))
                .andExpect(jsonPath("$[0].approvalMode").value("MANUAL"));
    }
}
