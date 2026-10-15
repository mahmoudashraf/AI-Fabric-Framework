package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class ProductionTargetRetirementInterlock implements TargetRetirementInterlock {
    private static final String ADMIN_SECRET = "PLATFORM_ADMIN_API_KEY";
    private static final String OPERATOR_SECRET = "PLATFORM_OPERATOR_API_KEY";

    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementGateRepository gates;
    private final TargetRetirementInventory inventory;
    private final TargetRetirementJson json;
    private final DeploymentRepository deployments;
    private final PlatformUserRepository users;
    private final PlatformAuthProperties auth;
    private final PlatformSecretService secrets;
    private final TargetRetirementProviderResources resources;
    private final ObjectProvider<TargetRetirementParticipation> participation;
    private final List<TargetRetirementSystemAuthorizer> systemAuthorizers;
    private final TransactionTemplate transactions;

    public ProductionTargetRetirementInterlock(TargetRetirementOperationRepository operations,
            TargetRetirementGateRepository gates, TargetRetirementInventory inventory, TargetRetirementJson json,
            DeploymentRepository deployments, PlatformUserRepository users, PlatformAuthProperties auth,
            PlatformSecretService secrets, TargetRetirementProviderResources resources,
            ObjectProvider<TargetRetirementParticipation> participation,
            List<TargetRetirementSystemAuthorizer> systemAuthorizers,
            PlatformTransactionManager manager) {
        this.operations = operations;
        this.gates = gates;
        this.inventory = inventory;
        this.json = json;
        this.deployments = deployments;
        this.users = users;
        this.auth = auth;
        this.secrets = secrets;
        this.resources = resources;
        this.participation = participation;
        this.systemAuthorizers = List.copyOf(systemAuthorizers);
        this.transactions = new TransactionTemplate(manager);
    }

    @Override
    public Evidence inspect(Context context) {
        List<String> blockers = validate(context, false);
        boolean retentionApproved = context.retention().decision() == RetentionDecision.RECLAIM
            || context.retention().expiresAt() != null && !context.retention().expiresAt().isAfter(Instant.now());
        if (!retentionApproved) blockers = append(blockers, "RETENTION_WINDOW_ACTIVE");
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(),
            "target-retirement-operation:" + context.operationId(), blockers.isEmpty(), true,
            blockers.isEmpty(), retentionApproved, null, null, null, null, false, blockers);
    }

    @Override
    public EffectResult underFence(Context context, String effectKey, Supplier<EffectResult> effect) {
        if (effect == null || effectKey == null || effectKey.isBlank()) {
            throw new TargetRetirementFailure("RETIREMENT_EFFECT_INVALID");
        }
        requireClear(validate(context, true));
        EffectResult result = effect.get();
        requireClear(validate(context, true));
        return result;
    }

    @Override
    public RecoveryEvidence recover(Context context, String previousWorkerToken, String effectKey) {
        Instant now = Instant.now();
        var operation = operations.findById(context.operationId())
            .orElseThrow(() -> new TargetRetirementFailure("RETIREMENT_OPERATION_MISSING"));
        var gate = gates.findByDeploymentIdAndTargetProfileId(context.snapshot().deploymentId(),
            context.snapshot().targetProfileId()).orElseThrow(() -> new TargetRetirementFailure("TARGET_FENCE_MISSING"));
        boolean fenced = List.of(State.RETRYABLE_FAILURE, State.BLOCKED).contains(operation.getState())
            && Objects.equals(previousWorkerToken, operation.getWorkerToken())
            && operation.getLeaseExpiresAt() != null && !operation.getLeaseExpiresAt().isAfter(now)
            && Objects.equals(effectKey, operation.getInFlightEffect())
            && "RETIRING".equals(gate.getState()) && context.operationId().equals(gate.getOperationId())
            && context.fenceEpoch() == gate.getEpoch();
        boolean settled = fenced && settled(context, effectKey);
        return new RecoveryEvidence(context.operationId(), previousWorkerToken, effectKey, now,
            "target-retirement-recovery:" + context.operationId() + ":" + effectKey, fenced, settled);
    }

    @Override
    public EffectResult verifyResourceAbsent(Context context, ResourceGeneration resource) {
        requireClear(validate(context, true));
        EffectResult proof = resources.readback(context, resource);
        if (!proof.complete()) return proof;
        transactions.executeWithoutResult(status -> inventory.recordReclaimed(resource, proof.observedAt()));
        requireClear(validate(context, true));
        return new EffectResult(true, "provider-resource:" + resource.handleId() + ":absent", proof.observedAt());
    }

    private List<String> validate(Context context, boolean requireWorker) {
        var blockers = new java.util.ArrayList<String>();
        var operation = operations.findById(context.operationId()).orElse(null);
        if (operation == null) return List.of("RETIREMENT_OPERATION_MISSING");
        if (!context.operationKey().equals(operation.getOperationKey())
            || !context.requestFingerprint().equals(operation.getRequestFingerprint())
            || !context.snapshot().deploymentId().equals(operation.getDeploymentId())
            || !context.snapshot().targetProfileId().equals(operation.getTargetProfileId())
            || !context.snapshot().customerId().equals(operation.getCustomerId())) {
            blockers.add("RETIREMENT_OPERATION_CHANGED");
        }
        var deployment = deployments.findById(context.snapshot().deploymentId()).orElse(null);
        if (deployment == null || !context.snapshot().customerId().equals(deployment.getCustomerId())
            || !context.snapshot().tenantId().equals(deployment.getTenantId()) || deployment.getArchivedAt() != null) {
            blockers.add("OWNERSHIP_CHANGED");
        }
        authorize(context, blockers);
        var gate = gates.findByDeploymentIdAndTargetProfileId(context.snapshot().deploymentId(),
            context.snapshot().targetProfileId()).orElse(null);
        if (operation.getState() == State.PLANNED) {
            if (gate == null || !"AVAILABLE".equals(gate.getState()) || gate.getEpoch() != context.snapshot().targetEpoch()) {
                blockers.add("TARGET_GENERATION_CHANGED");
            }
        } else if (gate == null || !"RETIRING".equals(gate.getState())
            || !context.operationId().equals(gate.getOperationId()) || context.fenceEpoch() != gate.getEpoch()) {
            blockers.add("TARGET_FENCE_CHANGED");
        }
        if (requireWorker && (!Objects.equals(context.workerToken(), operation.getWorkerToken())
            || operation.getLeaseExpiresAt() == null || !operation.getLeaseExpiresAt().isAfter(Instant.now())
            || !List.of(State.RUNNING, State.CANCEL_REQUESTED).contains(operation.getState()))) {
            blockers.add("STALE_WORKER");
        }
        try {
            inventory.recheck(context.snapshot(), json.read(operation.getRequestJson(), PlanRequest.class));
        } catch (TargetRetirementFailure error) {
            blockers.add(error.code());
        }
        return blockers.stream().distinct().toList();
    }

    private void authorize(Context context, List<String> blockers) {
        if ("SYSTEM".equals(context.requestedByRole()) && "system".equals(context.requestedByActorId())) {
            var matching = systemAuthorizers.stream().filter(value -> value.supports(context)).toList();
            if (matching.size() != 1) blockers.add("RETIREMENT_ACTOR_REVOKED");
            else {
                String blocker = matching.getFirst().blocker(context);
                if (blocker != null) blockers.add(blocker);
            }
            return;
        }
        PlatformRole role;
        try { role = PlatformRole.valueOf(context.requestedByRole()); }
        catch (Exception error) { blockers.add("RETIREMENT_ACTOR_REVOKED"); return; }
        var user = users.findByEmailIgnoreCase(context.requestedByActorId());
        if (user.isPresent()) {
            var value = user.get();
            if (!"ACTIVE".equalsIgnoreCase(value.getStatus()) || !role.name().equals(value.getRole())
                || role == PlatformRole.CUSTOMER_ADMIN
                    && !context.snapshot().customerId().equals(value.getCustomerId())) {
                blockers.add("RETIREMENT_ACTOR_REVOKED");
            }
            return;
        }
        boolean canonicalAdmin = role == PlatformRole.PLATFORM_ADMIN
            && "platform-admin".equals(context.requestedByActorId()) && configured(auth.adminApiKey(), ADMIN_SECRET);
        boolean canonicalOperator = role == PlatformRole.PLATFORM_OPERATOR
            && "platform-operator".equals(context.requestedByActorId()) && configured(auth.operatorApiKey(), OPERATOR_SECRET);
        if (!auth.enabled() || !auth.apiKeyEnabled() || !(canonicalAdmin || canonicalOperator)) {
            blockers.add("RETIREMENT_ACTOR_REVOKED");
        }
    }

    private boolean configured(String configured, String reference) {
        return configured != null && !configured.isBlank()
            || secrets.resolveSecret(reference) != null && !secrets.resolveSecret(reference).isBlank();
    }

    private boolean settled(Context context, String effectKey) {
        String[] parts = effectKey.split(":", 4);
        if (parts.length != 4 || !context.operationId().equals(parts[0])) return false;
        Step step;
        try { step = Step.valueOf(parts[2]); }
        catch (Exception error) { return false; }
        if (step == Step.RESOURCES) {
            var resource = context.snapshot().resources().stream()
                .filter(value -> value.handleId().equals(parts[3]) && !value.preserved()).findFirst().orElse(null);
            return resource != null && resources.readback(context, resource).complete();
        }
        ParticipantKind kind = switch (step) {
            case DRAIN, CANCEL_DRAIN -> ParticipantKind.RUNTIME_DRAIN;
            case AUTHORITY -> ParticipantKind.AUTHORITY;
            case INDEX -> ParticipantKind.INDEX;
            case DEPENDENCIES -> ParticipantKind.DEPENDENCIES;
            default -> null;
        };
        if (kind == null) return false;
        EffectResult result = participation.getObject().participant(kind).readback(context, step, effectKey);
        participation.getObject().validate(result);
        return result.complete();
    }

    private List<String> append(List<String> values, String value) {
        var result = new java.util.ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private void requireClear(List<String> blockers) {
        if (!blockers.isEmpty()) throw new TargetRetirementFailure(blockers.getFirst());
    }
}
