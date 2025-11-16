package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class BehaviorQueryService {

    private final List<BehaviorDataProvider> providers;
    private final Map<String, BehaviorDataProvider> providerIndex = new ConcurrentHashMap<>();

    public List<BehaviorEvent> query(BehaviorQuery query) {
        return resolveProvider(null).query(query);
    }

    public List<BehaviorEvent> query(String providerType, BehaviorQuery query) {
        return resolveProvider(providerType).query(query);
    }

    public List<BehaviorEvent> getRecentEvents(String providerType, java.util.UUID userId, int limit) {
        return resolveProvider(providerType).getRecentEvents(userId, limit);
    }

    private BehaviorDataProvider resolveProvider(String type) {
        if (type == null || type.isBlank()) {
            return providers.stream()
                .min(Comparator.comparing(BehaviorDataProvider::getProviderType))
                .orElseThrow(() -> new IllegalStateException("No BehaviorDataProvider registered"));
        }
        return providerIndex.computeIfAbsent(type, key -> providers.stream()
            .filter(provider -> provider.getProviderType().equalsIgnoreCase(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown provider type: " + key)));
    }
}
