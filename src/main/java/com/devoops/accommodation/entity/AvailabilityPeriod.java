package com.devoops.accommodation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "availability_periods")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AvailabilityPeriod extends BaseEntity {

    @Column(name = "accommodation_id", nullable = false)
    private UUID accommodationId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "price_per_day", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerDay;
}
