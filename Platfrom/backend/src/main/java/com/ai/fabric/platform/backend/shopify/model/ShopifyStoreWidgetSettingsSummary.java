package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;
import java.util.Map;

public record ShopifyStoreWidgetSettingsSummary(
    String launcherLabel,
    String welcomeMessage,
    String shellModeProfile,
    boolean debugEnabled,
    List<String> enabledSurfaces,
    String defaultConversationMode,
    List<String> allowedConversationModes,
    Map<String, String> pageModeMappings,
    Boolean assistantDockEnabled,
    Boolean askAssistantLauncherEnabled
) {
    public ShopifyStoreWidgetSettingsSummary(String launcherLabel,
                                             String welcomeMessage,
                                             String shellModeProfile,
                                             boolean debugEnabled,
                                             List<String> enabledSurfaces,
                                             String defaultConversationMode,
                                             List<String> allowedConversationModes,
                                             Map<String, String> pageModeMappings) {
        this(
            launcherLabel,
            welcomeMessage,
            shellModeProfile,
            debugEnabled,
            enabledSurfaces,
            defaultConversationMode,
            allowedConversationModes,
            pageModeMappings,
            Boolean.TRUE,
            Boolean.FALSE
        );
    }
}
