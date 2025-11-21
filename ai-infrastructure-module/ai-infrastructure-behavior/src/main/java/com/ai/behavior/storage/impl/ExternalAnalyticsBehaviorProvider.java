package com.ai.behavior.storage.impl;

import com.ai.behavior.adapter.ExternalAnalyticsAdapter;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.behavior.providers.external", name = "enabled", havingValue = "true")
public class ExternalAnalyticsBehaviorProvider implements BehaviorDataProvider {

    private final ExternalAnalyticsAdapter adapter;

    @Override
    public List<BehaviorSignal> query(BehaviorQuery query) {
        return adapter.fetchEvents(query);
    }

    @Override
    public String getProviderType() {
        return adapter.getProviderName();
    }
}
