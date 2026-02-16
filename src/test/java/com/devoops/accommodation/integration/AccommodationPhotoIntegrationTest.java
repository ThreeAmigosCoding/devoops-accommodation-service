package com.devoops.accommodation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccommodationPhotoIntegrationTest {

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
    private static String photoId;
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID OTHER_HOST_ID = UUID.randomUUID();

    private static final String ACCOMMODATION_BASE_PATH = "/api/accommodation";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        // MinIO
        registry.add("minio.endpoint", minio::getS3URL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
        registry.add("minio.bucket", () -> "test-accommodation-photos");
    }

    private String photosPath() {
        return ACCOMMODATION_BASE_PATH + "/" + accommodationId + "/photos";
    }

    @Test
    @Order(1)
    @DisplayName("Create accommodation for photo tests")
    void setup_CreateAccommodation() throws Exception {
        var request = Map.of(
                "name", "Photo Test Apartment",
                "address", "123 Photo St",
                "minGuests", 1,
                "maxGuests", 4,
                "pricingMode", "PER_GUEST",
                "approvalMode", "MANUAL"
        );

        MvcResult result = mockMvc.perform(post(ACCOMMODATION_BASE_PATH)
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
    @DisplayName("Upload photo with valid request returns 201")
    void uploadPhoto_WithValidRequest_Returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-image.jpg", "image/jpeg",
                "fake image content".getBytes());

        MvcResult result = mockMvc.perform(multipart(photosPath())
                        .file(file)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.accommodationId").value(accommodationId))
                .andExpect(jsonPath("$.originalFilename").value("test-image.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andReturn();

        photoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(3)
    @DisplayName("Upload photo with custom display order uses provided order")
    void uploadPhoto_WithDisplayOrder_UsesProvidedOrder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "second-image.png", "image/png",
                "fake image content 2".getBytes());

        mockMvc.perform(multipart(photosPath())
                        .file(file)
                        .param("displayOrder", "5")
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder").value(5));
    }

    @Test
    @Order(4)
    @DisplayName("Upload photo without auth headers returns 401")
    void uploadPhoto_WithoutAuthHeaders_Returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart(photosPath())
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("Upload photo with GUEST role returns 403")
    void uploadPhoto_WithGuestRole_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart(photosPath())
                        .file(file)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("Upload photo with different host returns 403")
    void uploadPhoto_WithDifferentHost_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart(photosPath())
                        .file(file)
                        .header("X-User-Id", OTHER_HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Upload photo with invalid content type returns 400")
    void uploadPhoto_WithInvalidContentType_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.gif", "image/gif", "content".getBytes());

        mockMvc.perform(multipart(photosPath())
                        .file(file)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("List photos returns 200 with photo list")
    void listPhotos_Returns200WithPhotoList() throws Exception {
        mockMvc.perform(get(photosPath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].originalFilename").value("test-image.jpg"));
    }

    @Test
    @Order(9)
    @DisplayName("List photos for non-existing accommodation returns 404")
    void listPhotos_NonExistingAccommodation_Returns404() throws Exception {
        mockMvc.perform(get(ACCOMMODATION_BASE_PATH + "/" + UUID.randomUUID() + "/photos"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("Get photo file returns 200 with image content")
    void getPhotoFile_Returns200WithImageContent() throws Exception {
        mockMvc.perform(get(photosPath() + "/" + photoId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().exists("Content-Length"));
    }

    @Test
    @Order(11)
    @DisplayName("Get photo file for non-existing photo returns 404")
    void getPhotoFile_NonExistingPhoto_Returns404() throws Exception {
        mockMvc.perform(get(photosPath() + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    @DisplayName("Delete photo without auth headers returns 401")
    void deletePhoto_WithoutAuthHeaders_Returns401() throws Exception {
        mockMvc.perform(delete(photosPath() + "/" + photoId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    @DisplayName("Delete photo with GUEST role returns 403")
    void deletePhoto_WithGuestRole_Returns403() throws Exception {
        mockMvc.perform(delete(photosPath() + "/" + photoId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("Delete photo with different host returns 403")
    void deletePhoto_WithDifferentHost_Returns403() throws Exception {
        mockMvc.perform(delete(photosPath() + "/" + photoId)
                        .header("X-User-Id", OTHER_HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(15)
    @DisplayName("Delete photo with valid owner returns 204")
    void deletePhoto_WithValidOwner_Returns204() throws Exception {
        mockMvc.perform(delete(photosPath() + "/" + photoId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(16)
    @DisplayName("After delete, get photo returns 404 (soft-delete filters)")
    void deletePhoto_ThenGet_Returns404() throws Exception {
        mockMvc.perform(get(photosPath() + "/" + photoId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(17)
    @DisplayName("After delete, list photos excludes deleted photo")
    void deletePhoto_ThenList_ExcludesDeletedPhoto() throws Exception {
        mockMvc.perform(get(photosPath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + photoId + "')]").doesNotExist());
    }
}
