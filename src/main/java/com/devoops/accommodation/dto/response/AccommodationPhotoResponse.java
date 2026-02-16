package com.devoops.accommodation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccommodationPhotoResponse(
        UUID id,
        UUID accommodationId,
        String originalFilename,
        String contentType,
        Long fileSize,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
