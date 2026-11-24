package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Explicit logout fences work; closing the assistant or navigating does not. */
@Configuration
public class AuthoringGoalLogoutConfiguration implements WebMvcConfigurer {
    private final AuthoringGoalService goals;
    private final CustomerSupabaseIdentityService identities;
    public AuthoringGoalLogoutConfiguration(AuthoringGoalService goals, CustomerSupabaseIdentityService identities) {
        this.goals = goals; this.identities = identities;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!"POST".equals(request.getMethod())) return true;
                var principal = PlatformSecurityContext.currentPrincipal();
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    try { principal = identities.authenticate(header.substring(7)); }
                    catch (org.springframework.web.server.ResponseStatusException invalid) { /* Native logout can still revoke its own cookie session. */ }
                }
                if (principal != null) goals.revokeActor(principal.actorId());
                return true;
            }
        }).addPathPatterns("/api/platform/auth/logout");
    }
}
