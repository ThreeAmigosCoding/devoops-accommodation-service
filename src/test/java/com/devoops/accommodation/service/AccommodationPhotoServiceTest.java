package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.response.AccommodationPhotoResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AccommodationPhoto;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.mapper.AccommodationPhotoMapper;
import com.devoops.accommodation.repository.AccommodationPhotoRepository;
import com.devoops.accommodation.repository.AccommodationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccommodationPhotoServiceTest {

    @Mock
    private AccommodationPhotoRepository photoRepository;

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private AccommodationPhotoMapper photoMapper;

    @InjectMocks
    private AccommodationPhotoService photoService;

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID OTHER_HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID PHOTO_ID = UUID.randomUUID();
    private static final UserContext HOST_CONTEXT = new UserContext(HOST_ID, "HOST");
    private static final UserContext OTHER_HOST_CONTEXT = new UserContext(OTHER_HOST_ID, "HOST");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(photoService, "maxPhotosPerAccommodation", 20);
        ReflectionTestUtils.setField(photoService, "allowedContentTypesConfig", "image/jpeg,image/png,image/webp");
    }

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

    private AccommodationPhoto createPhoto() {
        return AccommodationPhoto.builder()
                .id(PHOTO_ID)
                .accommodationId(ACCOMMODATION_ID)
                .storageFilename("uuid-filename.jpg")
                .originalFilename("original.jpg")
                .contentType("image/jpeg")
                .fileSize(1024L)
                .displayOrder(0)
                .build();
    }

    private AccommodationPhotoResponse createPhotoResponse() {
        return new AccommodationPhotoResponse(
                PHOTO_ID, ACCOMMODATION_ID, "original.jpg", "image/jpeg",
                1024L, 0, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("uploadPhoto")
    class UploadPhotoTests {

        @Test
        @DisplayName("With valid request uploads photo and returns response")
        void uploadPhoto_ValidRequest_ReturnsResponse() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());
            var accommodation = createAccommodation();
            var photo = createPhoto();
            var response = createPhotoResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.countByAccommodationId(ACCOMMODATION_ID)).thenReturn(0L);
            when(photoStorageService.store(file)).thenReturn("uuid-filename.jpg");
            when(photoRepository.findMaxDisplayOrder(ACCOMMODATION_ID)).thenReturn(-1);
            when(photoRepository.saveAndFlush(any(AccommodationPhoto.class))).thenReturn(photo);
            when(photoMapper.toResponse(photo)).thenReturn(response);

            AccommodationPhotoResponse result = photoService.uploadPhoto(ACCOMMODATION_ID, file, null, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
            verify(photoStorageService).store(file);
            verify(photoRepository).saveAndFlush(any(AccommodationPhoto.class));
        }

        @Test
        @DisplayName("With custom display order uses provided order")
        void uploadPhoto_CustomDisplayOrder_UsesProvidedOrder() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());
            var accommodation = createAccommodation();
            var photo = createPhoto();
            var response = createPhotoResponse();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.countByAccommodationId(ACCOMMODATION_ID)).thenReturn(0L);
            when(photoStorageService.store(file)).thenReturn("uuid-filename.jpg");
            when(photoRepository.saveAndFlush(any(AccommodationPhoto.class))).thenReturn(photo);
            when(photoMapper.toResponse(photo)).thenReturn(response);

            photoService.uploadPhoto(ACCOMMODATION_ID, file, 5, HOST_CONTEXT);

            verify(photoRepository, never()).findMaxDisplayOrder(any());
        }

        @Test
        @DisplayName("With accommodation not found throws AccommodationNotFoundException")
        void uploadPhoto_AccommodationNotFound_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.uploadPhoto(ACCOMMODATION_ID, file, null, HOST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void uploadPhoto_WrongOwner_ThrowsForbiddenException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> photoService.uploadPhoto(ACCOMMODATION_ID, file, null, OTHER_HOST_CONTEXT))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not the owner");
        }

        @Test
        @DisplayName("With invalid content type throws InvalidContentTypeException")
        void uploadPhoto_InvalidContentType_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.gif", "image/gif", "test content".getBytes());
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> photoService.uploadPhoto(ACCOMMODATION_ID, file, null, HOST_CONTEXT))
                    .isInstanceOf(InvalidContentTypeException.class)
                    .hasMessageContaining("image/gif");
        }

        @Test
        @DisplayName("With photo limit reached throws PhotoLimitExceededException")
        void uploadPhoto_PhotoLimitReached_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.countByAccommodationId(ACCOMMODATION_ID)).thenReturn(20L);

            assertThatThrownBy(() -> photoService.uploadPhoto(ACCOMMODATION_ID, file, null, HOST_CONTEXT))
                    .isInstanceOf(PhotoLimitExceededException.class)
                    .hasMessageContaining("Maximum number of photos");
        }
    }

    @Nested
    @DisplayName("listPhotos")
    class ListPhotosTests {

        @Test
        @DisplayName("Returns list of photo responses")
        void listPhotos_ReturnsPhotoResponses() {
            var accommodation = createAccommodation();
            var photos = List.of(createPhoto());
            var responses = List.of(createPhotoResponse());

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.findByAccommodationIdOrderByDisplayOrderAsc(ACCOMMODATION_ID)).thenReturn(photos);
            when(photoMapper.toResponseList(photos)).thenReturn(responses);

            List<AccommodationPhotoResponse> result = photoService.listPhotos(ACCOMMODATION_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(responses.get(0));
        }

        @Test
        @DisplayName("With accommodation not found throws AccommodationNotFoundException")
        void listPhotos_AccommodationNotFound_ThrowsException() {
            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.listPhotos(ACCOMMODATION_ID))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPhotoFile")
    class GetPhotoFileTests {

        @Test
        @DisplayName("Returns input stream for photo file")
        void getPhotoFile_ReturnsInputStream() {
            var photo = createPhoto();
            InputStream mockStream = new ByteArrayInputStream("test content".getBytes());

            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.of(photo));
            when(photoStorageService.loadAsStream(photo.getStorageFilename())).thenReturn(mockStream);

            InputStream result = photoService.getPhotoFile(ACCOMMODATION_ID, PHOTO_ID);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("With photo not found throws PhotoNotFoundException")
        void getPhotoFile_PhotoNotFound_ThrowsException() {
            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.getPhotoFile(ACCOMMODATION_ID, PHOTO_ID))
                    .isInstanceOf(PhotoNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPhotoMetadata")
    class GetPhotoMetadataTests {

        @Test
        @DisplayName("Returns photo entity")
        void getPhotoMetadata_ReturnsPhotoEntity() {
            var photo = createPhoto();

            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.of(photo));

            AccommodationPhoto result = photoService.getPhotoMetadata(ACCOMMODATION_ID, PHOTO_ID);

            assertThat(result).isEqualTo(photo);
        }

        @Test
        @DisplayName("With photo not found throws PhotoNotFoundException")
        void getPhotoMetadata_PhotoNotFound_ThrowsException() {
            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.getPhotoMetadata(ACCOMMODATION_ID, PHOTO_ID))
                    .isInstanceOf(PhotoNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deletePhoto")
    class DeletePhotoTests {

        @Test
        @DisplayName("With valid owner soft-deletes photo and removes from storage")
        void deletePhoto_ValidOwner_SoftDeletesPhoto() {
            var accommodation = createAccommodation();
            var photo = createPhoto();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.of(photo));

            photoService.deletePhoto(ACCOMMODATION_ID, PHOTO_ID, HOST_CONTEXT);

            assertThat(photo.isDeleted()).isTrue();
            verify(photoStorageService).delete(photo.getStorageFilename());
            verify(photoRepository).save(photo);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void deletePhoto_WrongOwner_ThrowsForbiddenException() {
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));

            assertThatThrownBy(() -> photoService.deletePhoto(ACCOMMODATION_ID, PHOTO_ID, OTHER_HOST_CONTEXT))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("With accommodation not found throws AccommodationNotFoundException")
        void deletePhoto_AccommodationNotFound_ThrowsException() {
            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.deletePhoto(ACCOMMODATION_ID, PHOTO_ID, HOST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class);
        }

        @Test
        @DisplayName("With photo not found throws PhotoNotFoundException")
        void deletePhoto_PhotoNotFound_ThrowsException() {
            var accommodation = createAccommodation();

            when(accommodationRepository.findById(ACCOMMODATION_ID)).thenReturn(Optional.of(accommodation));
            when(photoRepository.findByIdAndAccommodationId(PHOTO_ID, ACCOMMODATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> photoService.deletePhoto(ACCOMMODATION_ID, PHOTO_ID, HOST_CONTEXT))
                    .isInstanceOf(PhotoNotFoundException.class);
        }
    }
}
