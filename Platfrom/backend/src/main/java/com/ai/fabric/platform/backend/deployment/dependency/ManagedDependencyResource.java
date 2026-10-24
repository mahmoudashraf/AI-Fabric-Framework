package com.ai.fabric.platform.backend.deployment.dependency;

import java.util.Map;
import java.util.Set;

/** Immutable owned-resource identity and physical reclamation evidence, without lifecycle policy. */
public record ManagedDependencyResource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                                        Map<String, String> volumes, Map<String, String> volumeReclamationPlan,
                                        Set<String> reclaimedVolumeUuids) {
    public ManagedDependencyResource {
        volumes = volumes == null ? Map.of() : Map.copyOf(volumes);
        volumeReclamationPlan = volumeReclamationPlan == null ? Map.of() : Map.copyOf(volumeReclamationPlan);
        reclaimedVolumeUuids = reclaimedVolumeUuids == null ? Set.of() : Set.copyOf(reclaimedVolumeUuids);
    }

    public ManagedDependencyResource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                                     Map<String, String> volumes) {
        this(slot, serviceRef, applicationUuid, databaseUuid, volumes, Map.of(), Set.of());
    }

    public ManagedDependencyResource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                                     Map<String, String> volumes, Set<String> reclaimedVolumeUuids) {
        this(slot, serviceRef, applicationUuid, databaseUuid, volumes,
            reclaimedVolumeUuids.stream().collect(java.util.stream.Collectors.toMap(
                value -> value, value -> value)), reclaimedVolumeUuids);
    }
}
