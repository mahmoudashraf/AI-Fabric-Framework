package com.subscription.hub.action;

import com.ai.infrastructure.intent.action.AIActionProvider;
import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.subscription.hub.action.handler.CancelSubscriptionActionHandler;
import com.subscription.hub.action.handler.SubscribeActionHandler;
import com.subscription.hub.action.handler.UpgradeSubscriptionActionHandler;
import com.subscription.hub.action.handler.DowngradeSubscriptionActionHandler;
import com.subscription.hub.action.handler.UpdateAddressActionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides available subscription actions to the AI Fabric Framework
 * This allows the framework to know what actions are available for intent extraction
 */
@Component
@RequiredArgsConstructor
public class SubscriptionActionProvider implements AIActionProvider {
    
    private final CancelSubscriptionActionHandler cancelHandler;
    private final SubscribeActionHandler subscribeHandler;
    private final UpgradeSubscriptionActionHandler upgradeHandler;
    private final DowngradeSubscriptionActionHandler downgradeHandler;
    private final UpdateAddressActionHandler updateAddressHandler;
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            cancelHandler,
            subscribeHandler,
            upgradeHandler,
            downgradeHandler,
            updateAddressHandler
        ).stream()
            .map(handler -> {
                var metadata = handler.getActionMetadata();
                return ActionInfo.builder()
                    .name(metadata.getName())
                    .description(metadata.getDescription())
                    .category(metadata.getCategory())
                    .parameters(metadata.getParameters())
                    .build();
            })
            .collect(Collectors.toList());
    }
}
