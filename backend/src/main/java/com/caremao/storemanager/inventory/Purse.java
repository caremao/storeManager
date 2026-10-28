package com.caremao.storemanager.inventory;

import com.caremao.storemanager.common.ApiException;

import jakarta.persistence.Embeddable;

/**
 * Bolsa de monedas. Porta la lógica de currency.py del proyecto original, con un cambio:
 * al gastar no re-denomina toda la bolsa, sino que paga con las monedas que hay y solo
 * rompe una moneda más grande cuando hace falta dar cambio.
 */
@Embeddable
public class Purse {

    private static final Denomination[] HIGH_TO_LOW = {
            Denomination.PLATINUM, Denomination.GOLD, Denomination.SILVER, Denomination.COPPER };

    private int copper;
    private int silver;
    private int gold;
    private int platinum;

    public Purse() {
    }

    public Purse(int copper, int silver, int gold, int platinum) {
        this.copper = copper;
        this.silver = silver;
        this.gold = gold;
        this.platinum = platinum;
    }

    public long totalCopper() {
        return copper
                + silver * (long) Denomination.SILVER.copperValue()
                + gold * (long) Denomination.GOLD.copperValue()
                + platinum * (long) Denomination.PLATINUM.copperValue();
    }

    /**
     * Ajuste manual con deltas por denominación (positivos suman, negativos gastan).
     * Si las monedas exactas no alcanzan pero el total sí, paga con cambio.
     */
    public void apply(int dCopper, int dSilver, int dGold, int dPlatinum) {
        Purse next = copy();
        next.copper = add(next.copper, Math.max(dCopper, 0));
        next.silver = add(next.silver, Math.max(dSilver, 0));
        next.gold = add(next.gold, Math.max(dGold, 0));
        next.platinum = add(next.platinum, Math.max(dPlatinum, 0));

        // En long: -Integer.MIN_VALUE no entra en un int.
        long[] spend = { Math.max(-(long) dCopper, 0), Math.max(-(long) dSilver, 0), Math.max(-(long) dGold, 0),
                Math.max(-(long) dPlatinum, 0) };
        if (next.copper >= spend[0] && next.silver >= spend[1] && next.gold >= spend[2] && next.platinum >= spend[3]) {
            next.copper = add(next.copper, -spend[0]);
            next.silver = add(next.silver, -spend[1]);
            next.gold = add(next.gold, -spend[2]);
            next.platinum = add(next.platinum, -spend[3]);
        } else {
            next.spend(spend[0] + spend[1] * Denomination.SILVER.copperValue()
                    + spend[2] * Denomination.GOLD.copperValue() + spend[3] * Denomination.PLATINUM.copperValue());
        }
        copyFrom(next);
    }

    /**
     * Paga {@code amount} cp: usa primero las monedas más grandes que caben y, si sobra algo
     * por pagar, rompe la moneda restante más chica y devuelve el cambio en denominaciones menores.
     */
    public void spend(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0");
        }
        if (totalCopper() < amount) {
            throw new InsufficientFundsException("Fondos insuficientes");
        }
        long[] counts = { platinum, gold, silver, copper };
        long remaining = amount;
        for (int i = 0; i < HIGH_TO_LOW.length; i++) {
            int value = HIGH_TO_LOW[i].copperValue();
            long used = Math.min(counts[i], remaining / value);
            counts[i] -= used;
            remaining -= used * value;
        }
        if (remaining > 0) {
            // Toda moneda que quedó vale más que lo que falta: se rompe la más chica.
            for (int i = HIGH_TO_LOW.length - 1; i >= 0; i--) {
                if (counts[i] > 0) {
                    counts[i]--;
                    long change = HIGH_TO_LOW[i].copperValue() - remaining;
                    for (int j = i + 1; j < HIGH_TO_LOW.length; j++) {
                        int value = HIGH_TO_LOW[j].copperValue();
                        counts[j] += change / value;
                        change %= value;
                    }
                    break;
                }
            }
        }
        platinum = add(0, counts[0]);
        gold = add(0, counts[1]);
        silver = add(0, counts[2]);
        copper = add(0, counts[3]);
    }

    /** Cobra {@code amount} cp en oro, plata y cobre (sin platino, como se cotiza en 5e). */
    public void receive(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0");
        }
        int newGold = add(gold, amount / 100);
        int newSilver = add(silver, (amount % 100) / 10);
        int newCopper = add(copper, amount % 10);
        gold = newGold;
        silver = newSilver;
        copper = newCopper;
    }

    /** Suma sin desbordar: una bolsa con más de ~2.100 millones de una moneda se rechaza. */
    private static int add(int current, long delta) {
        try {
            return Math.toIntExact(Math.addExact(current, delta));
        } catch (ArithmeticException e) {
            throw ApiException.badRequest("La bolsa no puede guardar tantas monedas");
        }
    }

    /**
     * Convierte {@code amount} monedas de una denominación a otra. Hacia arriba solo consume
     * lo necesario (250 cp → 2 gp deja 50 cp); hacia abajo convierte todo.
     */
    public void convert(Denomination from, Denomination to, int amount) {
        if (from == to) {
            throw ApiException.badRequest("No se puede convertir a la misma moneda");
        }
        if (amount <= 0) {
            throw ApiException.badRequest("La cantidad debe ser positiva");
        }
        int current = get(from);
        if (current < amount) {
            throw new InsufficientFundsException("No tienes suficientes monedas de " + label(from)
                    + ": tienes " + current + ", necesitas " + amount);
        }
        long totalCopper = (long) amount * from.copperValue();
        long converted = totalCopper / to.copperValue();
        if (converted == 0) {
            throw ApiException.badRequest("Cantidad muy pequeña para convertir a " + label(to));
        }
        long usedSource = converted * to.copperValue() / from.copperValue();
        int newTarget = add(get(to), converted);
        set(from, add(current, -usedSource));
        set(to, newTarget);
    }

    public int get(Denomination d) {
        return switch (d) {
            case COPPER -> copper;
            case SILVER -> silver;
            case GOLD -> gold;
            case PLATINUM -> platinum;
        };
    }

    private void set(Denomination d, int value) {
        switch (d) {
            case COPPER -> copper = value;
            case SILVER -> silver = value;
            case GOLD -> gold = value;
            case PLATINUM -> platinum = value;
        }
    }

    private static String label(Denomination d) {
        return switch (d) {
            case COPPER -> "cobre";
            case SILVER -> "plata";
            case GOLD -> "oro";
            case PLATINUM -> "platino";
        };
    }

    public Purse copy() {
        return new Purse(copper, silver, gold, platinum);
    }

    private void copyFrom(Purse other) {
        copper = other.copper;
        silver = other.silver;
        gold = other.gold;
        platinum = other.platinum;
    }

    public int getCopper() {
        return copper;
    }

    public int getSilver() {
        return silver;
    }

    public int getGold() {
        return gold;
    }

    public int getPlatinum() {
        return platinum;
    }

    @Override
    public String toString() {
        return platinum + "pp " + gold + "gp " + silver + "sp " + copper + "cp";
    }
}
