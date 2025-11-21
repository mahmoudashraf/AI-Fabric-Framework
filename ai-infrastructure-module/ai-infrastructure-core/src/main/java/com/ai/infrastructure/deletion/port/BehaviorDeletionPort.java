package com.ai.infrastructure.deletion.port;

import java.util.UUID;

/**
 * SPI allowing optional behavior modules to plug into the generic deletion workflow.
 */
public interface BehaviorDeletionPort {
    int deleteUserBehaviors(UUID userId);
}
