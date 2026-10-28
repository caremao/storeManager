package com.caremao.storemanager.auth;

import java.util.Objects;
import java.util.UUID;

import com.caremao.storemanager.common.ApiException;

/** Quién hace la petición: el GM de la campaña o el jugador de un personaje. */
public record Actor(UUID sessionId, UUID campaignId, Role role, UUID characterId) {

    public static final String REQUEST_ATTRIBUTE = Actor.class.getName();

    public boolean isGm() {
        return role == Role.GM;
    }

    public boolean isCharacter(UUID inventoryId) {
        return role == Role.PLAYER && Objects.equals(characterId, inventoryId);
    }

    public void requireGm() {
        if (!isGm()) {
            throw ApiException.forbidden();
        }
    }
}
