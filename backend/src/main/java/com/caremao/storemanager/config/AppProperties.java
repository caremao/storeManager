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
        String campaignCreationKey) {

    public String joinUrl(String token) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/join/" + token;
    }

    public boolean campaignCreationKeyRequired() {
        return campaignCreationKey != null && !campaignCreationKey.isBlank();
    }
}
