package com.caremao.storemanager.campaign;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.Tokens;
import com.caremao.storemanager.campaign.CampaignDtos.CampaignDto;
import com.caremao.storemanager.campaign.CampaignDtos.CreateCampaignRequest;
import com.caremao.storemanager.campaign.CampaignDtos.UpdateCampaignRequest;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.config.AppProperties;
import com.caremao.storemanager.events.CampaignEvent;

@Service
public class CampaignService {

    private static final int MAX_SLUG = 48;

    private final CampaignRepository campaigns;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final ApplicationEventPublisher events;

    public CampaignService(CampaignRepository campaigns, PasswordEncoder encoder, AppProperties props,
            ApplicationEventPublisher events) {
        this.campaigns = campaigns;
        this.encoder = encoder;
        this.props = props;
        this.events = events;
    }

    @Transactional
    public Campaign create(CreateCampaignRequest request) {
        if (props.campaignCreationKeyRequired() && (request.creationKey() == null
                || !Tokens.constantTimeEquals(request.creationKey(), props.campaignCreationKey()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Clave de creación incorrecta");
        }
        requireHashablePassphrase(request.passphrase());
        boolean customSlug = request.slug() != null && !request.slug().isBlank();
        String slug = uniqueSlug(customSlug ? request.slug() : request.name());
        return campaigns.save(new Campaign(slug, request.name().trim(), encoder.encode(request.passphrase())));
    }

    @Transactional
    public CampaignDto update(Actor actor, UpdateCampaignRequest request) {
        actor.requireGm();
        Campaign campaign = campaigns.findById(actor.campaignId()).orElseThrow(ApiException::unauthorized);
        if (request.playersCanAddCoins() != null) {
            campaign.setPlayersCanAddCoins(request.playersCanAddCoins());
        }
        events.publishEvent(CampaignEvent.rules(campaign.getId()));
        return CampaignDto.of(campaign);
    }

    /** bcrypt ignora (o rechaza) lo que pase de 72 bytes; con tildes eso son menos de 72 caracteres. */
    static void requireHashablePassphrase(String passphrase) {
        if (passphrase.getBytes(StandardCharsets.UTF_8).length > CampaignDtos.MAX_PASSPHRASE_BYTES) {
            throw ApiException.badRequest("La contraseña es demasiado larga (máximo "
                    + CampaignDtos.MAX_PASSPHRASE_BYTES + " bytes; las letras con tilde ocupan 2)");
        }
    }

    static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "campana" : slug;
    }

    private String uniqueSlug(String text) {
        String base = slugify(text);
        String slug = base;
        for (int n = 2; campaigns.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
