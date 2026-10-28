package com.caremao.storemanager.common;

import org.springframework.http.HttpStatus;

/** Error de negocio con su código HTTP; el mensaje llega tal cual a la UI. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " no encontrado");
    }

    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso para esto");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
