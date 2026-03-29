package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.StringUtils;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class PlatformSecurityConfiguration {

    private final PlatformAuthProperties properties;
    private final ObjectMapper objectMapper;

    public PlatformSecurityConfiguration(PlatformAuthProperties properties,
                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain platformSecurityFilterChain(HttpSecurity http) throws Exception {
        if (properties.enabled()
            && !StringUtils.hasText(properties.adminApiKey())
            && !StringUtils.hasText(properties.operatorApiKey())) {
            throw new IllegalStateException("Platform auth is enabled but no platform API keys are configured.");
        }

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterBefore(new PlatformApiKeyAuthenticationFilter(properties), AnonymousAuthenticationFilter.class);
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers("/actuator/health", "/api/platform/auth/session").permitAll();
            authorize.requestMatchers(
                "/api/deployments/*/versions/*/artifacts/ai-actions.yml",
                "/api/deployments/*/versions/*/artifacts/ai-entity-config.yml",
                "/api/deployments/*/versions/*/artifacts/actions-routing.yml",
                "/api/deployments/*/versions/*/artifacts/deployment-manifest.json"
            ).permitAll();
            if (properties.enabled()) {
                authorize.anyRequest().authenticated();
            } else {
                authorize.anyRequest().permitAll();
            }
        });
        http.exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, authException) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized.", "UNAUTHORIZED"))
            .accessDeniedHandler((request, response, accessDeniedException) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden.", "FORBIDDEN"))
        );
        return http.build();
    }

    private void writeError(HttpServletResponse response,
                            int status,
                            String message,
                            String errorCode) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
            "success", false,
            "message", message,
            "errorCode", errorCode
        ));
    }
}
