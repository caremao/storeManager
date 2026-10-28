package com.caremao.storemanager.events;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.caremao.storemanager.auth.Actor;

/** Conexiones SSE abiertas, agrupadas por campaña. */
@Component
public class EventBroadcaster implements SmartLifecycle {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    /** Una conexión y la sesión que la abrió, para cortarla si esa sesión se cierra. */
    private record Connection(UUID sessionId, SseEmitter emitter) {
    }

    private final Map<UUID, Set<Connection>> connections = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    /**
     * Cierra las conexiones antes del apagado ordenado del servidor web, que si no espera a que
     * expiren. Los navegadores reconectan solos cuando la app vuelve.
     */
    @Override
    public void stop() {
        running = false;
        connections.values().forEach(campaign -> campaign.forEach(c -> c.emitter().complete()));
        connections.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public SseEmitter subscribe(Actor actor) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Connection connection = new Connection(actor.sessionId(), emitter);
        Set<Connection> campaign = connections.computeIfAbsent(actor.campaignId(), k -> new CopyOnWriteArraySet<>());
        campaign.add(connection);
        emitter.onCompletion(() -> campaign.remove(connection));
        emitter.onTimeout(() -> campaign.remove(connection));
        emitter.onError(e -> campaign.remove(connection));
        send(campaign, connection, SseEmitter.event().name("ready").data(Map.of("ok", true)));
        return emitter;
    }

    /** Se emite solo después del commit: nadie se entera de un cambio que se revirtió. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onCampaignEvent(CampaignEvent event) {
        Set<Connection> campaign = connections.get(event.campaignId());
        if (campaign == null) {
            return;
        }
        Map<String, Object> payload = Map.of("inventoryIds", event.inventoryIds(), "tradeIds", event.tradeIds(),
                "campaign", event.campaign());
        for (Connection connection : campaign) {
            send(campaign, connection, SseEmitter.event().name("change").data(payload));
        }
    }

    /** También después del commit: si el navegador reconectara antes, la sesión todavía existiría. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSessionsClosed(SessionsClosed event) {
        Set<Connection> campaign = connections.get(event.campaignId());
        if (campaign == null) {
            return;
        }
        for (Connection connection : campaign) {
            if (event.sessionIds().contains(connection.sessionId())) {
                campaign.remove(connection);
                connection.emitter().complete();
            }
        }
    }

    /** Mantiene viva la conexión a través de Caddy y Funnel. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        connections.values().forEach(campaign -> {
            for (Connection connection : campaign) {
                send(campaign, connection, SseEmitter.event().comment("hb"));
            }
        });
    }

    private static void send(Set<Connection> campaign, Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            campaign.remove(connection);
        }
    }
}
