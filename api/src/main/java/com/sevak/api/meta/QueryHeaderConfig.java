package com.sevak.api.meta;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Adds the X-Sevak-Query header to every endpoint marked with {@link ShowsQuery}. */
@Configuration
public class QueryHeaderConfig implements WebMvcConfigurer {

    public static final String HEADER = "X-Sevak-Query";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method) {
                    ShowsQuery query = method.getMethodAnnotation(ShowsQuery.class);
                    if (query != null) {
                        response.setHeader(HEADER, query.value());
                    }
                }
                return true;
            }
        });
    }
}
