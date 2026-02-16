package com.devoops.accommodation.service;

import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.response.AccommodationPhotoResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AccommodationPhoto;
import com.devoops.accommodation.exception.*;
import com.devoops.accommodation.mapper.AccommodationPhotoMapper;
import com.devoops.accommodation.repository.AccommodationPhotoRepository;
import com.devoops.accommodation.repository.AccommodationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationPhotoService {

    private final AccommodationPhotoRepository photoRepository;
    private final AccommodationRepository accommodationRepository;
    private final PhotoStorageService photoStorageService;
    private final AccommodationPhotoMapper photoMapper;

    @Value("${app.photo.max-photos-per-accommodation}")
    private int maxPhotosPerAccommodation;

    @Value("${app.photo.allowed-content-types}")
    private String allowedContentTypesConfig;

    @Transactional
    public AccommodationPhotoResponse uploadPhoto(UUID accommodationId, MultipartFile file, Integer displayOrder, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(accommodationId);
        validateOwnership(accommodation, userContext);
        validateContentType(file.getContentType());
        validatePhotoLimit(accommodationId);

        String storageFilename = photoStorageService.store(file);

        int order = displayOrder != null ? displayOrder : photoRepository.findMaxDisplayOrder(accommodationId) + 1;

        AccommodationPhoto photo = AccommodationPhoto.builder()
                .accommodationId(accommodationId)
                .storageFilename(storageFilename)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .displayOrder(order)
                .build();

        photo = photoRepository.saveAndFlush(photo);
        log.info("Uploaded photo {} for accommodation {}", photo.getId(), accommodationId);

        return photoMapper.toResponse(photo);
    }

    @Transactional(readOnly = true)
    public List<AccommodationPhotoResponse> listPhotos(UUID accommodationId) {
        findAccommodationOrThrow(accommodationId);
        List<AccommodationPhoto> photos = photoRepository.findByAccommodationIdOrderByDisplayOrderAsc(accommodationId);
        return photoMapper.toResponseList(photos);
    }

    @Transactional(readOnly = true)
    public InputStream getPhotoFile(UUID accommodationId, UUID photoId) {
        AccommodationPhoto photo = findPhotoOrThrow(accommodationId, photoId);
        return photoStorageService.loadAsStream(photo.getStorageFilename());
    }

    @Transactional(readOnly = true)
    public AccommodationPhoto getPhotoMetadata(UUID accommodationId, UUID photoId) {
        return findPhotoOrThrow(accommodationId, photoId);
    }

    @Transactional
    public void deletePhoto(UUID accommodationId, UUID photoId, UserContext userContext) {
        Accommodation accommodation = findAccommodationOrThrow(accommodationId);
        validateOwnership(accommodation, userContext);

        AccommodationPhoto photo = findPhotoOrThrow(accommodationId, photoId);

        photoStorageService.delete(photo.getStorageFilename());

        photo.setDeleted(true);
        photoRepository.save(photo);

        log.info("Deleted photo {} from accommodation {}", photoId, accommodationId);
    }

    private Accommodation findAccommodationOrThrow(UUID accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new AccommodationNotFoundException("Accommodation not found with id: " + accommodationId));
    }

    private AccommodationPhoto findPhotoOrThrow(UUID accommodationId, UUID photoId) {
        return photoRepository.findByIdAndAccommodationId(photoId, accommodationId)
                .orElseThrow(() -> new PhotoNotFoundException("Photo not found with id: " + photoId));
    }

    private void validateOwnership(Accommodation accommodation, UserContext userContext) {
        if (!accommodation.getHostId().equals(userContext.userId())) {
            throw new ForbiddenException("You are not the owner of this accommodation");
        }
    }

    private void validateContentType(String contentType) {
        Set<String> allowedTypes = Arrays.stream(allowedContentTypesConfig.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new InvalidContentTypeException("Invalid content type: " + contentType + ". Allowed types: " + allowedContentTypesConfig);
        }
    }

    private void validatePhotoLimit(UUID accommodationId) {
        long currentCount = photoRepository.countByAccommodationId(accommodationId);
        if (currentCount >= maxPhotosPerAccommodation) {
            throw new PhotoLimitExceededException("Maximum number of photos (" + maxPhotosPerAccommodation + ") reached for this accommodation");
        }
    }
}
