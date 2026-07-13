package com.omnia.backend.common.exception;

public class PasswordResetTokenUsedException extends RuntimeException {

    public PasswordResetTokenUsedException(String message) {
        super(message);
    }
}