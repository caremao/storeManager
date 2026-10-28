package com.caremao.storemanager.trade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<TradeRequest, UUID> {

    Optional<TradeRequest> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<TradeRequest> findTop100ByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<TradeRequest> findByCampaignIdAndStatusOrderByCreatedAtDesc(UUID campaignId, TradeStatus status);

    List<TradeRequest> findTop100ByCharacterIdOrderByCreatedAtDesc(UUID characterId);
}
