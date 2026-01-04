package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.spi.BehaviorContextProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the aggregated context object consumed by the prompt builder.
 */
@Service
public class SystemContextBuilder {

    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Optional<BehaviorContextProvider> behaviorContextProvider;
    private final Clock clock;
    private final BeanFactory beanFactory;

    @Autowired
    public SystemContextBuilder(AvailableActionsRegistry availableActionsRegistry,
                                KnowledgeBaseOverviewService knowledgeBaseOverviewService,
                                ObjectProvider<BehaviorContextProvider> behaviorContextProvider,
                                ObjectProvider<Clock> clockProvider,
                                BeanFactory beanFactory) {
        this.availableActionsRegistry = availableActionsRegistry;
        this.knowledgeBaseOverviewService = knowledgeBaseOverviewService;
        this.behaviorContextProvider = Optional.ofNullable(behaviorContextProvider.getIfAvailable());
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
        this.beanFactory = beanFactory;
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
            clockProvider,
            null  // BeanFactory not available in test constructor
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
            .timestamp(LocalDateTime.now(clock))
            .availableEntityTypes(getAvailableEntityTypes());

        behaviorContextProvider.ifPresent(provider ->
            provider.getBehaviorContext(orchestrationContext)
                .ifPresent(builder::behaviorContext)
        );

        return builder.build();
    }

    /**
     * Extracts available entity types from EntityRelationshipMapper.
     * This is mandatory when the relationship-query module is present.
     * Uses reflection to avoid hard dependency on relationship-query module.
     * 
     * @return Set of registered entity types, or empty set if relationship-query module is not present
     */
    private Set<String> getAvailableEntityTypes() {
        if (beanFactory == null) {
            return Set.of();
        }

        try {
            // Try to get EntityRelationshipMapper bean by class name (relationship-query module)
            Class<?> mapperClass = Class.forName("com.ai.infrastructure.relationship.service.EntityRelationshipMapper");
            Object mapper = beanFactory.getBean(mapperClass);
            
            // Use reflection to call getAllEntityMappings() method
            java.lang.reflect.Method method = mapperClass.getMethod("getAllEntityMappings");
            @SuppressWarnings("unchecked")
            Map<String, ?> mappings = (Map<String, ?>) method.invoke(mapper);
            
            // Must return entity types if mapper exists - empty list if no entities registered
            return mappings != null ? mappings.keySet() : Set.of();
        } catch (ClassNotFoundException ex) {
            // EntityRelationshipMapper class not found - relationship-query module not present
            return Set.of();
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ex) {
            // Bean not registered - relationship-query module not configured
            return Set.of();
        } catch (Exception ex) {
            // If reflection fails or method doesn't exist, return empty set
            return Set.of();
        }
    }

    @Deprecated(forRemoval = true)
    public SystemContext buildContext(String userId) {
        return buildContext(OrchestrationContext.forUser(userId));
    }
}
