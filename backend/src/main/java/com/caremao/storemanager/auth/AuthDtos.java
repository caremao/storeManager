package com.caremao.storemanager.auth;

import java.util.UUID;

import com.caremao.storemanager.campaign.CampaignDtos.CampaignDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record MeResponse(Role role, CampaignDto campaign, UUID characterId, String characterName) {
    }

    public record GmLoginRequest(@NotBlank @Size(max = 200) String passphrase) {
    }

    /** El token viaja en el cuerpo y no en la ruta, para que no quede en logs de acceso. */
    public record JoinRequest(@NotBlank @Size(max = 64) String token) {
    }
}
