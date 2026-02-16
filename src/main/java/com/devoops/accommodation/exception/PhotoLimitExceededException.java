package com.devoops.accommodation.exception;

public class PhotoLimitExceededException extends RuntimeException {
    public PhotoLimitExceededException(String message) {
        super(message);
    }
}
