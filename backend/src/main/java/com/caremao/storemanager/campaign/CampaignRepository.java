package com.caremao.storemanager.campaign;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Optional<Campaign> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
