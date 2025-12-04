package com.ai.behavior.schema;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface BehaviorSchemaRegistry {

    Optional<BehaviorSignalDefinition> find(String schemaId);

    BehaviorSignalDefinition getRequired(String schemaId);

    Collection<BehaviorSignalDefinition> getAll();

    default Instant getLastLoadedAt() {
        return Instant.EPOCH;
    }
}
