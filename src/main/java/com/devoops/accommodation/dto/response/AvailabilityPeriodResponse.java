package com.devoops.accommodation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AvailabilityPeriodResponse(
        UUID id,
        UUID accommodationId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal pricePerDay,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
