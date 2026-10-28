package com.caremao.storemanager.campaign;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    public record CampaignDto(UUID id, String slug, String name) {

        public static CampaignDto of(Campaign c) {
            return new CampaignDto(c.getId(), c.getSlug(), c.getName());
        }
    }

    public record CreateCampaignRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 8, max = 200) String passphrase,
            @Size(max = 48) @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "solo minúsculas, números y guiones") String slug,
            String creationKey) {
    }

    public record ServerConfig(boolean campaignCreationKeyRequired) {
    }
}
