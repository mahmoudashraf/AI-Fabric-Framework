package com.ai.infrastructure.intent.action;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Exposes {@link ActionHandler} beans as prompt-visible actions for intent extraction.
 *
 * <p>This prevents misleading prompts like "No actions are currently registered" when
 * action handlers are present in the Spring context.</p>
 */
@Service
@RequiredArgsConstructor
public class ActionHandlersAIActionProvider implements AIActionProvider {

    private final List<ActionHandler> actionHandlers;

    @Override
    public List<ActionInfo> getAvailableActions() {
        if (actionHandlers == null || actionHandlers.isEmpty()) {
            return List.of();
        }
        return actionHandlers.stream()
            .filter(Objects::nonNull)
            .map(ActionHandler::getActionMetadata)
            .filter(Objects::nonNull)
            .map(meta -> ActionInfo.builder()
                .name(meta.getName())
                .description(meta.getDescription())
                .category(meta.getCategory())
                .parameters(meta.getParameters())
                .build())
            .filter(ActionInfo::hasValidName)
            .toList();
    }

    @Override
    public String getProviderName() {
        return "action-handlers";
    }
}

