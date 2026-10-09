package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceConnectionProfileRegistry {

    public static final String VERSION = "1.0.0";
    public static final String ANONYMOUS = "runtime-anonymous-direct";
    public static final String AUTHENTICATED = "runtime-authenticated-broker";
    public static final String SHOPIFY = "shopify-storefront-bridge";

    private final PlatformAIWorkspaceProperties properties;

    public AIWorkspaceConnectionProfileRegistry(PlatformAIWorkspaceProperties properties) {
        this.properties = properties;
    }

    public ConnectionProfile resolve(String code,
                                     String version,
                                     AIWorkspaceConnectionMode mode,
                                     JsonNode configuration) {
        if (!VERSION.equals(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace connection-profile version.");
        }
        ConnectionProfile profile = switch (code == null ? "" : code) {
            case ANONYMOUS -> new ConnectionProfile(
                ANONYMOUS, VERSION, "Direct anonymous runtime", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
                "direct-public-runtime", "loomai-runtime-anonymous-v1", true, null, Map.of()
            );
            case AUTHENTICATED -> new ConnectionProfile(
                AUTHENTICATED, VERSION, "Authenticated runtime broker", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED,
                "brokered-public-runtime", "loomai-runtime-token-broker-v1",
                properties.authenticatedBrokerUrl() != null,
                properties.authenticatedBrokerUrl(), Map.of()
            );
            case SHOPIFY -> shopifyProfile(configuration);
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

    public List<AIWorkspaceCatalogSummary.ConnectionProfileSummary> summaries() {
        return List.of(
            summary(ANONYMOUS, "Direct anonymous runtime", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
                "direct-public-runtime", "loomai-runtime-anonymous-v1", true, "Ready"),
            summary(AUTHENTICATED, "Authenticated runtime broker", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED,
                "brokered-public-runtime", "loomai-runtime-token-broker-v1", properties.authenticatedBrokerUrl() != null,
                properties.authenticatedBrokerUrl() == null ? "No reviewed broker endpoint is configured." : "Ready"),
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
            bootstrapUrl, Map.of("shopDomain", shopDomain)
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
        Map<String, Object> publicConfiguration
    ) {
    }
}
