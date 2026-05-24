package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreWidgetSettingsService {

    private static final int MAX_LAUNCHER_LABEL_LENGTH = 60;
    private static final int MAX_WELCOME_MESSAGE_LENGTH = 320;
    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final boolean DEFAULT_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_ASSISTANT_DOCK_ENABLED = true;
    private static final boolean DEFAULT_ASK_ASSISTANT_LAUNCHER_ENABLED = false;
    private static final String DEFAULT_COLOR_SCHEME = "graphite";
    private static final String DEFAULT_CONVERSATION_MODE = "thinker_deep";
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison",
        "order-lookup"
    );
    private static final Set<String> ALLOWED_SHELL_MODE_PROFILES = Set.of(
        "SHOPIFY_COMPANION",
        "GUIDED_COMMERCE",
        "GUIDED_SUPPORT"
    );
    private static final Set<String> ALLOWED_COLOR_SCHEMES = Set.of(
        "graphite",
        "violet",
        "blue",
        "emerald",
        "rose"
    );
    private static final Set<String> ALLOWED_SURFACES = Set.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison",
        "order-lookup"
    );
    private static final Set<String> ALLOWED_CONVERSATION_MODES = Set.of(
        "navigator",
        "navigator_deep",
        "thinker_deep",
        "cart_assistant",
        "executor"
    );
    private static final Set<String> ALLOWED_PAGE_MODE_KEYS = Set.of(
        "landing",
        "product",
        "collection",
        "search",
        "cart",
        "account",
        "support",
        "content"
    );

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService connectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService auditService;

    public ShopifyStoreWidgetSettingsService(ShopifyStoreConnectionRepository repository,
                                             ShopifyStoreConnectionService connectionService,
                                             ShopifyStoreSourcePreflightSupport support,
                                             PlatformAuditService auditService) {
        this.repository = repository;
        this.connectionService = connectionService;
        this.support = support;
        this.auditService = auditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary update(String shopDomain, UpdateShopifyStoreWidgetSettingsRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null) {
            throw new ResponseStatusException(CONFLICT, "widget settings payload is required.");
        }

        String launcherLabel = normalizeLauncherLabel(request.launcherLabel());
        String welcomeMessage = normalizeWelcomeMessage(request.welcomeMessage());
        String shellModeProfile = normalizeShellModeProfile(request.shellModeProfile());
        boolean debugEnabled = request.debugEnabled() != null ? request.debugEnabled() : DEFAULT_DEBUG_ENABLED;
        boolean assistantDockEnabled = request.assistantDockEnabled() != null
            ? request.assistantDockEnabled()
            : DEFAULT_ASSISTANT_DOCK_ENABLED;
        boolean askAssistantLauncherEnabled = request.askAssistantLauncherEnabled() != null
            ? request.askAssistantLauncherEnabled()
            : DEFAULT_ASK_ASSISTANT_LAUNCHER_ENABLED;
        String colorScheme = normalizeColorScheme(request.colorScheme());
        List<String> enabledSurfaces = normalizeEnabledSurfaces(request.enabledSurfaces());
        String defaultConversationMode = normalizeDefaultConversationMode(request.defaultConversationMode(), shellModeProfile);
        List<String> allowedConversationModes = normalizeAllowedConversationModes(
            request.allowedConversationModes(),
            defaultConversationMode
        );
        Map<String, String> pageModeMappings = normalizePageModeMappings(
            request.pageModeMappings(),
            allowedConversationModes
        );
        Instant now = Instant.now();

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode widget = details.with("widget");
        ObjectNode settings = widget.putObject("settings");
        settings.put("launcherLabel", launcherLabel);
        settings.put("welcomeMessage", welcomeMessage);
        settings.put("shellModeProfile", shellModeProfile);
        settings.put("debugEnabled", debugEnabled);
        settings.put("assistantDockEnabled", assistantDockEnabled);
        settings.put("askAssistantLauncherEnabled", askAssistantLauncherEnabled);
        settings.put("colorScheme", colorScheme);
        ArrayNode enabledSurfacesNode = settings.putArray("enabledSurfaces");
        enabledSurfaces.forEach(enabledSurfacesNode::add);
        settings.put("defaultConversationMode", defaultConversationMode);
        ArrayNode allowedModesNode = settings.putArray("allowedConversationModes");
        allowedConversationModes.forEach(allowedModesNode::add);
        ObjectNode pageModeMappingsNode = settings.putObject("pageModeMappings");
        pageModeMappings.forEach(pageModeMappingsNode::put);
        settings.put("updatedAt", now.toString());
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        auditService.record(
            "SHOPIFY_STORE_WIDGET_SETTINGS_UPDATED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.ofEntries(
                Map.entry("shopDomain", store.getShopDomain()),
                Map.entry("launcherLabel", launcherLabel),
                Map.entry("welcomeMessageLength", Integer.toString(welcomeMessage.length())),
                Map.entry("shellModeProfile", shellModeProfile),
                Map.entry("debugEnabled", Boolean.toString(debugEnabled)),
                Map.entry("assistantDockEnabled", Boolean.toString(assistantDockEnabled)),
                Map.entry("askAssistantLauncherEnabled", Boolean.toString(askAssistantLauncherEnabled)),
                Map.entry("colorScheme", colorScheme),
                Map.entry("enabledSurfaces", String.join(",", enabledSurfaces)),
                Map.entry("defaultConversationMode", defaultConversationMode),
                Map.entry("allowedConversationModes", String.join(",", allowedConversationModes)),
                Map.entry("pageModeMappings", pageModeMappings.toString())
            )
        );

        return connectionService.getConnection(store.getShopDomain());
    }

    private String normalizeShopDomain(String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLauncherLabel(String value) {
        String normalized = hasText(value) ? value.trim() : DEFAULT_LAUNCHER_LABEL;
        if (normalized.length() > MAX_LAUNCHER_LABEL_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "launcherLabel exceeds " + MAX_LAUNCHER_LABEL_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizeWelcomeMessage(String value) {
        String normalized = hasText(value) ? value.trim() : DEFAULT_WELCOME_MESSAGE;
        if (normalized.length() > MAX_WELCOME_MESSAGE_LENGTH) {
            throw new ResponseStatusException(CONFLICT, "welcomeMessage exceeds " + MAX_WELCOME_MESSAGE_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizeShellModeProfile(String value) {
        String normalized = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : DEFAULT_SHELL_MODE_PROFILE;
        if (!ALLOWED_SHELL_MODE_PROFILES.contains(normalized)) {
            throw new ResponseStatusException(CONFLICT, "shellModeProfile must be one of " + ALLOWED_SHELL_MODE_PROFILES + ".");
        }
        return normalized;
    }

    private String normalizeColorScheme(String value) {
        String normalized = hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : DEFAULT_COLOR_SCHEME;
        if (!ALLOWED_COLOR_SCHEMES.contains(normalized)) {
            throw new ResponseStatusException(CONFLICT, "colorScheme must be one of " + ALLOWED_COLOR_SCHEMES + ".");
        }
        return normalized;
    }

    private List<String> normalizeEnabledSurfaces(List<String> values) {
        if (values == null) {
            return DEFAULT_ENABLED_SURFACES;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SURFACES.contains(candidate)) {
                throw new ResponseStatusException(CONFLICT, "enabledSurfaces contains unsupported value: " + value);
            }
            normalized.add(candidate);
        }
        return List.copyOf(normalized);
    }

    private String normalizeDefaultConversationMode(String value, String shellModeProfile) {
        String fallback = defaultConversationModeForShellProfile(shellModeProfile);
        String normalized = hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : fallback;
        if (!ALLOWED_CONVERSATION_MODES.contains(normalized)) {
            throw new ResponseStatusException(
                CONFLICT,
                "defaultConversationMode must be one of " + ALLOWED_CONVERSATION_MODES + "."
            );
        }
        return normalized;
    }

    private List<String> normalizeAllowedConversationModes(List<String> values, String defaultConversationMode) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!hasText(value)) {
                    continue;
                }
                String candidate = value.trim().toLowerCase(Locale.ROOT);
                if (!ALLOWED_CONVERSATION_MODES.contains(candidate)) {
                    throw new ResponseStatusException(
                        CONFLICT,
                        "allowedConversationModes contains unsupported value: " + value
                    );
                }
                normalized.add(candidate);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(defaultConversationMode);
        } else {
            normalized.add(defaultConversationMode);
        }
        return List.copyOf(normalized);
    }

    private Map<String, String> normalizePageModeMappings(Map<String, String> values, List<String> allowedConversationModes) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!hasText(entry.getKey()) || !hasText(entry.getValue())) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String mode = entry.getValue().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_PAGE_MODE_KEYS.contains(key)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "pageModeMappings contains unsupported page key: " + entry.getKey()
                );
            }
            if (!ALLOWED_CONVERSATION_MODES.contains(mode)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "pageModeMappings contains unsupported mode: " + entry.getValue()
                );
            }
            if (!allowedConversationModes.contains(mode)) {
                throw new ResponseStatusException(
                    CONFLICT,
                    "pageModeMappings must only target modes from allowedConversationModes."
                );
            }
            normalized.put(key, mode);
        }
        return Map.copyOf(normalized);
    }

    private String defaultConversationModeForShellProfile(String shellModeProfile) {
        return DEFAULT_CONVERSATION_MODE;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
