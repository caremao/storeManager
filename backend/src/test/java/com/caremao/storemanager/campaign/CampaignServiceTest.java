package com.caremao.storemanager.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampaignServiceTest {

    @Test
    void slugifyStripsAccentsAndSymbols() {
        assertThat(CampaignService.slugify("Los Gemflower")).isEqualTo("los-gemflower");
        assertThat(CampaignService.slugify("  Campaña de Ñandú!! ")).isEqualTo("campana-de-nandu");
        assertThat(CampaignService.slugify("¿?")).isEqualTo("campana");
    }
}
