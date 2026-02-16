package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RequireRole;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.response.AccommodationPhotoResponse;
import com.devoops.accommodation.entity.AccommodationPhoto;
import com.devoops.accommodation.service.AccommodationPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accommodation/{accommodationId}/photos")
@RequiredArgsConstructor
public class AccommodationPhotoController {

    private final AccommodationPhotoService photoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("HOST")
    public ResponseEntity<AccommodationPhotoResponse> uploadPhoto(
            @PathVariable UUID accommodationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            UserContext userContext) {
        AccommodationPhotoResponse response = photoService.uploadPhoto(accommodationId, file, displayOrder, userContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AccommodationPhotoResponse>> listPhotos(@PathVariable UUID accommodationId) {
        List<AccommodationPhotoResponse> photos = photoService.listPhotos(accommodationId);
        return ResponseEntity.ok(photos);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<StreamingResponseBody> getPhoto(
            @PathVariable UUID accommodationId,
            @PathVariable UUID photoId) {
        AccommodationPhoto metadata = photoService.getPhotoMetadata(accommodationId, photoId);
        InputStream inputStream = photoService.getPhotoFile(accommodationId, photoId);

        StreamingResponseBody responseBody = outputStream -> {
            try (inputStream) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .contentLength(metadata.getFileSize())
                .body(responseBody);
    }

    @DeleteMapping("/{photoId}")
    @RequireRole("HOST")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable UUID accommodationId,
            @PathVariable UUID photoId,
            UserContext userContext) {
        photoService.deletePhoto(accommodationId, photoId, userContext);
        return ResponseEntity.noContent().build();
    }
}
