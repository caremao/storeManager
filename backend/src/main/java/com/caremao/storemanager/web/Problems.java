package com.caremao.storemanager.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Respuestas de error desde filtros, con el mismo formato ProblemDetail que el resto de la API. */
final class Problems {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Problems() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(Map.of(
                "status", status.value(), "title", status.getReasonPhrase(), "detail", detail)));
    }
}
