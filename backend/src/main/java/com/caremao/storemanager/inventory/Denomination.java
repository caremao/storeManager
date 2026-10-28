package com.caremao.storemanager.inventory;

/** Monedas de 5e y su valor en piezas de cobre (10 cp = 1 sp, 10 sp = 1 gp, 10 gp = 1 pp). */
public enum Denomination {
    COPPER(1),
    SILVER(10),
    GOLD(100),
    PLATINUM(1000);

    private final int copperValue;

    Denomination(int copperValue) {
        this.copperValue = copperValue;
    }

    public int copperValue() {
        return copperValue;
    }
}
