package com.caremao.storemanager.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.caremao.storemanager.auth.Tokens;
import com.caremao.storemanager.config.AppProperties;

/**
 * Sesiones del portal admin. Viven en memoria: reiniciar la app (p. ej. al cambiar ADMIN_KEY) las
 * cierra todas. Como en las de GM y jugador, solo se guarda el SHA-256 del token.
 */
@Component
class AdminSessions {

    private final Map<String, Instant> expiresByHash = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AppProperties props;

    AdminSessions(Clock clock, AppProperties props) {
        this.clock = clock;
        this.props = props;
    }

    String start() {
        String token = Tokens.random(32);
        expiresByHash.put(Tokens.sha256(token), clock.instant().plus(props.adminSessionTtl()));
        return token;
    }

    boolean isValid(String token) {
        Instant expiresAt = expiresByHash.get(Tokens.sha256(token));
        return expiresAt != null && expiresAt.isAfter(clock.instant());
    }

    void end(String token) {
        expiresByHash.remove(Tokens.sha256(token));
    }

    @Scheduled(fixedRate = 600_000)
    void purgeExpired() {
        Instant now = clock.instant();
        expiresByHash.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }
}
