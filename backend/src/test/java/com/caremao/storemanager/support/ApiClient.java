package com.caremao.storemanager.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP con su propio tarro de cookies: cada instancia es un navegador (GM o jugador). */
public class ApiClient {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    public record Response(int status, String body) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        public <T> T as(Class<T> type) {
            return JSON.readValue(body, type);
        }

        public <T> T as(TypeReference<T> type) {
            return JSON.readValue(body, type);
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

    public Response get(String path) {
        return send("GET", path, null);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body);
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body);
    }

    public Response delete(String path) {
        return send("DELETE", path, null);
    }

    public CompletableFuture<Response> postAsync(String path, Object body) {
        return http.sendAsync(request("POST", path, body), BodyHandlers.ofString())
                .thenApply(r -> new Response(r.statusCode(), r.body()));
    }

    /** Abre /api/events y devuelve el flujo de líneas (para leerlo en otro hilo). */
    public CompletableFuture<HttpResponse<Stream<String>>> openEvents() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        return http.sendAsync(request, BodyHandlers.ofLines());
    }

    private Response send(String method, String path, Object body) {
        try {
            HttpResponse<String> response = http.send(request(method, path, body), BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest request(String method, String path, Object body) {
        BodyPublisher publisher = body == null
                ? BodyPublishers.noBody()
                : BodyPublishers.ofString(JSON.writeValueAsString(body));
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, publisher)
                .build();
    }
}
