package com.caremao.storemanager.trade;

public enum TradeStatus {
    /** Le toca al GM: aprobar, contraofertar o rechazar. */
    PENDING,
    /** El GM contraofertó; le toca al jugador: aceptar, volver a ofertar o cancelar. */
    COUNTERED,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isOpen() {
        return this == PENDING || this == COUNTERED;
    }
}
