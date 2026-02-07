package com.ai.infrastructure.intent.action;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Exposes {@link com.ai.infrastructure.intent.action.annotation.AIAction}-declared actions as prompt-visible actions for intent extraction.
 */
@Service
@RequiredArgsConstructor
public class RegisteredActionsAIActionProvider implements AIActionProvider {

    private final AIActionRegistry actionRegistry;

    @Override
    public List<ActionInfo> getAvailableActions() {
        if (actionRegistry == null) {
            return List.of();
        }
        return actionRegistry.getAllMetadata().stream()
            .filter(Objects::nonNull)
            .map(meta -> ActionInfo.builder()
                .name(meta.getName())
                .description(meta.getDescription())
                .category(meta.getCategory())
                .parameters(meta.getParameters())
                .parameterSchemas(meta.getParameterSchemas())
                .build())
            .filter(ActionInfo::hasValidName)
            .toList();
    }

    @Override
    public String getProviderName() {
        return "registered-actions";
    }
}
