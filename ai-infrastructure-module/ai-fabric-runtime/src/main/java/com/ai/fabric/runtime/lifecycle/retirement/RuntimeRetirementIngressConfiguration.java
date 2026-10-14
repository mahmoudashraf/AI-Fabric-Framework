package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class RuntimeRetirementIngressConfiguration implements WebMvcConfigurer {
    private final RuntimeTargetRetirementService service;
    public RuntimeRetirementIngressConfiguration(RuntimeTargetRetirementService service) { this.service = service; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DrainIngress(service)).addPathPatterns("/api/**");
    }

    public static final class DrainIngress implements HandlerInterceptor {
        private static final String PERMIT = DrainIngress.class.getName() + ".permit";
        private static final Set<String> CONTINUATIONS = Set.of(
            "/api/public/chat/session/renew",
            "/api/chat/me/query",
            "/api/chat/me/conversations/{conversationId}",
            "/api/chat/me/conversations/{conversationId}/messages",
            "/api/chat/me/conversations/{conversationId}/handover-requests",
            "/api/chat/me/conversations/{conversationId}/handover-requests/{handoverId}/cancel",
            "/api/chat/me/conversations/{conversationId}/handover-requests/{handoverId}/retry",
            "/api/chat/me/conversations/{conversationId}/ai-resume-requests",
            "/api/private/conversation-channel-bindings/{bindingId}/events",
            "/api/private/conversation-channel-dispatches/claim",
            "/api/private/conversation-channel-dispatches/{dispatchId}/outcomes/{attempt}",
            "/api/reviews/v1/tasks/{taskId}/decisions",
            "/api/smart-brain/v1/operations/{operationId}/cancel",
            "/api/agentic/v1/executions/{executionId}/cancel"
        );
        private final RuntimeTargetRetirementService service;
        public DrainIngress(RuntimeTargetRetirementService service) { this.service = service; }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
            Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = matched == null ? "unmapped-mutation" : matched.toString();
            if (route.startsWith("/api/private/runtime-target-retirement/")) return true;
            request.setAttribute(PERMIT, service.beginIngress(route, CONTINUATIONS.contains(route)));
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
            Object permit = request.getAttribute(PERMIT);
            if (permit instanceof TurnPermit turn) service.finishTurn(turn);
        }
    }
}
