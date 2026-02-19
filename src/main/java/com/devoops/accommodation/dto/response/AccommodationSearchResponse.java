package com.devoops.accommodation.dto.response;

import com.devoops.accommodation.entity.AmenityType;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AccommodationSearchResponse(
        UUID id,
        UUID hostId,
        String name,
        String address,
        int minGuests,
        int maxGuests,
        PricingMode pricingMode,
        ApprovalMode approvalMode,
        List<AmenityType> amenities,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal totalPrice,
        BigDecimal unitPrice,
        int numberOfNights
) {
}
