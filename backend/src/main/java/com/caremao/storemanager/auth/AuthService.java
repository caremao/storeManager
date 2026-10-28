package com.caremao.storemanager.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.auth.AuthDtos.MeResponse;
import com.caremao.storemanager.campaign.Campaign;
import com.caremao.storemanager.campaign.CampaignDtos.CampaignDto;
import com.caremao.storemanager.campaign.CampaignRepository;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.config.AppProperties;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryRepository;

@Service
public class AuthService {

    /** Una sesión recién creada: el token va a la cookie y nunca se guarda en claro. */
    public record Login(String token, Duration ttl, MeResponse me) {
    }

    private final AppSessionRepository sessions;
    private final CampaignRepository campaigns;
    private final InventoryRepository inventories;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter limiter;
    private final AppProperties props;

    public AuthService(AppSessionRepository sessions, CampaignRepository campaigns, InventoryRepository inventories,
            PasswordEncoder encoder, LoginRateLimiter limiter, AppProperties props) {
        this.sessions = sessions;
        this.campaigns = campaigns;
        this.inventories = inventories;
        this.encoder = encoder;
        this.limiter = limiter;
        this.props = props;
    }

    @Transactional
    public Login startGmSession(Campaign campaign) {
        String token = createSession(campaign.getId(), Role.GM, null, props.gmSessionTtl());
        return new Login(token, props.gmSessionTtl(), new MeResponse(Role.GM, CampaignDto.of(campaign), null, null));
    }

    @Transactional
    public Login gmLogin(String slug, String passphrase, String clientIp) {
        String key = "gm:" + clientIp;
        limiter.check(key);
        Campaign campaign = campaigns.findBySlug(slug).orElse(null);
        if (campaign == null || !encoder.matches(passphrase, campaign.getGmPassphraseHash())) {
            limiter.recordFailure(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Campaña o contraseña incorrecta");
        }
        limiter.reset(key);
        return startGmSession(campaign);
    }

    @Transactional
    public Login join(String accessToken) {
        Inventory character = inventories.findByAccessToken(accessToken)
                .filter(Inventory::isCharacter)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Este QR ya no es válido; pídele uno nuevo al GM"));
        Campaign campaign = campaigns.findById(character.getCampaignId()).orElseThrow();
        String token = createSession(campaign.getId(), Role.PLAYER, character.getId(), props.playerSessionTtl());
        return new Login(token, props.playerSessionTtl(),
                new MeResponse(Role.PLAYER, CampaignDto.of(campaign), character.getId(), character.getName()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Actor actor) {
        Campaign campaign = campaigns.findById(actor.campaignId()).orElseThrow(ApiException::unauthorized);
        if (actor.isGm()) {
            return new MeResponse(Role.GM, CampaignDto.of(campaign), null, null);
        }
        Inventory character = inventories.findById(actor.characterId()).orElseThrow(ApiException::unauthorized);
        return new MeResponse(Role.PLAYER, CampaignDto.of(campaign), character.getId(), character.getName());
    }

    @Transactional
    public void logout(Actor actor) {
        sessions.deleteById(actor.sessionId());
    }

    public ResponseCookie sessionCookie(Login login) {
        return baseCookie(login.token()).maxAge(login.ttl()).build();
    }

    public ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(SessionFilter.COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }

    private String createSession(UUID campaignId, Role role, UUID characterId, Duration ttl) {
        String token = Tokens.random(32);
        sessions.save(new AppSession(Tokens.sha256(token), campaignId, role, characterId, Instant.now().plus(ttl)));
        return token;
    }

    @Scheduled(cron = "0 17 4 * * *")
    public void purgeExpiredSessions() {
        sessions.deleteExpired(Instant.now());
    }
}
