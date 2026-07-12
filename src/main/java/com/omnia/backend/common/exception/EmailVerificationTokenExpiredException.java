package com.omnia.backend.common.exception;

public class EmailVerificationTokenExpiredException
        extends RuntimeException {

    public EmailVerificationTokenExpiredException(String message) {
        super(message);
    }
}