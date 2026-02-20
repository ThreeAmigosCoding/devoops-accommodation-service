package com.devoops.accommodation.controller;

import com.devoops.accommodation.config.RequireRole;
import com.devoops.accommodation.config.UserContext;
import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.request.UpdateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.dto.response.AccommodationSearchResponse;
import com.devoops.accommodation.service.AccommodationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accommodation")
@RequiredArgsConstructor
public class AccommodationController {

    private final AccommodationService accommodationService;

    @PostMapping
    @RequireRole("HOST")
    public ResponseEntity<AccommodationResponse> create(
            @Valid @RequestBody CreateAccommodationRequest request,
            UserContext userContext) {
        AccommodationResponse response = accommodationService.create(request, userContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<AccommodationResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(accommodationService.getAll(page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AccommodationSearchResponse>> search(
            @RequestParam String location,
            @RequestParam int guests,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(accommodationService.search(location, guests, startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccommodationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(accommodationService.getById(id));
    }

    @GetMapping("/host/{hostId}")
    public ResponseEntity<List<AccommodationResponse>> getByHostId(@PathVariable UUID hostId) {
        return ResponseEntity.ok(accommodationService.getByHostId(hostId));
    }

    @PutMapping("/{id}")
    @RequireRole("HOST")
    public ResponseEntity<AccommodationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccommodationRequest request,
            UserContext userContext) {
        return ResponseEntity.ok(accommodationService.update(id, request, userContext));
    }

    @DeleteMapping("/{id}")
    @RequireRole("HOST")
    public ResponseEntity<Void> delete(@PathVariable UUID id, UserContext userContext) {
        accommodationService.delete(id, userContext);
        return ResponseEntity.noContent().build();
    }
}
