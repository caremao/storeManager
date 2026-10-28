package com.caremao.storemanager.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.caremao.storemanager.common.ApiException;

/** Un parámetro {@code Actor} en un controlador exige sesión: sin ella responde 401. */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object actor = webRequest.getAttribute(Actor.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (actor == null) {
            throw ApiException.unauthorized();
        }
        return actor;
    }
}
