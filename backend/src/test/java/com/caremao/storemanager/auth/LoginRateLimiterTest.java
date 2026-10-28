package com.caremao.storemanager.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.common.ApiException;

class LoginRateLimiterTest {

    /** Reloj que avanza a mano. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-28T20:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void blocksAClientAfterFiveFailuresUntilTheWindowPasses() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_CLIENT; i++) {
            limiter.check("1.1.1.1", "mesa");
            limiter.recordFailure("1.1.1.1", null);
        }
        assertThatThrownBy(() -> limiter.check("1.1.1.1", "mesa")).isInstanceOf(ApiException.class);
        assertThatCode(() -> limiter.check("2.2.2.2", "mesa")).as("otra IP sigue pudiendo").doesNotThrowAnyException();

        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertThatCode(() -> limiter.check("1.1.1.1", "mesa")).doesNotThrowAnyException();
    }

    @Test
    void blocksACampaignAcrossAllClients() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_CAMPAIGN; i++) {
            limiter.recordFailure("10.0.0." + i, "mesa");
        }
        assertThatThrownBy(() -> limiter.check("10.9.9.9", "mesa")).isInstanceOf(ApiException.class);
        assertThatCode(() -> limiter.check("10.9.9.9", "otra-mesa")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheClientButNotTheCampaign() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("1.1.1.1", "mesa");
        }
        limiter.reset("1.1.1.1");
        limiter.recordFailure("1.1.1.1", "mesa");
        assertThatCode(() -> limiter.check("1.1.1.1", "mesa")).doesNotThrowAnyException();
    }

    @Test
    void staleEntriesAreEvictedAndTheClientMapIsBounded() {
        for (int i = 0; i < 100; i++) {
            limiter.recordFailure("10.0." + (i / 256) + "." + (i % 256), null);
        }
        assertThat(limiter.trackedClients()).isEqualTo(100);
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        limiter.evictStale();
        assertThat(limiter.trackedClients()).isZero();

        for (int i = 0; i < LoginRateLimiter.MAX_TRACKED_CLIENTS + 50; i++) {
            limiter.recordFailure("ip-" + i, null);
        }
        assertThat(limiter.trackedClients()).isLessThanOrEqualTo(LoginRateLimiter.MAX_TRACKED_CLIENTS);
    }
}
