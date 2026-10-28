package com.caremao.storemanager.config;

import java.io.IOException;
import java.util.List;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.caremao.storemanager.admin.Admin;
import com.caremao.storemanager.admin.AdminArgumentResolver;
import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.ActorArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    static {
        // Actor y Admin salen de la sesión, no de la petición: que no aparezcan en el OpenAPI.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class, Admin.class);
    }

    private final AdminArgumentResolver adminResolver;

    public WebConfig(AdminArgumentResolver adminResolver) {
        this.adminResolver = adminResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver());
        resolvers.add(adminResolver);
    }

    /** Sirve la SPA compilada; cualquier ruta que no sea un archivo ni la API cae en index.html. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
