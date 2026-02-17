package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RoleAuthorizationInterceptor;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.config.UserContextResolver;
import com.devoops.accommodation.dto.response.AccommodationPhotoResponse;
import com.devoops.accommodation.entity.AccommodationPhoto;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.service.AccommodationPhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccommodationPhotoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccommodationPhotoService photoService;

    @InjectMocks
    private AccommodationPhotoController photoController;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID PHOTO_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(photoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextResolver())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .build();
    }

    private AccommodationPhotoResponse createPhotoResponse() {
        return new AccommodationPhotoResponse(
                PHOTO_ID, ACCOMMODATION_ID, "test.jpg", "image/jpeg",
                1024L, 0, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private AccommodationPhoto createPhotoEntity() {
        return AccommodationPhoto.builder()
                .id(PHOTO_ID)
                .accommodationId(ACCOMMODATION_ID)
                .storageFilename("uuid.jpg")
                .originalFilename("test.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .displayOrder(0)
                .build();
    }

    @Nested
    @DisplayName("POST /api/accommodation/{accommodationId}/photos")
    class UploadPhotoEndpoint {

        @Test
        @DisplayName("With valid request returns 201")
        void upload_WithValidRequest_Returns201() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.jpg", "image/jpeg", "test content".getBytes());

            when(photoService.uploadPhotos(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenReturn(List.of(createPhotoResponse()));

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].id").value(PHOTO_ID.toString()))
                    .andExpect(jsonPath("$[0].originalFilename").value("test.jpg"));
        }

        @Test
        @DisplayName("With missing auth headers returns 401")
        void upload_WithMissingAuthHeaders_Returns401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.jpg", "image/jpeg", "test content".getBytes());

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void upload_WithGuestRole_Returns403() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.jpg", "image/jpeg", "test content".getBytes());

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With accommodation not found returns 404")
        void upload_WithAccommodationNotFound_Returns404() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.jpg", "image/jpeg", "test content".getBytes());

            when(photoService.uploadPhotos(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenThrow(new AccommodationNotFoundException("Not found"));

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With invalid content type returns 400")
        void upload_WithInvalidContentType_Returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.gif", "image/gif", "test content".getBytes());

            when(photoService.uploadPhotos(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenThrow(new InvalidContentTypeException("Invalid content type"));

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With photo limit exceeded returns 400")
        void upload_WithPhotoLimitExceeded_Returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "test.jpg", "image/jpeg", "test content".getBytes());

            when(photoService.uploadPhotos(eq(ACCOMMODATION_ID), any(), any(UserContext.class)))
                    .thenThrow(new PhotoLimitExceededException("Limit exceeded"));

            mockMvc.perform(multipart("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID)
                            .file(file)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/{accommodationId}/photos")
    class ListPhotosEndpoint {

        @Test
        @DisplayName("Returns 200 with list of photos")
        void list_Returns200WithList() throws Exception {
            when(photoService.listPhotos(ACCOMMODATION_ID))
                    .thenReturn(List.of(createPhotoResponse()));

            mockMvc.perform(get("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(PHOTO_ID.toString()))
                    .andExpect(jsonPath("$[0].originalFilename").value("test.jpg"));
        }

        @Test
        @DisplayName("With accommodation not found returns 404")
        void list_WithAccommodationNotFound_Returns404() throws Exception {
            when(photoService.listPhotos(ACCOMMODATION_ID))
                    .thenThrow(new AccommodationNotFoundException("Not found"));

            mockMvc.perform(get("/api/accommodation/{accommodationId}/photos", ACCOMMODATION_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/accommodation/{accommodationId}/photos/{photoId}")
    class GetPhotoEndpoint {

        @Test
        @DisplayName("Returns 200 with image file")
        void get_Returns200WithImageFile() throws Exception {
            AccommodationPhoto photo = createPhotoEntity();
            byte[] content = "test image content".getBytes();

            when(photoService.getPhotoMetadata(ACCOMMODATION_ID, PHOTO_ID)).thenReturn(photo);
            when(photoService.getPhotoFile(ACCOMMODATION_ID, PHOTO_ID))
                    .thenReturn(new ByteArrayInputStream(content));

            mockMvc.perform(get("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andExpect(header().longValue("Content-Length", photo.getFileSize()));
        }

        @Test
        @DisplayName("With photo not found returns 404")
        void get_WithPhotoNotFound_Returns404() throws Exception {
            when(photoService.getPhotoMetadata(ACCOMMODATION_ID, PHOTO_ID))
                    .thenThrow(new PhotoNotFoundException("Not found"));

            mockMvc.perform(get("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/accommodation/{accommodationId}/photos/{photoId}")
    class DeletePhotoEndpoint {

        @Test
        @DisplayName("With valid request returns 204")
        void delete_WithValidRequest_Returns204() throws Exception {
            doNothing().when(photoService).deletePhoto(eq(ACCOMMODATION_ID), eq(PHOTO_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("With missing auth headers returns 401")
        void delete_WithMissingAuthHeaders_Returns401() throws Exception {
            mockMvc.perform(delete("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void delete_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(delete("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With photo not found returns 404")
        void delete_WithPhotoNotFound_Returns404() throws Exception {
            doThrow(new PhotoNotFoundException("Not found"))
                    .when(photoService).deletePhoto(eq(ACCOMMODATION_ID), eq(PHOTO_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void delete_WithWrongOwner_Returns403() throws Exception {
            doThrow(new ForbiddenException("Not the owner"))
                    .when(photoService).deletePhoto(eq(ACCOMMODATION_ID), eq(PHOTO_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/accommodation/{accommodationId}/photos/{photoId}", ACCOMMODATION_ID, PHOTO_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }
    }
}
