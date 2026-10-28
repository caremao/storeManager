package com.caremao.storemanager.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
        String dataDir,
        String publicBaseUrl,
        boolean cookieSecure,
        Duration gmSessionTtl,
        Duration playerSessionTtl,
        String campaignCreationKey,
        long maxRequestBytes,
        String adminKey,
        Duration adminSessionTtl) {

    /** Más corta, la clave del portal admin (que ve todas las campañas) no lo enciende. */
    public static final int MIN_ADMIN_KEY_LENGTH = 16;

    /**
     * QR de un personaje. El token va en el fragmento ({@code #}): el navegador no lo envía al
     * servidor, así que no queda en los logs de Caddy ni de Funnel. La SPA lo lee y lo canjea por una cookie.
     */
    public String joinUrl(String token) {
        return base() + "/join#" + token;
    }

    /** QR de la vitrina pública de una tienda, con el mismo patrón de fragmento. */
    public String storefrontUrl(String token) {
        return base() + "/vitrina#" + token;
    }

    public boolean campaignCreationKeyRequired() {
        return campaignCreationKey != null && !campaignCreationKey.isBlank();
    }

    public boolean adminEnabled() {
        return adminKey != null && !adminKey.isBlank() && adminKey.length() >= MIN_ADMIN_KEY_LENGTH;
    }

    private String base() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }
}
