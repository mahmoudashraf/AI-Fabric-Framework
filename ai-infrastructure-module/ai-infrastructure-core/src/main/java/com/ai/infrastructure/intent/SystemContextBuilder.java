package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds the aggregated context object consumed by the prompt builder.
 */
@Service
public class SystemContextBuilder {

    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Clock clock;

    public SystemContextBuilder(AvailableActionsRegistry availableActionsRegistry,
                                KnowledgeBaseOverviewService knowledgeBaseOverviewService,
                                ObjectProvider<Clock> clockProvider) {
        this.availableActionsRegistry = availableActionsRegistry;
        this.knowledgeBaseOverviewService = knowledgeBaseOverviewService;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    public SystemContext buildContext(String userId) {
        List<ActionInfo> actions = availableActionsRegistry.getAllAvailableActions();
        KnowledgeBaseOverview overview = knowledgeBaseOverviewService.getOverview();

        return SystemContext.builder()
            .availableActions(actions)
            .knowledgeBaseOverview(overview)
            .userId(userId)
            .timestamp(LocalDateTime.now(clock))
            .build();
    }
}
