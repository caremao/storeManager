package com.caremao.storemanager.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Sesión de navegador; la cookie lleva el token y aquí solo se guarda su SHA-256. */
@Entity
@Table(name = "app_session")
public class AppSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String tokenHash;
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private UUID characterId;
    private Instant createdAt = Instant.now();
    private Instant expiresAt;

    protected AppSession() {
    }

    public AppSession(String tokenHash, UUID campaignId, Role role, UUID characterId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.campaignId = campaignId;
        this.role = role;
        this.characterId = characterId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public Role getRole() {
        return role;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Actor toActor() {
        return new Actor(id, campaignId, role, characterId);
    }
}
