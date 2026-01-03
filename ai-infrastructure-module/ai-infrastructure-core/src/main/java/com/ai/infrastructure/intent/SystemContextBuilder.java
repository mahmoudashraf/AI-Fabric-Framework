package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.spi.BehaviorContextProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Builds the aggregated context object consumed by the prompt builder.
 */
@Service
public class SystemContextBuilder {

    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Optional<BehaviorContextProvider> behaviorContextProvider;
    private final Clock clock;

    @Autowired
    public SystemContextBuilder(AvailableActionsRegistry availableActionsRegistry,
                                KnowledgeBaseOverviewService knowledgeBaseOverviewService,
                                ObjectProvider<BehaviorContextProvider> behaviorContextProvider,
                                ObjectProvider<Clock> clockProvider) {
        this.availableActionsRegistry = availableActionsRegistry;
        this.knowledgeBaseOverviewService = knowledgeBaseOverviewService;
        this.behaviorContextProvider = Optional.ofNullable(behaviorContextProvider.getIfAvailable());
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    // Convenience constructor for tests / legacy instantiation
    public SystemContextBuilder(AvailableActionsRegistry availableActionsRegistry,
                                KnowledgeBaseOverviewService knowledgeBaseOverviewService,
                                ObjectProvider<Clock> clockProvider) {
        this(availableActionsRegistry, knowledgeBaseOverviewService,
            new ObjectProvider<BehaviorContextProvider>() {
                @Override public BehaviorContextProvider getObject(Object... args) { return null; }
                @Override public BehaviorContextProvider getObject() { return null; }
                @Override public BehaviorContextProvider getIfAvailable() { return null; }
                @Override public BehaviorContextProvider getIfUnique() { return null; }
            },
            clockProvider
        );
    }

    public SystemContext buildContext(OrchestrationContext orchestrationContext) {
        List<ActionInfo> actions = availableActionsRegistry.getAllAvailableActions();
        KnowledgeBaseOverview overview = knowledgeBaseOverviewService.getOverview();

        SystemContext.SystemContextBuilder builder = SystemContext.builder()
            .availableActions(actions)
            .knowledgeBaseOverview(overview)
            .userId(orchestrationContext.getUserId())
            .sessionId(orchestrationContext.getSessionId())
            .authenticated(orchestrationContext.isAuthenticated())
            .locale(orchestrationContext.getLocale())
            .metadata(orchestrationContext.getMetadata())
            .timestamp(LocalDateTime.now(clock));

        behaviorContextProvider.ifPresent(provider ->
            provider.getBehaviorContext(orchestrationContext)
                .ifPresent(builder::behaviorContext)
        );

        return builder.build();
    }

    @Deprecated(forRemoval = true)
    public SystemContext buildContext(String userId) {
        return buildContext(OrchestrationContext.forUser(userId));
    }
}
