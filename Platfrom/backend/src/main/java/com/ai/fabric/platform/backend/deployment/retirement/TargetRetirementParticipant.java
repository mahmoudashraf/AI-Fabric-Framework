package com.ai.fabric.platform.backend.deployment.retirement;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/**
 * Runtime/inbox, target authority, index and dedicated dependencies each register exactly one bean.
 * inspect must enumerate actual references/work, including an explicit complete empty inventory.
 * apply and readback are idempotent by effectKey and must enforce the supplied durable fence.
 * No participant may replay/migrate work or delete customer source objects/shared resources.
 * CANCEL_DRAIN restores ingress only after proving no reclamation/authority revocation occurred.
 */
public interface TargetRetirementParticipant {
    ParticipantKind kind();
    Evidence inspect(Context context);
    EffectResult apply(Context context, Step step, String effectKey);
    EffectResult readback(Context context, Step step, String effectKey);

    /**
     * Proves an unfinished effect can be resumed by the same durable operation after the prior
     * worker has been fenced. Participants remain fail-closed unless they explicitly implement
     * restart-safe, operation-key-bound checkpoints.
     */
    default boolean recoverySafe(Context context, Step step, String effectKey) { return false; }
}
