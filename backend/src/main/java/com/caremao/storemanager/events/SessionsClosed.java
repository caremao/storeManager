package com.caremao.storemanager.events;

import java.util.Set;
import java.util.UUID;

/**
 * Se cerraron sesiones (o se borró la campaña entera). Sus conexiones SSE se cortan: el navegador
 * reconecta, recibe 401 y vuelve a la pantalla de inicio sin esperar a su próximo pedido.
 */
public record SessionsClosed(UUID campaignId, Set<UUID> sessionIds) {
}
