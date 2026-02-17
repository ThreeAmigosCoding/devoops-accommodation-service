package com.devoops.accommodation.grpc;

import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import com.devoops.accommodation.entity.PricingMode;
import com.devoops.accommodation.grpc.proto.AccommodationInternalServiceGrpc;
import com.devoops.accommodation.grpc.proto.ReservationValidationRequest;
import com.devoops.accommodation.grpc.proto.ReservationValidationResponse;
import com.devoops.accommodation.repository.AccommodationRepository;
import com.devoops.accommodation.repository.AvailabilityPeriodRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AccommodationGrpcService extends AccommodationInternalServiceGrpc.AccommodationInternalServiceImplBase {

    private final AccommodationRepository accommodationRepository;
    private final AvailabilityPeriodRepository availabilityPeriodRepository;

    @Override
    public void validateAndCalculatePrice(
            ReservationValidationRequest request,
            StreamObserver<ReservationValidationResponse> responseObserver) {

        log.debug("Received validation request for accommodation: {}", request.getAccommodationId());

        ReservationValidationResponse response = processValidation(request);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private ReservationValidationResponse processValidation(ReservationValidationRequest request) {
        UUID accommodationId;
        LocalDate startDate;
        LocalDate endDate;

        try {
            accommodationId = UUID.fromString(request.getAccommodationId());
        } catch (IllegalArgumentException e) {
            return buildErrorResponse("ACCOMMODATION_NOT_FOUND", "Invalid accommodation ID format");
        }

        try {
            startDate = LocalDate.parse(request.getStartDate());
            endDate = LocalDate.parse(request.getEndDate());
        } catch (DateTimeParseException e) {
            return buildErrorResponse("INVALID_DATES", "Invalid date format. Use ISO format (yyyy-MM-dd)");
        }

        Optional<Accommodation> accommodationOpt = accommodationRepository.findById(accommodationId);
        if (accommodationOpt.isEmpty()) {
            log.debug("Accommodation not found: {}", accommodationId);
            return buildErrorResponse("ACCOMMODATION_NOT_FOUND", "Accommodation not found");
        }

        Accommodation accommodation = accommodationOpt.get();
        int guestCount = request.getGuestCount();

        if (guestCount < accommodation.getMinGuests() || guestCount > accommodation.getMaxGuests()) {
            log.debug("Invalid guest count {} for accommodation {} (min: {}, max: {})",
                    guestCount, accommodationId, accommodation.getMinGuests(), accommodation.getMaxGuests());
            return buildErrorResponse("GUEST_COUNT_INVALID",
                    String.format("Guest count must be between %d and %d",
                            accommodation.getMinGuests(), accommodation.getMaxGuests()));
        }

        Optional<AvailabilityPeriod> periodOpt = availabilityPeriodRepository
                .findCoveringPeriod(accommodationId, startDate, endDate);

        if (periodOpt.isEmpty()) {
            log.debug("No availability period covers dates {} to {} for accommodation {}",
                    startDate, endDate, accommodationId);
            return buildErrorResponse("DATES_NOT_AVAILABLE",
                    "The selected dates are not within an available period");
        }

        AvailabilityPeriod period = periodOpt.get();
        BigDecimal totalPrice = calculateTotalPrice(period, accommodation.getPricingMode(), startDate, endDate, guestCount);

        log.info("Validation successful for accommodation {}: totalPrice={}, approvalMode={}",
                accommodationId, totalPrice, accommodation.getApprovalMode());

        return ReservationValidationResponse.newBuilder()
                .setValid(true)
                .setHostId(accommodation.getHostId().toString())
                .setTotalPrice(totalPrice.toPlainString())
                .setPricingMode(accommodation.getPricingMode().name())
                .setApprovalMode(accommodation.getApprovalMode().name())
                .build();
    }

    private BigDecimal calculateTotalPrice(
            AvailabilityPeriod period,
            PricingMode pricingMode,
            LocalDate startDate,
            LocalDate endDate,
            int guestCount) {

        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal basePrice = period.getPricePerDay().multiply(BigDecimal.valueOf(nights));

        if (pricingMode == PricingMode.PER_GUEST) {
            return basePrice.multiply(BigDecimal.valueOf(guestCount));
        }

        return basePrice;
    }

    private ReservationValidationResponse buildErrorResponse(String errorCode, String errorMessage) {
        return ReservationValidationResponse.newBuilder()
                .setValid(false)
                .setErrorCode(errorCode)
                .setErrorMessage(errorMessage)
                .build();
    }
}
