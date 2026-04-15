package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformPublicApiProperties;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class PlatformSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformSecurityConfiguration.class);

    private final PlatformAuthProperties properties;
    private final PlatformPublicApiProperties publicApiProperties;
    private final ObjectMapper objectMapper;
    private final PlatformIdentityService platformIdentityService;
    private final PlatformSecretService platformSecretService;

    public PlatformSecurityConfiguration(PlatformAuthProperties properties,
                                         PlatformPublicApiProperties publicApiProperties,
                                         ObjectMapper objectMapper,
                                         PlatformIdentityService platformIdentityService,
                                         PlatformSecretService platformSecretService) {
        this.properties = properties;
        this.publicApiProperties = publicApiProperties;
        this.objectMapper = objectMapper;
        this.platformIdentityService = platformIdentityService;
        this.platformSecretService = platformSecretService;
    }

    @PostConstruct
    void warnIfAuthDisabled() {
        if (!properties.enabled()) {
            log.warn(
                "Platform auth is disabled. Protected platform endpoints will reject requests unless another "
                    + "authenticated mechanism such as the public provisioning API is configured."
            );
        }
    }

    @Bean
    public SecurityFilterChain platformSecurityFilterChain(HttpSecurity http) throws Exception {
        if (properties.enabled() && !apiKeyAuthAvailable() && !properties.sessionEnabled()) {
            throw new IllegalStateException("Platform auth is enabled but no authentication method is configured.");
        }

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.cors(Customizer.withDefaults());
        http.addFilterBefore(
            new PlatformSessionAuthenticationFilter(properties, platformIdentityService),
            AnonymousAuthenticationFilter.class
        );
        http.addFilterBefore(
            new PlatformPublicApiAuthenticationFilter(publicApiProperties),
            AnonymousAuthenticationFilter.class
        );
        http.addFilterBefore(
            new PlatformApiKeyAuthenticationFilter(properties, platformSecretService),
            AnonymousAuthenticationFilter.class
        );
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(
                "/actuator/health",
                "/api/platform/auth/session",
                "/api/platform/auth/login",
                "/api/platform/auth/logout",
                "/api/vectorization/runner/**"
            ).permitAll();
            authorize.requestMatchers(
                "/api/deployments/*/versions/*/artifacts/ai-actions.yml",
                "/api/deployments/*/versions/*/artifacts/ai-entity-config.yml",
                "/api/deployments/*/versions/*/artifacts/actions-routing.yml",
                "/api/deployments/*/versions/*/artifacts/ai-prompt-config.json",
                "/api/deployments/*/versions/*/artifacts/ai-marketplace-dataset-config.json",
                "/api/deployments/*/versions/*/artifacts/ai-knowledge-source-config.json",
                "/api/deployments/*/versions/*/artifacts/ai-shell-config.json",
                "/api/deployments/*/versions/*/artifacts/deployment-manifest.json"
            ).permitAll();
            authorize.anyRequest().authenticated();
        });
        http.exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, authException) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized.", "UNAUTHORIZED"))
            .accessDeniedHandler((request, response, accessDeniedException) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden.", "FORBIDDEN"))
        );
        return http.build();
    }

    private boolean apiKeyAuthAvailable() {
        return properties.apiKeyEnabled()
            && (
                hasText(properties.operatorApiKey())
                || hasText(properties.adminApiKey())
                || hasText(platformSecretService.resolveSecret("PLATFORM_OPERATOR_API_KEY"))
                || hasText(platformSecretService.resolveSecret("PLATFORM_ADMIN_API_KEY"))
            );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
