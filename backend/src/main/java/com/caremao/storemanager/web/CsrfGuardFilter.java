package com.caremao.storemanager.web;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rechaza escrituras a la API que no salen de la propia app.
 *
 * <p>{@code SameSite=Lax} no alcanza: Foundry (mismo host en Funnel, otro puerto) y los demás
 * {@code *.srv.home.arpa} son "same-site" y la cookie viaja igual. Los navegadores actuales mandan
 * {@code Sec-Fetch-Site}, que distingue "same-origin" de "same-site"; para los que no, se compara
 * {@code Origin} con el host pedido. Sin ninguna de las dos cabeceras no es un navegador (curl, tests)
 * y no hay cookie ajena que aprovechar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!fromThisApp(request)) {
            Problems.write(response, HttpStatus.FORBIDDEN, "Pedido rechazado: no viene de esta app");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean fromThisApp(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null) {
            return fetchSite.equals("same-origin") || fetchSite.equals("none");
        }
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        String authority;
        try {
            authority = URI.create(origin).getRawAuthority();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (authority == null) {
            return false; // "null" u otro origen opaco
        }
        return authority.equalsIgnoreCase(request.getHeader("Host"))
                || authority.equalsIgnoreCase(request.getHeader("X-Forwarded-Host"));
    }
}
