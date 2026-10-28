package com.caremao.storemanager.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.caremao.storemanager.common.ApiException;

/** Casos derivados de test_currency.py del proyecto original, más los del nuevo algoritmo de pago. */
class PurseTest {

    @Test
    void totalInCopper() {
        // (50/100) + (25/10) + 100 + (5*10) = 153 gp
        assertThat(new Purse(50, 25, 100, 5).totalCopper()).isEqualTo(15_300);
    }

    @Test
    void applyAddsPerDenomination() {
        Purse purse = new Purse();
        purse.apply(5, 0, 3, 0);
        assertPurse(purse, 5, 0, 3, 0);
    }

    @Test
    void applySpendsExactCoinsWhenTheyAreThere() {
        Purse purse = new Purse(0, 4, 10, 0);
        purse.apply(0, 0, -3, 0);
        assertPurse(purse, 0, 4, 7, 0);
    }

    @Test
    void applyBreaksPlatinumWhenGoldIsNotEnough() {
        // Caso del original: gastar 15 gp teniendo 10 gp y 1 pp
        Purse purse = new Purse(0, 0, 10, 1);
        purse.apply(0, 0, -15, 0);
        assertPurse(purse, 0, 0, 5, 0);
    }

    @Test
    void spendKeepsDenominationsInsteadOfConvertingEverything() {
        Purse purse = new Purse(0, 0, 100, 0);
        purse.spend(5_000);
        assertPurse(purse, 0, 0, 50, 0);
    }

    @Test
    void spendBreaksTheSmallestCoinAndReturnsChange() {
        Purse purse = new Purse(0, 0, 1, 0);
        purse.spend(3);
        assertPurse(purse, 7, 9, 0, 0);
    }

    @Test
    void spendUsesSmallCoinsBeforeBreakingABigOne() {
        Purse purse = new Purse(5, 0, 0, 1);
        purse.spend(7);
        // 5 cp + romper el platino: 1005 - 7 = 998 = 9 gp 9 sp 8 cp
        assertPurse(purse, 8, 9, 9, 0);
        assertThat(purse.totalCopper()).isEqualTo(998);
    }

    @Test
    void spendFailsWithoutTouchingThePurse() {
        Purse purse = new Purse(0, 0, 1, 0);
        assertThatThrownBy(() -> purse.spend(101)).isInstanceOf(InsufficientFundsException.class);
        assertPurse(purse, 0, 0, 1, 0);
    }

    @Test
    void applyFailsAtomicallyWhenTotalIsNotEnough() {
        Purse purse = new Purse(0, 5, 0, 0);
        assertThatThrownBy(() -> purse.apply(0, 0, -1, 0)).isInstanceOf(InsufficientFundsException.class);
        assertPurse(purse, 0, 5, 0, 0);
    }

    @Test
    void receivePaysInGoldSilverAndCopper() {
        Purse purse = new Purse();
        purse.receive(2_575);
        assertPurse(purse, 5, 7, 25, 0);
    }

    @Test
    void convertUpConsumesOnlyWhatIsNeeded() {
        Purse purse = new Purse(250, 0, 0, 0);
        purse.convert(Denomination.COPPER, Denomination.GOLD, 250);
        assertPurse(purse, 50, 0, 2, 0);
    }

    @Test
    void convertDownConvertsEverything() {
        Purse purse = new Purse(0, 0, 3, 0);
        purse.convert(Denomination.GOLD, Denomination.SILVER, 3);
        assertPurse(purse, 0, 30, 0, 0);
    }

    @Test
    void convertRejectsSameDenominationAndTinyAmounts() {
        Purse purse = new Purse(5, 0, 1, 0);
        assertThatThrownBy(() -> purse.convert(Denomination.GOLD, Denomination.GOLD, 1))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purse.convert(Denomination.COPPER, Denomination.GOLD, 5))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purse.convert(Denomination.GOLD, Denomination.SILVER, 2))
                .isInstanceOf(InsufficientFundsException.class);
    }

    private static void assertPurse(Purse purse, int cp, int sp, int gp, int pp) {
        assertThat(new int[] { purse.getCopper(), purse.getSilver(), purse.getGold(), purse.getPlatinum() })
                .as("cp, sp, gp, pp de %s", purse)
                .containsExactly(cp, sp, gp, pp);
    }
}
