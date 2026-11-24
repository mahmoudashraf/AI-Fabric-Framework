package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.specialist.SpecialistRegistry;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readback is based on registered native definitions, not just environment variables. */
@Component
public class WorkflowAssistanceStatus implements HealthIndicator {
    private final WorkflowAssistanceBundle bundle;
    private final ObjectProvider<SpecialistRegistry> registries;
    private final AuthoringPlatformClient platform;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private ObjectProvider<ai.fabric.execution.gateway.AIExecutionGateway> gateways;
    private ObjectProvider<ai.fabric.execution.gateway.DurableGoalExecutionHost> hosts;
    private ObjectProvider<com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes> budgets;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDurableSupport(ObjectProvider<ai.fabric.execution.gateway.AIExecutionGateway> gateways,
        ObjectProvider<ai.fabric.execution.gateway.DurableGoalExecutionHost> hosts,
        ObjectProvider<com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes> budgets) {
        this.gateways = gateways; this.hosts = hosts; this.budgets = budgets;
    }
    public WorkflowAssistanceStatus(WorkflowAssistanceBundle bundle, ObjectProvider<SpecialistRegistry> registries,
        AuthoringPlatformClient platform, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.bundle = bundle; this.registries = registries; this.platform = platform; this.mapper = mapper;
    }
    public Map<String, Object> status() {
        if (!bundle.enabled()) return Map.of("enabled", false, "ready", !platform.enabled());
        var registry = registries.getIfAvailable();
        boolean ready = registry != null && platform.enabled();
        boolean publishedGoal = false;
        for (var item : bundle.content().path("profiles")) {
            var definition = registry == null ? null : registry.find(ai.fabric.execution.specialist.SpecialistId.parse(item.path("specialistRef").asText())).orElse(null);
            ready &= definition != null && definition.executionProfile().workflowPolicy().enabled();
            publishedGoal |= definition != null && definition.executionProfile().workflowPolicy().goalMode();
        }
        boolean durable = gateways != null && gateways.getIfAvailable() instanceof ai.fabric.execution.gateway.DurableAIExecutionGateway
            && hosts.getIfAvailable() != null && budgets.getIfAvailable() != null;
        var result = new java.util.LinkedHashMap<String, Object>(Map.of("enabled", true, "ready", ready, "contractVersion", WorkflowAssistanceBundle.CONTRACT,
            "contentHash", bundle.contentHash(), "profiles", mapper.convertValue(bundle.content().path("profiles"), Object.class),
            "policy", mapper.convertValue(bundle.content().path("policy"), Object.class),
            "taskBindings", mapper.convertValue(bundle.content().path("taskBindings"), Object.class)));
        boolean autonomous = ready && publishedGoal && durable;
        result.put("autonomousEnabled", autonomous);
        result.put("autonomousUnavailableReason", autonomous ? "" : !publishedGoal ? "PUBLISHED_GOAL_POLICY_DISABLED" : "DURABLE_GOAL_HOST_UNAVAILABLE");
        result.put("executionModes", autonomous ? java.util.List.of("BOUNDED", "AUTONOMOUS") : java.util.List.of("BOUNDED"));
        result.put("goal", mapper.convertValue(bundle.content().path("goal"), Object.class));
        return java.util.Collections.unmodifiableMap(result);
    }
    @Override public Health health() {
        var state = status();
        return (Boolean.TRUE.equals(state.get("ready")) ? Health.up() : Health.down())
            .withDetails(state).build();
    }
}
