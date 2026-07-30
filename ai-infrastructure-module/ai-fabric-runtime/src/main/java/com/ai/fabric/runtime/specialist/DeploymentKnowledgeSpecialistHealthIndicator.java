package com.ai.fabric.runtime.specialist;

import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.manifest.SpecialistManifestRuntimeStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("deploymentKnowledgeSpecialist")
@ConditionalOnProperty(
    prefix = "app.specialists.deployment-knowledge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DeploymentKnowledgeSpecialistHealthIndicator
    implements HealthIndicator {

    private static final SpecialistId SPECIALIST_ID = SpecialistId.parse(
        DeploymentKnowledgeSpecialistService.SPECIALIST_NAME
            + "@"
            + DeploymentKnowledgeSpecialistService.SPECIALIST_VERSION
    );

    private final SpecialistRegistry registry;
    private final SpecialistManifestRuntimeStatus manifestStatus;

    public DeploymentKnowledgeSpecialistHealthIndicator(
        SpecialistRegistry registry,
        SpecialistManifestRuntimeStatus manifestStatus
    ) {
        this.registry = registry;
        this.manifestStatus = manifestStatus;
    }

    @Override
    public Health health() {
        boolean registered = registry.find(SPECIALIST_ID).isPresent();
        Health.Builder builder = manifestStatus.ready() && registered
            ? Health.up()
            : Health.down();
        return builder
            .withDetail("specialist", SPECIALIST_ID.toString())
            .withDetail("registered", registered)
            .withDetail("manifestReady", manifestStatus.ready())
            .withDetail(
                "manifestDefinitionCount",
                manifestStatus.manifestDefinitionCount()
            )
            .withDetail(
                "registryContentHash",
                manifestStatus.registryContentHash()
            )
            .build();
    }
}
