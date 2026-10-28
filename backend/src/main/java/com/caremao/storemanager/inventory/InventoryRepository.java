package com.caremao.storemanager.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<Inventory> findByCampaignIdOrderByKindAscNameAsc(UUID campaignId);

    List<Inventory> findByCampaignIdAndKindOrderByNameAsc(UUID campaignId, InventoryKind kind);

    List<Inventory> findByCampaignIdAndKindAndVisibleToPlayersTrueOrderByNameAsc(UUID campaignId, InventoryKind kind);

    Optional<Inventory> findByAccessToken(String accessToken);

    Optional<Inventory> findByPublicToken(String publicToken);
}
