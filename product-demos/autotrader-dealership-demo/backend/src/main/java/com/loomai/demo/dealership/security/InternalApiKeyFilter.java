package com.loomai.demo.dealership.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final ReceiverBindingService bindings;

    public InternalApiKeyFilter(ReceiverBindingService bindings) {
        this.bindings = bindings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            bindings.authenticate(request.getHeader(ReceiverBindingService.API_KEY_HEADER));
        } catch (ResponseStatusException denied) {
            boolean unauthorized = denied.getStatusCode().value() == HttpServletResponse.SC_UNAUTHORIZED;
            response.setStatus(denied.getStatusCode().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(unauthorized
                ? "{\"success\":false,\"errorCode\":\"INTERNAL_AUTH_REQUIRED\",\"message\":\"Internal authentication is required.\"}"
                : "{\"success\":false,\"errorCode\":\"RECEIVER_BINDING_UNAVAILABLE\",\"message\":\"The receiver binding is unavailable.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
