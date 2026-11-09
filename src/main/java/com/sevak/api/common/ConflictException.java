package com.sevak.api.common;

/** The request is valid but clashes with the current state (HTTP 409). */
public class ConflictException extends RuntimeException {

    private final String type;

    public ConflictException(String message) {
        this("invalid-state", message);
    }

    public ConflictException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
