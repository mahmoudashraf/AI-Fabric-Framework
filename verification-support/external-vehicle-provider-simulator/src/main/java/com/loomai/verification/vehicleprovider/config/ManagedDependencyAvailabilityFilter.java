package com.loomai.verification.vehicleprovider.config;

import com.loomai.verification.vehicleprovider.service.ManagedDependencyLifecycleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ManagedDependencyAvailabilityFilter extends OncePerRequestFilter {
    private final ManagedDependencyLifecycleService lifecycle;
    public ManagedDependencyAvailabilityFilter(ManagedDependencyLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/internal/loomai-managed-dependency/") || path.startsWith("/actuator/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!lifecycle.available()) { response.sendError(503, "Managed dependency is disabled."); return; }
        chain.doFilter(request, response);
    }
}
