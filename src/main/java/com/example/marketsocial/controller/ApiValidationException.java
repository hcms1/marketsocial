package com.example.marketsocial.controller;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiValidationException extends RuntimeException {
    private final HttpStatus status;
    private final List<String> details;

    public ApiValidationException(HttpStatus status, List<String> details) {
        super(details == null || details.isEmpty() ? status.getReasonPhrase() : details.getFirst());
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
