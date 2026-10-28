package com.caremao.storemanager.admin;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.WebUtils;

import com.caremao.storemanager.common.ApiException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** Un parámetro {@code Admin} exige la cookie del portal: 404 si el portal está apagado, 401 sin sesión. */
@Component
public class AdminArgumentResolver implements HandlerMethodArgumentResolver {

    private final AdminService admin;
    private final AdminSessions sessions;

    AdminArgumentResolver(AdminService admin, AdminSessions sessions) {
        this.admin = admin;
        this.sessions = sessions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Admin.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        admin.requireEnabled();
        Cookie cookie = WebUtils.getCookie(webRequest.getNativeRequest(HttpServletRequest.class), AdminService.COOKIE);
        if (cookie == null || cookie.getValue().isBlank() || !sessions.isValid(cookie.getValue())) {
            throw ApiException.unauthorized();
        }
        return new Admin(cookie.getValue());
    }
}
