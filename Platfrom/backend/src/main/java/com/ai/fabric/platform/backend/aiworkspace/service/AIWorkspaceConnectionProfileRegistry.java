package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceConnectionProfileRegistry {

    public static final String VERSION = "1.0.0";
    public static final String ANONYMOUS = "runtime-anonymous-direct";
    public static final String AUTHENTICATED = "runtime-authenticated-broker";
    public static final String SHOPIFY = "shopify-storefront-bridge";
    public static final String SUPPORT_CASE_HOST = "platform-support-case-host";

    private final PlatformAIWorkspaceProperties properties;
    private final Environment environment;

    public AIWorkspaceConnectionProfileRegistry(PlatformAIWorkspaceProperties properties) {
        this(properties, new StandardEnvironment());
    }

    @Autowired
    public AIWorkspaceConnectionProfileRegistry(PlatformAIWorkspaceProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public ConnectionProfile resolve(String code,
                                     String version,
                                     AIWorkspaceConnectionMode mode,
                                     JsonNode configuration) {
        if (!VERSION.equals(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace connection-profile version.");
        }
        validateConfiguration(code, configuration);
        ConnectionProfile profile = switch (code == null ? "" : code) {
            case ANONYMOUS -> new ConnectionProfile(
                ANONYMOUS, VERSION, "Direct anonymous runtime", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
                "direct-public-runtime", "loomai-runtime-anonymous-v1", true, null, null, Map.of()
            );
            case AUTHENTICATED -> new ConnectionProfile(
                AUTHENTICATED, VERSION, "Authenticated runtime broker", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED,
                "brokered-public-runtime", "loomai-runtime-token-broker-v1",
                true, null, null, Map.of()
            );
            case SHOPIFY -> shopifyProfile(configuration);
            case SUPPORT_CASE_HOST -> new ConnectionProfile(
                SUPPORT_CASE_HOST, VERSION, "Console support host", AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME,
                "host-controlled-private-runtime", "loomai-support-case-host-v1",
                environment.getProperty("platform.support.enabled", Boolean.class, false), null, "omit", Map.of()
            );
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace connection profile.");
        };
        if (profile.mode() != mode) {
            throw new ResponseStatusException(BAD_REQUEST, "Connection profile is incompatible with the selected mode.");
        }
        if (!profile.enabled()) {
            throw new ResponseStatusException(BAD_REQUEST, "Selected connection profile is not configured on this Platform.");
        }
        return profile;
    }

    public void requireCustomer(String code, String customerId) {
        if (SUPPORT_CASE_HOST.equals(code) && (customerId == null
            || !customerId.equals(environment.getProperty("platform.support.customer-id")))) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Support host requires its configured internal owner.");
        }
    }

    private void validateConfiguration(String code, JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Connection configuration must be an object.");
        }
        Set<String> allowed = SHOPIFY.equals(code) ? Set.of("shopDomain") : Set.of();
        configuration.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown connection configuration field: " + field);
            }
        });
    }

    public List<AIWorkspaceCatalogSummary.ConnectionProfileSummary> summaries() {
        return List.of(
            summary(ANONYMOUS, "Direct anonymous runtime", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
                "direct-public-runtime", "loomai-runtime-anonymous-v1", true, "Ready"),
            summary(AUTHENTICATED, "Authenticated runtime broker", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED,
                "brokered-public-runtime", "loomai-runtime-token-broker-v1", true,
                "Deployment-local identity broker"),
            summary(SHOPIFY, "Shopify storefront bridge", AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME,
                "private-backend-adapter", "loomai-shopify-bridge-adapter-v1", properties.shopifyBridgeBaseUrl() != null,
                properties.shopifyBridgeBaseUrl() == null ? "Shopify Bridge is not configured." : "Ready")
        );
    }

    private ConnectionProfile shopifyProfile(JsonNode configuration) {
        String shopDomain = configuration == null ? "" : configuration.path("shopDomain").asText("").trim().toLowerCase();
        if (!shopDomain.matches("[a-z0-9][a-z0-9-]*\\.myshopify\\.com")) {
            throw new ResponseStatusException(BAD_REQUEST, "A valid shopDomain is required for the Shopify Bridge profile.");
        }
        String baseUrl = properties.shopifyBridgeBaseUrl();
        String bootstrapUrl = baseUrl == null
            ? null
            : baseUrl + "/api/storefront/shops/" + shopDomain + "/workspace/bootstrap";
        return new ConnectionProfile(
            SHOPIFY, VERSION, "Shopify storefront bridge", AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME,
            "private-backend-adapter", "loomai-shopify-bridge-adapter-v1", baseUrl != null,
            bootstrapUrl, "omit", Map.of("shopDomain", shopDomain)
        );
    }

    private AIWorkspaceCatalogSummary.ConnectionProfileSummary summary(String code,
                                                                        String name,
                                                                        AIWorkspaceConnectionMode mode,
                                                                        String handler,
                                                                        String schema,
                                                                        boolean enabled,
                                                                        String message) {
        return new AIWorkspaceCatalogSummary.ConnectionProfileSummary(
            code, VERSION, name, mode.manifestValue(), handler, schema, enabled, message
        );
    }

    public record ConnectionProfile(
        String code,
        String version,
        String name,
        AIWorkspaceConnectionMode mode,
        String handler,
        String configurationSchemaVersion,
        boolean enabled,
        String endpoint,
        String browserCredentials,
        Map<String, Object> publicConfiguration
    ) {
    }
}
