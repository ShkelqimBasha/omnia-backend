package com.omnia.backend.common.exception;

/** The caller hit a rate limit (too many requests or reports in a period). HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
