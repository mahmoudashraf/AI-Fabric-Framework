package com.ai.fabric.platform.backend.deployment.retirement;

import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/**
 * Required external authority; deliberately no default implementation.
 * inspect reauthorizes the persisted requested actor and proves current ownership, entitlement,
 * staging placement, actual provider instance generations (UUID alone is insufficient), exact
 * inventory and absence of
 * active/shared/foreign references for this snapshot. underFence holds a durable reclamation barrier
 * against activation/replacement/cancellation, NOT a database transaction around remote calls.
 * It must reject stale tokens and recheck current authority/references before invoking the effect.
 * recover must fence the previous worker AND settle late provider effects before authorizing retry.
 * A lease timeout, process restart or a provider health response alone is never recovery evidence.
 */
public interface TargetRetirementInterlock {
    Evidence inspect(Context context);
    EffectResult underFence(Context context, String effectKey, Supplier<EffectResult> effect);
    RecoveryEvidence recover(Context context, String previousWorkerToken, String effectKey);
    /** Explicit authoritative absence receipt; an Optional.empty/null/malformed response is not proof. */
    EffectResult verifyResourceAbsent(Context context, ResourceGeneration resource);
}
