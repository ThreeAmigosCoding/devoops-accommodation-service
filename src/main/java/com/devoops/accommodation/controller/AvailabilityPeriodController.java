package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RequireRole;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.request.UpdateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.response.AvailabilityPeriodResponse;
import com.devoops.accommodation.service.AvailabilityPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accommodation/{accommodationId}/availability")
@RequiredArgsConstructor
public class AvailabilityPeriodController {

    private final AvailabilityPeriodService availabilityPeriodService;

    @PostMapping
    @RequireRole("HOST")
    public ResponseEntity<AvailabilityPeriodResponse> create(
            @PathVariable UUID accommodationId,
            @Valid @RequestBody CreateAvailabilityPeriodRequest request,
            UserContext userContext) {
        AvailabilityPeriodResponse response = availabilityPeriodService.create(accommodationId, request, userContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityPeriodResponse>> getByAccommodationId(
            @PathVariable UUID accommodationId) {
        return ResponseEntity.ok(availabilityPeriodService.getByAccommodationId(accommodationId));
    }

    @GetMapping("/{periodId}")
    public ResponseEntity<AvailabilityPeriodResponse> getById(
            @PathVariable UUID accommodationId,
            @PathVariable UUID periodId) {
        return ResponseEntity.ok(availabilityPeriodService.getById(accommodationId, periodId));
    }

    @PutMapping("/{periodId}")
    @RequireRole("HOST")
    public ResponseEntity<AvailabilityPeriodResponse> update(
            @PathVariable UUID accommodationId,
            @PathVariable UUID periodId,
            @Valid @RequestBody UpdateAvailabilityPeriodRequest request,
            UserContext userContext) {
        return ResponseEntity.ok(availabilityPeriodService.update(accommodationId, periodId, request, userContext));
    }

    @DeleteMapping("/{periodId}")
    @RequireRole("HOST")
    public ResponseEntity<Void> delete(
            @PathVariable UUID accommodationId,
            @PathVariable UUID periodId,
            UserContext userContext) {
        availabilityPeriodService.delete(accommodationId, periodId, userContext);
        return ResponseEntity.noContent().build();
    }
}
