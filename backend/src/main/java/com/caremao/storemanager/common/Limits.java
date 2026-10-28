package com.caremao.storemanager.common;

/**
 * Topes de las entradas numéricas. Son generosos para una mesa, pero dejan todo cálculo
 * (precio × cantidad × porcentaje, sumas de bolsa) muy lejos de desbordar int o long.
 */
public final class Limits {

    /** Unidades de un item o de una operación. */
    public static final int MAX_QUANTITY = 1_000_000;
    /** 100 millones de po, en cobre. */
    public static final long MAX_VALUE_CP = 10_000_000_000L;
    /** Monedas de una denominación por operación (sumar, gastar, convertir, bolsa inicial). */
    public static final int MAX_COINS = 10_000_000;
    public static final double MAX_WEIGHT = 1_000_000;
    /** Tamaño del JSON de propiedades de un item, y cuánto puede anidarse. */
    public static final int MAX_PROPERTIES_CHARS = 10_000;
    public static final int MAX_PROPERTIES_DEPTH = 4;

    private Limits() {
    }
}
