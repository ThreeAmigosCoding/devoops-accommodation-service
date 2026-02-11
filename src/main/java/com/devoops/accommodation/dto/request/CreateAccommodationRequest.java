package com.devoops.accommodation.dto.request;

import com.devoops.accommodation.entity.AmenityType;
import com.devoops.accommodation.entity.ApprovalMode;
import com.devoops.accommodation.entity.PricingMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record CreateAccommodationRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Address is required")
        String address,

        @NotNull(message = "Minimum guests is required")
        @Min(value = 1, message = "Minimum guests must be at least 1")
        Integer minGuests,

        @NotNull(message = "Maximum guests is required")
        @Min(value = 1, message = "Maximum guests must be at least 1")
        Integer maxGuests,

        @NotNull(message = "Pricing mode is required")
        PricingMode pricingMode,

        @NotNull(message = "Approval mode is required")
        ApprovalMode approvalMode,

        Set<AmenityType> amenities
) {
}
