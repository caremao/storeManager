package com.caremao.storemanager.events;

import java.util.List;
import java.util.UUID;

/**
 * Algo cambió en la campaña. Viaja por SSE como aviso: el cliente vuelve a pedir lo que
 * le interesa y la autorización de cada GET decide qué ve. {@code campaign} indica que
 * cambiaron las reglas de la mesa.
 */
public record CampaignEvent(UUID campaignId, List<UUID> inventoryIds, List<UUID> tradeIds, boolean campaign) {

    public static CampaignEvent inventories(UUID campaignId, UUID... inventoryIds) {
        return new CampaignEvent(campaignId, List.of(inventoryIds), List.of(), false);
    }

    public static CampaignEvent trade(UUID campaignId, UUID tradeId, UUID... inventoryIds) {
        return new CampaignEvent(campaignId, List.of(inventoryIds), List.of(tradeId), false);
    }

    public static CampaignEvent rules(UUID campaignId) {
        return new CampaignEvent(campaignId, List.of(), List.of(), true);
    }
}
