package com.ai.behavior.storage.impl;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.behavior.providers.aggregated", name = "enabled", havingValue = "true")
public class AggregatedBehaviorProvider implements BehaviorDataProvider {

    private final List<BehaviorDataProvider> providers;
    private final BehaviorModuleProperties properties;

    @Override
    public List<BehaviorSignal> query(BehaviorQuery query) {
        var config = properties.getProviders().getAggregated();
        Map<String, BehaviorDataProvider> available = availableProviders();
        List<String> order = config.getProviderOrder() == null || config.getProviderOrder().isEmpty()
            ? new ArrayList<>(available.keySet())
            : config.getProviderOrder();

        List<BehaviorSignal> aggregated = new ArrayList<>();
        int maxProviders = Math.max(1, config.getMaxProviders());
        int providersUsed = 0;

        for (String providerType : order) {
            if (aggregated.size() >= query.getLimit()) {
                break;
            }
            var provider = available.get(providerType);
            if (provider == null) {
                continue;
            }
            try {
                aggregated.addAll(provider.query(query));
                providersUsed++;
            } catch (Exception ex) {
                log.warn("Aggregated provider failed to query source {}: {}", providerType, ex.getMessage());
            }
            if (providersUsed >= maxProviders) {
                break;
            }
        }

        Comparator<BehaviorSignal> comparator = Comparator.comparing(BehaviorSignal::getTimestamp,
            Comparator.nullsLast(LocalDateTime::compareTo));
        if (!query.isAscending()) {
            comparator = comparator.reversed();
        }

        return aggregated.stream()
            .filter(Objects::nonNull)
            .sorted(comparator)
            .limit(query.getLimit())
            .collect(Collectors.toList());
    }

    @Override
    public String getProviderType() {
        return "aggregated";
    }

    private Map<String, BehaviorDataProvider> availableProviders() {
        return providers.stream()
            .filter(provider -> provider != this)
            .collect(Collectors.toMap(
                BehaviorDataProvider::getProviderType,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
            ));
    }
}
