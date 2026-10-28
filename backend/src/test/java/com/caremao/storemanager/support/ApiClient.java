package com.caremao.storemanager.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP con su propio tarro de cookies: cada instancia es un navegador (GM o jugador). */
public class ApiClient {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    public record Response(int status, String body, HttpHeaders headers) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public <T> T as(Class<T> type) {
            return JSON.readValue(body, type);
        }

        public <T> T as(TypeReference<T> type) {
            return JSON.readValue(body, type);
        }

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private final String baseUrl;
    private final HttpClient http;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Response get(String path) {
        return send("GET", path, null, Map.of());
    }

    public Response post(String path, Object body) {
        return send("POST", path, body, Map.of());
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body, Map.of());
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body, Map.of());
    }

    public Response delete(String path) {
        return send("DELETE", path, null, Map.of());
    }

    /** Con cabeceras propias (Origin, Sec-Fetch-Site, X-Forwarded-For…). */
    public Response send(String method, String path, Object body, Map<String, String> headers) {
        BodyPublisher publisher = body == null
                ? BodyPublishers.noBody()
                : BodyPublishers.ofString(JSON.writeValueAsString(body));
        return sendRaw(method, path, publisher, headers);
    }

    /** Con un cuerpo arbitrario (p. ej. {@code ofInputStream}, que va chunked y sin Content-Length). */
    public Response sendRaw(String method, String path, BodyPublisher publisher, Map<String, String> headers) {
        try {
            HttpResponse<String> response = http.send(request(method, path, publisher, headers), BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public CompletableFuture<Response> postAsync(String path, Object body) {
        BodyPublisher publisher = BodyPublishers.ofString(JSON.writeValueAsString(body));
        return http.sendAsync(request("POST", path, publisher, Map.of()), BodyHandlers.ofString())
                .thenApply(r -> new Response(r.statusCode(), r.body(), r.headers()));
    }

    /** Abre /api/events y devuelve el flujo de líneas (para leerlo en otro hilo). */
    public CompletableFuture<HttpResponse<Stream<String>>> openEvents() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        return http.sendAsync(request, BodyHandlers.ofLines());
    }

    private HttpRequest request(String method, String path, BodyPublisher publisher, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, publisher);
        headers.forEach(builder::header);
        return builder.build();
    }
}
