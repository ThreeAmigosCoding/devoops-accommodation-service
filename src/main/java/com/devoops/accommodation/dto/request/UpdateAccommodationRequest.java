package com.devoops.accommodation.dto.request;

import com.devoops.accommodation.entity.AmenityType;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import jakarta.validation.constraints.Min;

import java.util.Set;

public record UpdateAccommodationRequest(
        String name,

        String address,

        @Min(value = 1, message = "Minimum guests must be at least 1")
        Integer minGuests,

        @Min(value = 1, message = "Maximum guests must be at least 1")
        Integer maxGuests,

        PricingMode pricingMode,

        ApprovalMode approvalMode,

        Set<AmenityType> amenities
) {
}
