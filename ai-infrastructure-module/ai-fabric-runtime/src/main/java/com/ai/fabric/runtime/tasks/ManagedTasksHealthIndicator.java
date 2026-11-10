package com.ai.fabric.runtime.tasks;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("managedTasks")
public final class ManagedTasksHealthIndicator implements HealthIndicator {
    private final ManagedTaskService tasks;
    public ManagedTasksHealthIndicator(ManagedTaskService tasks) { this.tasks = tasks; }
    @Override public Health health() {
        var status = tasks.status();
        return (Boolean.TRUE.equals(status.get("ready")) ? Health.up() : Health.down())
            .withDetail("enabled", status.get("enabled"))
            .withDetail("contractVersion", ManagedTasksBundle.CONTRACT).build();
    }
}
