package com.caremao.storemanager.history;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoryRepository extends JpaRepository<HistoryEntry, UUID> {

    Page<HistoryEntry> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    /** Entradas de un inventario, incluidas las de trades donde participó como contraparte. */
    @Query("""
            select h from HistoryEntry h
            where h.campaignId = :campaignId
              and (h.inventoryId = :inventoryId or h.relatedInventoryId = :inventoryId)
            order by h.createdAt desc
            """)
    Page<HistoryEntry> findForInventory(@Param("campaignId") UUID campaignId,
            @Param("inventoryId") UUID inventoryId, Pageable pageable);
}
