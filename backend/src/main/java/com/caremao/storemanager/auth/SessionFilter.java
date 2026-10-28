package com.caremao.storemanager.auth;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Resuelve la cookie de sesión a un {@link Actor}; los controladores deciden qué exige cada ruta. */
@Component
public class SessionFilter extends OncePerRequestFilter {

    public static final String COOKIE = "sm_session";

    private final AppSessionRepository sessions;

    public SessionFilter(AppSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            Instant now = Instant.now();
            sessions.findByTokenHash(Tokens.sha256(cookie.getValue()))
                    .filter(s -> !s.isExpired(now))
                    .ifPresent(s -> request.setAttribute(Actor.REQUEST_ATTRIBUTE, s.toActor()));
        }
        chain.doFilter(request, response);
    }
}
