package com.caremao.storemanager.events;

import java.util.List;
import java.util.UUID;

/**
 * Algo cambió en la campaña. Viaja por SSE como aviso: el cliente vuelve a pedir lo que
 * le interesa y la autorización de cada GET decide qué ve.
 */
public record CampaignEvent(UUID campaignId, List<UUID> inventoryIds, List<UUID> tradeIds) {

    public static CampaignEvent inventories(UUID campaignId, UUID... inventoryIds) {
        return new CampaignEvent(campaignId, List.of(inventoryIds), List.of());
    }

    public static CampaignEvent trade(UUID campaignId, UUID tradeId, UUID... inventoryIds) {
        return new CampaignEvent(campaignId, List.of(inventoryIds), List.of(tradeId));
    }
}
