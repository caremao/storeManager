package com.caremao.storemanager.admin;

/** Quien tiene sesión en el portal admin. Un parámetro de este tipo exige esa sesión. */
public record Admin(String token) {
}
