package com.sevak.api.common;

/** The request is valid but the resource is in the wrong state for it (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
