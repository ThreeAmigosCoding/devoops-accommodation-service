package com.devoops.accommodation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAvailabilityPeriodRequest(
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @NotNull(message = "Price per day is required") @Positive(message = "Price per day must be positive") BigDecimal pricePerDay
) {}
