package com.caremao.storemanager.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.caremao.storemanager.auth.AppSession;
import com.caremao.storemanager.auth.Role;
import com.caremao.storemanager.campaign.CampaignDtos;
import com.caremao.storemanager.inventory.InventoryKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminLoginRequest(@NotBlank @Size(max = 200) String key) {
    }

    public record ResetPassphraseRequest(
            @NotBlank @Size(min = 8, max = CampaignDtos.MAX_PASSPHRASE_BYTES) String passphrase) {
    }

    /** Una campaña con todo lo que tiene; {@code lastActivityAt} es el último movimiento del historial. */
    public record AdminCampaignDto(UUID id, String slug, String name, Instant createdAt, Instant lastActivityAt,
            long openTrades, List<AdminInventoryDto> inventories, List<AdminSessionDto> sessions) {
    }

    /** {@code visibleToPlayers} es null en los personajes. */
    public record AdminInventoryDto(UUID id, InventoryKind kind, String name, String playerName, long items,
            long totalCp, Boolean visibleToPlayers) {
    }

    /** Un navegador con sesión; {@code characterName} es null en las de GM. */
    public record AdminSessionDto(UUID id, Role role, String characterName, Instant createdAt, Instant expiresAt) {

        static AdminSessionDto of(AppSession s, String characterName) {
            return new AdminSessionDto(s.getId(), s.getRole(), characterName, s.getCreatedAt(), s.getExpiresAt());
        }
    }
}
