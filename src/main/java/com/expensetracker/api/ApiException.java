package com.expensetracker.api;

// Thrown anywhere in a handler to answer with a specific HTTP status, e.g. new ApiException(404, "Not found")
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}