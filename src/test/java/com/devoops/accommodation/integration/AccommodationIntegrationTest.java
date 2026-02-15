package com.devoops.accommodation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccommodationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accommodation_db_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String accommodationId;
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID OTHER_HOST_ID = UUID.randomUUID();

    private static final String BASE_PATH = "/api/accommodation";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    private Map<String, Object> validCreateRequest() {
        return Map.of(
                "name", "Integration Test Apartment",
                "address", "456 Integration St",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );
    }

    @Test
    @Order(1)
    @DisplayName("Create accommodation with valid request returns 201")
    void create_WithValidRequest_Returns201WithResponse() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Test Apartment"))
                .andExpect(jsonPath("$.address").value("456 Integration St"))
                .andExpect(jsonPath("$.hostId").value(HOST_ID.toString()))
                .andExpect(jsonPath("$.minGuests").value(1))
                .andExpect(jsonPath("$.maxGuests").value(4))
                .andExpect(jsonPath("$.pricingMode").value("PER_GUEST"))
                .andExpect(jsonPath("$.approvalMode").value("MANUAL"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        accommodationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Create accommodation with amenities stores them correctly")
    void create_WithAmenities_StoresAmenitiesCorrectly() throws Exception {
        var request = Map.of(
                "name", "Amenity Apartment",
                "address", "789 Amenity St",
                "minGuests", 1,
                "maxGuests", 2,
                "pricingMode", "PER_UNIT",
                "approvalMode", "AUTOMATIC",
                "amenities", List.of("WIFI", "PARKING", "AC")
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amenities", hasSize(3)))
                .andExpect(jsonPath("$.amenities", containsInAnyOrder("WIFI", "PARKING", "AC")));
    }

    @Test
    @Order(3)
    @DisplayName("Create accommodation with missing name returns 400")
    void create_WithMissingName_Returns400() throws Exception {
        var request = Map.of(
                "address", "123 St",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("Create accommodation with min exceeding max returns 400")
    void create_WithMinExceedingMax_Returns400() throws Exception {
        var request = Map.of(
                "name", "Bad Capacity",
                "address", "123 St",
                "minGuests", 5,
                "maxGuests", 2,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("Create accommodation without auth headers returns 401")
    void create_WithoutAuthHeaders_Returns401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("Create accommodation with GUEST role returns 403")
    void create_WithGuestRole_Returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Get by ID with existing ID returns 200")
    void getById_WithExistingId_Returns200() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + accommodationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accommodationId))
                .andExpect(jsonPath("$.name").value("Integration Test Apartment"));
    }

    @Test
    @Order(8)
    @DisplayName("Get by ID with non-existing ID returns 404")
    void getById_WithNonExistingId_Returns404() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("Get by host ID returns list of accommodations")
    void getByHostId_ReturnsListOfAccommodations() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/host/" + HOST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(10)
    @DisplayName("Update accommodation with valid request returns 200")
    void update_WithValidRequest_Returns200() throws Exception {
        var request = Map.of("name", "Updated Apartment Name");

        mockMvc.perform(put(BASE_PATH + "/" + accommodationId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Apartment Name"))
                .andExpect(jsonPath("$.address").value("456 Integration St"));
    }

    @Test
    @Order(11)
    @DisplayName("Update accommodation with different host returns 403")
    void update_WithDifferentHost_Returns403() throws Exception {
        var request = Map.of("name", "Hacked Name");

        mockMvc.perform(put(BASE_PATH + "/" + accommodationId)
                        .header("X-User-Id", OTHER_HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    @DisplayName("Update accommodation with partial fields only updates provided")
    void update_WithPartialFields_OnlyUpdatesProvided() throws Exception {
        var request = Map.of("maxGuests", 8);

        mockMvc.perform(put(BASE_PATH + "/" + accommodationId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxGuests").value(8))
                .andExpect(jsonPath("$.name").value("Updated Apartment Name"));
    }

    @Test
    @Order(13)
    @DisplayName("Delete accommodation with valid owner returns 204")
    void delete_WithValidOwner_Returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/" + accommodationId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(14)
    @DisplayName("After delete, get by ID returns 404 (soft-delete filters)")
    void delete_ThenGetById_Returns404() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + accommodationId))
                .andExpect(status().isNotFound());
    }
}
