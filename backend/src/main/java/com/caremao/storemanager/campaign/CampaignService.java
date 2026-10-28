package com.caremao.storemanager.campaign;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.Tokens;
import com.caremao.storemanager.campaign.CampaignDtos.CreateCampaignRequest;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.config.AppProperties;

@Service
public class CampaignService {

    private static final int MAX_SLUG = 48;

    private final CampaignRepository campaigns;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public CampaignService(CampaignRepository campaigns, PasswordEncoder encoder, AppProperties props) {
        this.campaigns = campaigns;
        this.encoder = encoder;
        this.props = props;
    }

    @Transactional
    public Campaign create(CreateCampaignRequest request) {
        if (props.campaignCreationKeyRequired() && (request.creationKey() == null
                || !Tokens.constantTimeEquals(request.creationKey(), props.campaignCreationKey()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Clave de creación incorrecta");
        }
        boolean customSlug = request.slug() != null && !request.slug().isBlank();
        String slug = uniqueSlug(customSlug ? request.slug() : request.name());
        return campaigns.save(new Campaign(slug, request.name().trim(), encoder.encode(request.passphrase())));
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
