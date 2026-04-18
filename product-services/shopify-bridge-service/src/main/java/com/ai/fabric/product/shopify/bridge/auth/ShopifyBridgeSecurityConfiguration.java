package com.ai.fabric.product.shopify.bridge.auth;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class ShopifyBridgeSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ShopifyBridgeProperties properties) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(new AdminApiKeyFilter(properties), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static final class AdminApiKeyFilter extends OncePerRequestFilter {

        private final ShopifyBridgeProperties properties;

        private AdminApiKeyFilter(ShopifyBridgeProperties properties) {
            this.properties = properties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (!request.getRequestURI().startsWith("/api/admin/")) {
                filterChain.doFilter(request, response);
                return;
            }

            if (properties.adminApiKey().isBlank()) {
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Bridge admin API key is not configured.");
                return;
            }

            String presented = request.getHeader(properties.adminApiKeyHeader());
            if (presented == null || !properties.adminApiKey().equals(presented)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bridge admin API key.");
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "shopify-bridge-admin",
                "N/A",
                AuthorityUtils.createAuthorityList("ROLE_BRIDGE_ADMIN")
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
