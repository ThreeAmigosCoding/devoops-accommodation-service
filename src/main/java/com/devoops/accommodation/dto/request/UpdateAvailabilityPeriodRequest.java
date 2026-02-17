package com.devoops.accommodation.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateAvailabilityPeriodRequest(
        LocalDate startDate,
        LocalDate endDate,
        @Positive(message = "Price per day must be positive") BigDecimal pricePerDay
) {}
