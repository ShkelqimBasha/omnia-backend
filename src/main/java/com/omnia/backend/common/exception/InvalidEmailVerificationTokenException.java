package com.omnia.backend.common.exception;

public class InvalidEmailVerificationTokenException
        extends RuntimeException {

    public InvalidEmailVerificationTokenException(String message) {
        super(message);
    }
}