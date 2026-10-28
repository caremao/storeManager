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

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
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
        emitters.values().forEach(campaign -> campaign.forEach(SseEmitter::complete));
        emitters.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public SseEmitter subscribe(Actor actor) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> campaign = emitters.computeIfAbsent(actor.campaignId(), k -> new CopyOnWriteArraySet<>());
        campaign.add(emitter);
        emitter.onCompletion(() -> campaign.remove(emitter));
        emitter.onTimeout(() -> campaign.remove(emitter));
        emitter.onError(e -> campaign.remove(emitter));
        send(campaign, emitter, SseEmitter.event().name("ready").data(Map.of("ok", true)));
        return emitter;
    }

    /** Se emite solo después del commit: nadie se entera de un cambio que se revirtió. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onCampaignEvent(CampaignEvent event) {
        Set<SseEmitter> campaign = emitters.get(event.campaignId());
        if (campaign == null) {
            return;
        }
        Map<String, Object> payload = Map.of("inventoryIds", event.inventoryIds(), "tradeIds", event.tradeIds());
        for (SseEmitter emitter : campaign) {
            send(campaign, emitter, SseEmitter.event().name("change").data(payload));
        }
    }

    /** Mantiene viva la conexión a través de Caddy y Funnel. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        emitters.values().forEach(campaign -> {
            for (SseEmitter emitter : campaign) {
                send(campaign, emitter, SseEmitter.event().comment("hb"));
            }
        });
    }

    private static void send(Set<SseEmitter> campaign, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            campaign.remove(emitter);
        }
    }
}
