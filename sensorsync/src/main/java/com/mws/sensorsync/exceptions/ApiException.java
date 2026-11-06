package com.mws.sensorsync.exceptions;

import org.springframework.http.HttpStatus;

/** Erro de negocio com status HTTP associado. */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg); }
}
