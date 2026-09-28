package com.alertas.shared.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException noEncontrado(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException invalido(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflicto(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException prohibido(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException noAutorizado(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }
}
