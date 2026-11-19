package com.omnia.backend.common.exception;

/** The request is well formed but conflicts with current state (stale view, reused key). HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
