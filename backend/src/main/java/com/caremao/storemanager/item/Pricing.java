package com.caremao.storemanager.item;

import com.caremao.storemanager.common.ApiException;

public final class Pricing {

    private Pricing() {
    }

    /** Aplica un porcentaje a un precio en cp, redondeando al cobre más cercano. Null si no hay precio. */
    public static Long apply(Long valueCp, int pct) {
        if (valueCp == null) {
            return null;
        }
        return (multiply(valueCp, pct) + 50) / 100;
    }

    /** Precio × cantidad sin desbordar; con los topes de {@code Limits} nunca debería fallar. */
    public static long multiply(long unitCp, long quantity) {
        try {
            return Math.multiplyExact(unitCp, quantity);
        } catch (ArithmeticException e) {
            throw ApiException.badRequest("El precio es demasiado grande");
        }
    }

    /** 1250 → "12 po 5 pp" (abreviaturas del manual en español), para mensajes de error. */
    public static String format(long cp) {
        StringBuilder out = new StringBuilder();
        long gp = cp / 100;
        long sp = (cp % 100) / 10;
        long rest = cp % 10;
        if (gp > 0) {
            out.append(gp).append(" po");
        }
        if (sp > 0) {
            out.append(out.isEmpty() ? "" : " ").append(sp).append(" pp");
        }
        if (rest > 0 || out.isEmpty()) {
            out.append(out.isEmpty() ? "" : " ").append(rest).append(" pc");
        }
        return out.toString();
    }
}
