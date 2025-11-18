package com.ai.behavior.schema;

import java.util.Collection;
import java.util.Optional;

public interface BehaviorSchemaRegistry {

    Optional<BehaviorSignalDefinition> find(String schemaId);

    BehaviorSignalDefinition getRequired(String schemaId);

    Collection<BehaviorSignalDefinition> getAll();
}
