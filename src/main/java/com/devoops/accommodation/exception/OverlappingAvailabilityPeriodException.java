package com.devoops.accommodation.exception;

public class OverlappingAvailabilityPeriodException extends RuntimeException {
    public OverlappingAvailabilityPeriodException(String message) {
        super(message);
    }
}
