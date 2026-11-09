package com.sevak.api.common;

/** The request is well-formed but breaks a business rule (HTTP 422). */
public class UnprocessableException extends RuntimeException {

    private final String type;

    public UnprocessableException(String message) {
        this("business-rule", message);
    }

    public UnprocessableException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
