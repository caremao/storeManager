package com.caremao.storemanager.campaign;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CampaignDtos {

    /** bcrypt solo usa los primeros 72 bytes; más largo se rechaza en vez de recortar en silencio. */
    public static final int MAX_PASSPHRASE_BYTES = 72;
    /** El nombre en minúsculas es el slug con el que entra el GM: una palabra, sin tildes ni símbolos. */
    public static final String NAME_PATTERN = "^[A-Za-z0-9]+$";
    public static final int MAX_NAME = 48;

    private CampaignDtos() {
    }

    public record CampaignDto(UUID id, String slug, String name, boolean playersCanAddCoins) {

        public static CampaignDto of(Campaign c) {
            return new CampaignDto(c.getId(), c.getSlug(), c.getName(), c.isPlayersCanAddCoins());
        }
    }

    public record CreateCampaignRequest(
            @NotBlank @Size(max = MAX_NAME)
            @Pattern(regexp = NAME_PATTERN, message = "solo letras (sin tildes) y números, sin espacios") String name,
            @NotBlank @Size(min = 8, max = MAX_PASSPHRASE_BYTES) String passphrase,
            @Size(max = 48) @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "solo minúsculas, números y guiones") String slug,
            @Size(max = 200) String creationKey) {
    }

    /** Reglas de la mesa que decide el GM. */
    public record UpdateCampaignRequest(Boolean playersCanAddCoins) {
    }

    public record ServerConfig(boolean campaignCreationKeyRequired) {
    }
}
