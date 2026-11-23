package com.omnia.backend.common.exception;

/** The caller sent too many requests in a short time. HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
