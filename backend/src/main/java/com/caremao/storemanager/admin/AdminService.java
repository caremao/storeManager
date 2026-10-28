package com.caremao.storemanager.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caremao.storemanager.admin.AdminDtos.AdminCampaignDto;
import com.caremao.storemanager.admin.AdminDtos.AdminInventoryDto;
import com.caremao.storemanager.admin.AdminDtos.AdminSessionDto;
import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.AppSession;
import com.caremao.storemanager.auth.AppSessionRepository;
import com.caremao.storemanager.auth.AuthService;
import com.caremao.storemanager.auth.AuthService.Login;
import com.caremao.storemanager.auth.LoginRateLimiter;
import com.caremao.storemanager.auth.Role;
import com.caremao.storemanager.auth.Tokens;
import com.caremao.storemanager.campaign.Campaign;
import com.caremao.storemanager.campaign.CampaignRepository;
import com.caremao.storemanager.campaign.CampaignService;
import com.caremao.storemanager.common.ApiException;
import com.caremao.storemanager.config.AppProperties;
import com.caremao.storemanager.events.SessionsClosed;
import com.caremao.storemanager.inventory.Inventory;
import com.caremao.storemanager.inventory.InventoryRepository;
import com.caremao.storemanager.trade.TradeStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * Portal de administración: ve todas las campañas y actúa sobre ellas. Está por encima de las
 * campañas, así que solo existe si {@code app.admin-key} está definida. Cada acción queda en el log.
 */
@Service
public class AdminService {

    public static final String COOKIE = "sm_admin";
    /** La cookie del portal solo viaja a su API, nunca al resto de la app. */
    static final String COOKIE_PATH = "/api/admin";

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminSessions sessions;
    private final LoginRateLimiter limiter;
    private final AppProperties props;
    private final CampaignRepository campaigns;
    private final CampaignService campaignService;
    private final InventoryRepository inventories;
    private final AppSessionRepository appSessions;
    private final AuthService auth;
    private final EntityManager em;
    private final ApplicationEventPublisher events;

    public AdminService(AdminSessions sessions, LoginRateLimiter limiter, AppProperties props,
            CampaignRepository campaigns, CampaignService campaignService, InventoryRepository inventories,
            AppSessionRepository appSessions, AuthService auth, EntityManager em, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.limiter = limiter;
        this.props = props;
        this.campaigns = campaigns;
        this.campaignService = campaignService;
        this.inventories = inventories;
        this.appSessions = appSessions;
        this.auth = auth;
        this.em = em;
        this.events = events;
        if (!props.adminEnabled() && props.adminKey() != null && !props.adminKey().isBlank()) {
            log.warn("ADMIN_KEY tiene menos de {} caracteres: el portal /admin queda apagado",
                    AppProperties.MIN_ADMIN_KEY_LENGTH);
        }
    }

    void requireEnabled() {
        if (!props.adminEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El portal de administración está apagado");
        }
    }

    /** Se comparan los SHA-256 para que el tiempo de respuesta no delate el largo de la clave. */
    String login(String key, String clientIp) {
        requireEnabled();
        limiter.check(clientIp, LoginRateLimiter.ADMIN);
        if (!Tokens.constantTimeEquals(Tokens.sha256(key), Tokens.sha256(props.adminKey()))) {
            limiter.recordFailure(clientIp, LoginRateLimiter.ADMIN);
            log.warn("Portal admin: clave incorrecta desde {}", clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave incorrecta");
        }
        limiter.reset(clientIp);
        log.info("Portal admin: sesión abierta desde {}", clientIp);
        return sessions.start();
    }

    void logout(Admin admin) {
        sessions.end(admin.token());
    }

    ResponseCookie sessionCookie(String token) {
        return baseCookie(token).maxAge(props.adminSessionTtl()).build();
    }

    ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }

    @Transactional(readOnly = true)
    public List<AdminCampaignDto> campaigns() {
        Instant now = Instant.now();
        // Totales de todas las campañas en tres consultas, en vez de tres por campaña.
        Map<UUID, Long> itemsByInventory = byId(em.createQuery(
                "select i.inventoryId, count(i) from Item i group by i.inventoryId", Object[].class));
        Map<UUID, Long> openTradesByCampaign = byId(em.createQuery("""
                select t.campaignId, count(t) from TradeRequest t
                where t.status in :open group by t.campaignId
                """, Object[].class).setParameter("open", List.of(TradeStatus.PENDING, TradeStatus.COUNTERED)));
        Map<UUID, Instant> lastActivityByCampaign = byId(em.createQuery(
                "select h.campaignId, max(h.createdAt) from HistoryEntry h group by h.campaignId", Object[].class));

        return campaigns.findAll(Sort.by("name")).stream().map(campaign -> {
            List<Inventory> invs = inventories.findByCampaignIdOrderByKindAscNameAsc(campaign.getId());
            Map<UUID, String> names = invs.stream().collect(Collectors.toMap(Inventory::getId, Inventory::getName));
            List<AdminInventoryDto> inventoryDtos = invs.stream().map(inv -> new AdminInventoryDto(
                    inv.getId(), inv.getKind(), inv.getName(), inv.getPlayerName(),
                    itemsByInventory.getOrDefault(inv.getId(), 0L), inv.getPurse().totalCopper(),
                    inv.isShop() ? inv.isVisibleToPlayers() : null)).toList();
            List<AdminSessionDto> sessionDtos = appSessions
                    .findByCampaignIdAndExpiresAtAfterOrderByCreatedAtDesc(campaign.getId(), now).stream()
                    .map(s -> AdminSessionDto.of(s, s.getCharacterId() == null ? null : names.get(s.getCharacterId())))
                    .toList();
            return new AdminCampaignDto(campaign.getId(), campaign.getSlug(), campaign.getName(),
                    campaign.getCreatedAt(), lastActivityByCampaign.get(campaign.getId()),
                    openTradesByCampaign.getOrDefault(campaign.getId(), 0L), inventoryDtos, sessionDtos);
        }).toList();
    }

    /**
     * Abre una sesión de GM en la campaña, sin su contraseña. Si el navegador ya tenía otra sesión
     * (de esta u otra campaña), esa se cierra: la cookie nueva la reemplaza.
     */
    @Transactional
    public Login enterAsGm(UUID campaignId, Actor current) {
        Campaign campaign = requireCampaign(campaignId);
        if (current != null) {
            appSessions.deleteById(current.sessionId());
            events.publishEvent(new SessionsClosed(current.campaignId(), Set.of(current.sessionId())));
        }
        log.info("Portal admin: entró como GM a la campaña {}", campaign.getSlug());
        return auth.startGmSession(campaign);
    }

    /** La contraseña vieja deja de servir y quien entró con ella (o desde el portal) queda afuera. */
    @Transactional
    public void resetPassphrase(UUID campaignId, String passphrase) {
        Campaign campaign = campaignService.resetPassphrase(campaignId, passphrase);
        List<AppSession> gmSessions = appSessions.findByCampaignIdAndRole(campaignId, Role.GM);
        appSessions.deleteAll(gmSessions);
        limiter.resetCampaign(campaign.getSlug());
        events.publishEvent(new SessionsClosed(campaignId, ids(gmSessions)));
        log.info("Portal admin: contraseña de GM nueva en la campaña {}; {} sesiones de GM cerradas",
                campaign.getSlug(), gmSessions.size());
    }

    @Transactional
    public void closeSession(UUID sessionId) {
        AppSession session = appSessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sesión no encontrada"));
        appSessions.delete(session);
        events.publishEvent(new SessionsClosed(session.getCampaignId(), Set.of(session.getId())));
        log.info("Portal admin: cerró una sesión de {} en la campaña {}", session.getRole(), session.getCampaignId());
    }

    /** Tiendas, personajes, items, solicitudes, historial y sesiones caen en cascada desde la base. */
    @Transactional
    public void deleteCampaign(UUID campaignId) {
        Campaign campaign = requireCampaign(campaignId);
        Set<UUID> sessionIds = ids(appSessions.findByCampaignId(campaignId));
        campaigns.delete(campaign);
        events.publishEvent(new SessionsClosed(campaignId, sessionIds));
        log.info("Portal admin: borró la campaña {} ({})", campaign.getSlug(), campaign.getName());
    }

    private Campaign requireCampaign(UUID id) {
        return campaigns.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
    }

    /** Filas {@code (id, valor)} de un {@code group by}, como mapa. */
    @SuppressWarnings("unchecked")
    private static <V> Map<UUID, V> byId(TypedQuery<Object[]> query) {
        return query.getResultStream().collect(Collectors.toMap(r -> (UUID) r[0], r -> (V) r[1]));
    }

    private static Set<UUID> ids(List<AppSession> list) {
        return list.stream().map(AppSession::getId).collect(Collectors.toSet());
    }
}
