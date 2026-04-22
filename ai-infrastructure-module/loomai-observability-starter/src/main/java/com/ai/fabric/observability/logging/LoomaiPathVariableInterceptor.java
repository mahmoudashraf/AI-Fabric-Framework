package com.ai.fabric.observability.logging;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

public class LoomaiPathVariableInterceptor implements HandlerInterceptor {

    private final List<String> pathVariableKeys;

    public LoomaiPathVariableInterceptor(List<String> pathVariableKeys) {
        this.pathVariableKeys = pathVariableKeys;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (routePattern instanceof String route && !route.isBlank()) {
            MDC.put(LoomaiMdcKeys.HTTP_ROUTE, route);
        }

        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> variables) {
            for (String key : pathVariableKeys) {
                Object value = variables.get(key);
                if (value instanceof String stringValue && !stringValue.isBlank()) {
                    MDC.put(key, stringValue);
                }
            }
        }
        return true;
    }
}
