package com.devoops.accommodation.exception;

public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String message) {
        super(message);
    }
}
