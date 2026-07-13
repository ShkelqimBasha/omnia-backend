package com.omnia.backend.common.exception;

public class PasswordResetTokenExpiredException extends RuntimeException {

    public PasswordResetTokenExpiredException(String message) {
        super(message);
    }
}