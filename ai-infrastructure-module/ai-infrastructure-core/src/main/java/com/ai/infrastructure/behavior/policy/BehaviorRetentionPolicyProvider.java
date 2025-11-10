package com.ai.infrastructure.behavior.policy;

import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.entity.Behavior.BehaviorType;
import java.util.List;

/**
 * Infrastructure hook allowing customers to control retention of behaviour tracking data.
 */
public interface BehaviorRetentionPolicyProvider {

    /**
     * Retrieve the default retention window for the supplied behaviour type.
     *
     * @param behaviorType behaviour classification
     * @return retention window in days (0 = delete immediately, -1 = keep indefinitely)
     */
    int getRetentionDays(BehaviorType behaviorType);

    /**
     * Resolve retention window for a specific user.
     *
     * @param behaviorType behaviour classification
     * @param userId identifier of the user
     * @return retention window in days (0 = delete immediately, -1 = keep indefinitely)
     */
    int getRetentionDaysForUser(BehaviorType behaviorType, String userId);

    /**
     * Callback invoked before behaviours are deleted allowing customer systems to archive or enrich.
     *
     * @param behaviors immutable list of behaviours scheduled for deletion
     */
    void beforeBehaviorDeletion(List<Behavior> behaviors);
}
