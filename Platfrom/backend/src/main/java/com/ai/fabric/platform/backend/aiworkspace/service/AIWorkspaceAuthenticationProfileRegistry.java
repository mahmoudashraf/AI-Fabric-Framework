package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceAuthenticationProfileRegistry {

    public static final String VERSION = "1.0.0";
    public static final String NONE = "none";
    public static final String SUPABASE = "supabase";
    private static final Set<String> SUPPORTED_METHODS = Set.of("email-password", "magic-link");

    private final ObjectMapper objectMapper;

    public AIWorkspaceAuthenticationProfileRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationProfile resolve(String code,
                                         String version,
                                         AIWorkspaceInteractionPolicy policy,
                                         JsonNode configuration) {
        if (!VERSION.equals(version)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace authentication-profile version.");
        }
        String normalizedCode = code == null || code.isBlank() ? NONE : code.trim().toLowerCase(Locale.ROOT);
        ObjectNode safe = configuration == null || configuration.isNull()
            ? objectMapper.createObjectNode()
            : requireObject(configuration);
        AuthenticationProfile profile = switch (normalizedCode) {
            case NONE -> none(safe);
            case SUPABASE -> supabase(safe);
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported AI Workspace authentication profile.");
        };
        if (policy.requiresAuthenticationProfile() && NONE.equals(profile.code())) {
            throw new ResponseStatusException(BAD_REQUEST, "The selected interaction policy requires an authentication profile.");
        }
        if (policy == AIWorkspaceInteractionPolicy.PUBLIC && !NONE.equals(profile.code())) {
            throw new ResponseStatusException(BAD_REQUEST, "Public-only AI apps must use the none authentication profile.");
        }
        return profile;
    }

    public List<AIWorkspaceCatalogSummary.AuthenticationProfileSummary> summaries() {
        return List.of(
            new AIWorkspaceCatalogSummary.AuthenticationProfileSummary(
                NONE, VERSION, "No customer sign-in", "loomai-workspace-auth-none-v1", true),
            new AIWorkspaceCatalogSummary.AuthenticationProfileSummary(
                SUPABASE, VERSION, "Customer-owned Supabase Auth", "loomai-workspace-auth-supabase-v1", true)
        );
    }

    private AuthenticationProfile none(ObjectNode configuration) {
        if (!configuration.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "The none authentication profile does not accept configuration.");
        }
        return new AuthenticationProfile(NONE, VERSION, "loomai-workspace-auth-none-v1", configuration);
    }

    private AuthenticationProfile supabase(ObjectNode configuration) {
        rejectUnknown(configuration, Set.of("projectUrl", "publishableKey", "methods"));
        String projectUrl = requiredText(configuration, "projectUrl", 512);
        String publishableKey = requiredText(configuration, "publishableKey", 2_048);
        URI uri;
        try {
            uri = URI.create(projectUrl);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Supabase projectUrl is invalid.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
            throw new ResponseStatusException(BAD_REQUEST, "Supabase projectUrl must be an exact HTTPS origin.");
        }
        if (publishableKey.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(BAD_REQUEST, "Supabase publishableKey is invalid.");
        }
        JsonNode methodsNode = configuration.path("methods");
        if (!methodsNode.isArray() || methodsNode.isEmpty() || methodsNode.size() > SUPPORTED_METHODS.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Supabase methods must contain supported sign-in methods.");
        }
        LinkedHashSet<String> methods = new LinkedHashSet<>();
        methodsNode.forEach(method -> {
            if (!method.isTextual() || !SUPPORTED_METHODS.contains(method.asText())) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported Supabase sign-in method.");
            }
            methods.add(method.asText());
        });
        ObjectNode publicConfiguration = objectMapper.createObjectNode();
        publicConfiguration.put("projectUrl", normalizeOrigin(uri));
        publicConfiguration.put("publishableKey", publishableKey);
        ArrayNode publicMethods = publicConfiguration.putArray("methods");
        methods.forEach(publicMethods::add);
        return new AuthenticationProfile(SUPABASE, VERSION, "loomai-workspace-auth-supabase-v1", publicConfiguration);
    }

    private ObjectNode requireObject(JsonNode node) {
        if (!node.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Authentication configuration must be an object.");
        }
        return (ObjectNode) node.deepCopy();
    }

    private void rejectUnknown(ObjectNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown authentication configuration field: " + field);
            }
        });
    }

    private String requiredText(ObjectNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().trim().length() > maxLength) {
            throw new ResponseStatusException(BAD_REQUEST, field + " is required.");
        }
        return value.asText().trim();
    }

    private String normalizeOrigin(URI uri) {
        int port = uri.getPort();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
            + (port < 0 ? "" : ":" + port);
    }

    public record AuthenticationProfile(
        String code,
        String version,
        String configurationSchemaVersion,
        ObjectNode publicConfiguration
    ) {
    }
}
