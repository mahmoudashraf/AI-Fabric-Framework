package com.ai.fabric.runtime.specialist.assistance;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SpecialistAssistanceHealthIndicator implements HealthIndicator {
    private final SpecialistAssistanceStatusService status;
    public SpecialistAssistanceHealthIndicator(SpecialistAssistanceStatusService status) { this.status = status; }
    @Override public Health health() {
        var current = status.status();
        var health = Boolean.TRUE.equals(current.get("ready")) ? Health.up() : Health.down();
        health.withDetail("enabled", current.get("enabled"));
        if (current.containsKey("contentHash")) health.withDetail("contentHash", current.get("contentHash"));
        return health.build();
    }
}
