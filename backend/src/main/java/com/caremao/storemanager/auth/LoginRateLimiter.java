package com.caremao.storemanager.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.caremao.storemanager.common.ApiException;

/**
 * Frena la fuerza bruta sobre el login del GM (la app queda pública vía Funnel).
 *
 * <p>Cuenta fallos por IP y por campaña. El tope por campaña es el que de verdad acota los intentos:
 * no depende de la IP, así que no se esquiva rotando direcciones. Solo se cuentan campañas que
 * existen, de modo que ese mapa no crece con slugs inventados; el de IPs tiene un máximo.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES_PER_CLIENT = 5;
    static final int MAX_FAILURES_PER_CAMPAIGN = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> byClient = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byCampaign = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientIp, String slug) {
        if (recent(byClient, clientIp) >= MAX_FAILURES_PER_CLIENT
                || recent(byCampaign, slug) >= MAX_FAILURES_PER_CAMPAIGN) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos; espera unos minutos.");
        }
    }

    /** {@code existingCampaignSlug} es null si la campaña no existe: así no se cuenta un slug inventado. */
    public void recordFailure(String clientIp, String existingCampaignSlug) {
        if (byClient.size() >= MAX_TRACKED_CLIENTS && !byClient.containsKey(clientIp)) {
            evictStale();
            if (byClient.size() >= MAX_TRACKED_CLIENTS) {
                // Lleno de IPs con fallos recientes: se suelta el registro por IP. El tope por campaña sigue.
                byClient.clear();
            }
        }
        record(byClient, clientIp);
        if (existingCampaignSlug != null) {
            record(byCampaign, existingCampaignSlug);
        }
    }

    public void reset(String clientIp) {
        byClient.remove(clientIp);
    }

    int trackedClients() {
        return byClient.size();
    }

    @Scheduled(fixedRate = 60_000)
    public void evictStale() {
        evict(byClient);
        evict(byCampaign);
    }

    private int recent(Map<String, Deque<Instant>> map, String key) {
        Deque<Instant> attempts = map.get(key);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            prune(attempts);
            return attempts.size();
        }
    }

    private void record(Map<String, Deque<Instant>> map, String key) {
        Deque<Instant> attempts = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    private void evict(Map<String, Deque<Instant>> map) {
        map.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}
