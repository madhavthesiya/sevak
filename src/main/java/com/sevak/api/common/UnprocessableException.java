package com.sevak.api.common;

/** The request is well-formed but breaks a business rule (HTTP 422). */
public class UnprocessableException extends RuntimeException {
    public UnprocessableException(String message) {
        super(message);
    }
}
