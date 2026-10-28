package com.caremao.storemanager.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PricingTest {

    @Test
    void appliesPercentageRoundingToNearestCopper() {
        assertThat(Pricing.apply(5_000L, 100)).isEqualTo(5_000L);
        assertThat(Pricing.apply(1_500L, 50)).isEqualTo(750L);
        assertThat(Pricing.apply(15L, 50)).isEqualTo(8L);
        assertThat(Pricing.apply(null, 50)).isNull();
    }

    @Test
    void formatsCopperAsCoins() {
        assertThat(Pricing.format(1_250)).isEqualTo("12 po 5 pp");
        assertThat(Pricing.format(7)).isEqualTo("7 pc");
        assertThat(Pricing.format(0)).isEqualTo("0 pc");
    }
}
