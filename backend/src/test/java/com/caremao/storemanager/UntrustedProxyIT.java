package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.caremao.storemanager.support.ApiClient;
import com.caremao.storemanager.support.ApiClient.Response;
import com.caremao.storemanager.support.IntegrationTest;

/**
 * Como un cliente que habla directo al puerto: aquí ninguna IP es proxy de confianza, así que
 * X-Forwarded-For se ignora y rotarlo no esquiva el límite por IP.
 */
@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.1")
class UntrustedProxyIT extends IntegrationTest {

    @Test
    void spoofedForwardedForDoesNotBypassThePerClientLimit() {
        ApiClient gm = newGm();
        String slug = slugOf(gm);
        for (int i = 0; i < 5; i++) {
            assertThat(loginFrom("10.2.0." + i, slug).status()).isEqualTo(401);
        }
        assertThat(loginFrom("10.2.0.99", slug).status()).isEqualTo(429);
    }

    private Response loginFrom(String spoofedIp, String slug) {
        return browser().send("POST", "/api/campaigns/" + slug + "/gm-login", Map.of("passphrase", "mala"),
                Map.of("X-Forwarded-For", spoofedIp));
    }
}
