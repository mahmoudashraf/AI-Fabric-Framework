package com.loomai.demo.dealership.runtime;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
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
