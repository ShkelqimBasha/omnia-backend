package com.omnia.backend.common.exception;

/** A per-user limit was reached (e.g. product reports per day). HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
