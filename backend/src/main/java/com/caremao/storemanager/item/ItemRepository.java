package com.caremao.storemanager.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByInventoryIdOrderByNameAsc(UUID inventoryId);

    Optional<Item> findByIdAndInventoryId(UUID id, UUID inventoryId);

    Optional<Item> findFirstByInventoryIdAndSrdIndex(UUID inventoryId, String srdIndex);

    Optional<Item> findFirstByInventoryIdAndNameIgnoreCase(UUID inventoryId, String name);
}
