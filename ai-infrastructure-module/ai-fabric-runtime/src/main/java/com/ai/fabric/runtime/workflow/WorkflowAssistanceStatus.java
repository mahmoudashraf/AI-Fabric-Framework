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
    public WorkflowAssistanceStatus(WorkflowAssistanceBundle bundle, ObjectProvider<SpecialistRegistry> registries,
        AuthoringPlatformClient platform, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.bundle = bundle; this.registries = registries; this.platform = platform; this.mapper = mapper;
    }
    public Map<String, Object> status() {
        if (!bundle.enabled()) return Map.of("enabled", false, "ready", !platform.enabled());
        var registry = registries.getIfAvailable();
        boolean ready = registry != null && platform.enabled();
        for (var item : bundle.content().path("profiles")) {
            var definition = registry == null ? null : registry.find(ai.fabric.execution.specialist.SpecialistId.parse(item.path("specialistRef").asText())).orElse(null);
            ready &= definition != null && definition.executionProfile().workflowPolicy().enabled();
        }
        return Map.of("enabled", true, "ready", ready, "contractVersion", WorkflowAssistanceBundle.CONTRACT,
            "contentHash", bundle.contentHash(), "profiles", mapper.convertValue(bundle.content().path("profiles"), Object.class),
            "policy", mapper.convertValue(bundle.content().path("policy"), Object.class),
            "taskBindings", mapper.convertValue(bundle.content().path("taskBindings"), Object.class));
    }
    @Override public Health health() {
        var state = status();
        return (Boolean.TRUE.equals(state.get("ready")) ? Health.up() : Health.down())
            .withDetails(state).build();
    }
}
