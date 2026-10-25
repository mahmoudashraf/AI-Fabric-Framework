package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyLifecycleService.*;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.*;

@Service
public class ManagedDependencyOperatorService {
    private final ManagedDependencyLifecycleService lifecycle;
    private final ManagedDependencyGraphStore store;
    private final ManagedDependencySpecValidator validator;
    private final PlatformSecretService secrets;
    private final ManagedDependencyCustomerOfferGuard customerOfferGuard;
    public ManagedDependencyOperatorService(ManagedDependencyLifecycleService lifecycle, ManagedDependencyGraphStore store,
                                            ManagedDependencySpecValidator validator, PlatformSecretService secrets,
                                            ManagedDependencyCustomerOfferGuard customerOfferGuard) {
        this.lifecycle = lifecycle; this.store = store; this.validator = validator; this.secrets = secrets;
        this.customerOfferGuard = customerOfferGuard;
    }
    public record Enroll(String slot, String displayName, String operationKey) {}
    public record Mutation(int expectedCredentialRevision, String operationKey) {}
    public record Delivery(String operatorId, String bindingId, int credentialRevision, String apiKey) {
        @Override public String toString() { return "Delivery[operatorId=" + operatorId + ", apiKey=***]"; }
    }

    public ManagedDependencyLifecycleService.Summary enroll(String deploymentId, String targetProfileId, Enroll request) {
        owner(deploymentId, targetProfileId);
        return enrollInternal(deploymentId, targetProfileId, request, true);
    }

    ManagedDependencyLifecycleService.Summary enrollInitialOwnerForRelease(String deploymentId,
            String targetProfileId, String releaseId, Enroll request) {
        var graph = lifecycle.find(deploymentId, targetProfileId);
        if (releaseId == null || !releaseId.equals(graph.getReleaseId())) fail("DEPENDENCY_RELEASE_BINDING_CHANGED");
        if (store.state(graph).operators.stream().anyMatch(operator -> request.slot().equals(operator.slot))) {
            return lifecycle.summary(graph);
        }
        return enrollInternal(deploymentId, targetProfileId, request, false);
    }

    private ManagedDependencyLifecycleService.Summary enrollInternal(String deploymentId, String targetProfileId,
            Enroll request, boolean reconcile) {
        required(request.slot(), 48); required(request.displayName(), 128); required(request.operationKey(), 128);
        // These are protocol delimiters, not semantic text interpretation.
        if (request.displayName().chars().anyMatch(c -> c == '|' || c == ';' || c == ',' || Character.isISOControl(c))) fail("INBOX_OPERATOR_NAME_INVALID");
        var graph = lifecycle.find(deploymentId, targetProfileId);
        boolean[] accepted = {false};
        if (store.spec(graph).services().stream().noneMatch(s -> request.slot().equals(s.slot())
            && s.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1)) fail("INBOX_OPERATOR_SLOT_INVALID");
        store.mutate(graph.getId(), null, (current, state) -> {
            requireIdle(current);
            requireNoPendingRevocation(state);
            if (reconcile) customerOfferGuard.authorizeGrant(current, INBOX_OPERATOR_ENROLL,
                request.operationKey(), request.slot());
            var matchingOwner = state.operators.stream()
                .filter(operator -> request.slot().equals(operator.slot)
                    && request.displayName().equals(operator.displayName))
                .findFirst().orElse(null);
            if (matchingOwner != null) {
                if (!"ENROLLED".equals(matchingOwner.status)) fail("INBOX_INITIAL_OWNER_REVOKED");
                return;
            }
            var fingerprint = validator.fingerprint(List.of("ENROLL", request.slot(), request.displayName()));
            if (!command(state, request.operationKey(), fingerprint, id("operator"))) return;
            accepted[0] = true;
            var operator = new ManagedDependencyState.Operator();
            operator.id = state.commands.get(request.operationKey()).resultId();
            operator.displayName = request.displayName(); operator.slot = request.slot();
            operator.secretName = lifecycle.managedSecretName(graph.getId(), operator.slot, operator.id + "-1");
            secrets.upsertManagedSecret(operator.secretName, credential(), Map.of("dependencyGraphId", graph.getId(),
                "deploymentId", graph.getDeploymentId(), "targetProfileId", graph.getTargetProfileId(), "operatorId", operator.id));
            state.operators.add(operator); node(state, operator.slot).disabled = false;
            changed(current, state);
        });
        lifecycle.audit(graph.getId(), "INBOX_OPERATOR_ENROLLED", Map.of("operationKey", request.operationKey()));
        if (!accepted[0]) return reconcile
            ? lifecycle.status(deploymentId, targetProfileId)
            : lifecycle.summary(lifecycle.find(deploymentId, targetProfileId));
        return reconcile
            ? lifecycle.reconcile(deploymentId, targetProfileId, request.operationKey() + ":apply")
            : lifecycle.summary(lifecycle.find(deploymentId, targetProfileId));
    }

    public ManagedDependencyLifecycleService.Summary rotate(String deploymentId, String targetProfileId, String operatorId, Mutation request) {
        return mutate(deploymentId, targetProfileId, operatorId, request, false);
    }
    public ManagedDependencyLifecycleService.Summary revoke(String deploymentId, String targetProfileId, String operatorId, Mutation request) {
        return mutate(deploymentId, targetProfileId, operatorId, request, true);
    }
    private ManagedDependencyLifecycleService.Summary mutate(String deploymentId, String targetProfileId, String operatorId, Mutation request, boolean revoke) {
        owner(deploymentId, targetProfileId);
        var graph = lifecycle.find(deploymentId, targetProfileId);
        boolean[] accepted = {false};
        store.mutate(graph.getId(), null, (current, state) -> {
            requireIdle(current);
            var operator = operator(state, operatorId);
            requireCompatiblePendingRevocation(state, operatorId, request.operationKey(), revoke);
            if (!revoke) customerOfferGuard.authorizeGrant(current, INBOX_OPERATOR_ROTATE,
                request.operationKey(), operator.slot);
            if (!command(state, request.operationKey(), validator.fingerprint(List.of(revoke ? "REVOKE" : "ROTATE", operatorId,
                request.expectedCredentialRevision())), operatorId)) return;
            accepted[0] = true;
            if (operator.credentialRevision != request.expectedCredentialRevision() || !"ENROLLED".equals(operator.status)) fail("INBOX_OPERATOR_REVISION_CONFLICT");
            state.retiredOperatorSecretNames.add(operator.secretName);
            operator.effectiveAt = null;
            if (revoke) {
                operator.status = "REVOKED";
                if (state.operators.stream().noneMatch(o -> o.slot.equals(operator.slot) && "ENROLLED".equals(o.status))) {
                    node(state, operator.slot).disabled = true;
                }
            } else {
                operator.credentialRevision++; operator.delivered = false;
                operator.secretName = lifecycle.managedSecretName(graph.getId(), operator.slot, operator.id + "-" + operator.credentialRevision);
                secrets.upsertManagedSecret(operator.secretName, credential(), Map.of("dependencyGraphId", graph.getId(),
                    "deploymentId", graph.getDeploymentId(), "targetProfileId", graph.getTargetProfileId(), "operatorId", operator.id));
            }
            changed(current, state);
        });
        lifecycle.audit(graph.getId(), revoke ? "INBOX_OPERATOR_REVOKE_REQUESTED" : "INBOX_OPERATOR_ROTATE_REQUESTED", Map.of("operatorId", operatorId));
        if (!accepted[0] && !revoke) return lifecycle.status(deploymentId, targetProfileId);
        // The old key remains effective until managed redeploy/readback completes. Never report immediate revoke.
        return revoke
            ? lifecycle.reconcileSecurityRevocation(deploymentId, targetProfileId, operatorId, request.operationKey())
            : lifecycle.reconcile(deploymentId, targetProfileId, request.operationKey() + ":apply");
    }
    public Delivery deliver(String deploymentId, String targetProfileId, String operatorId, Mutation request, boolean secureTransport) {
        owner(deploymentId, targetProfileId);
        if (!secureTransport) fail("INBOX_CREDENTIAL_TLS_REQUIRED");
        var graph = lifecycle.find(deploymentId, targetProfileId);
        Delivery[] delivery = {null};
        store.mutate(graph.getId(), null, (current, state) -> {
            requireIdle(current);
            requireNoPendingRevocation(state);
            var operator = operator(state, operatorId);
            customerOfferGuard.authorizeGrant(current, INBOX_OPERATOR_DELIVER,
                request.operationKey(), operator.slot);
            if (!"READY".equals(current.getStatus()) || state.configurationRevision != state.effectiveConfigurationRevision
                || !"ENROLLED".equals(operator.status) || operator.effectiveAt == null) fail("INBOX_OPERATOR_CONFIGURATION_NOT_EFFECTIVE");
            if (operator.credentialRevision != request.expectedCredentialRevision()) fail("INBOX_OPERATOR_REVISION_CONFLICT");
            // Delivery is intentionally non-replayable, even if the client loses the response.
            if (operator.delivered || !command(state, request.operationKey(), validator.fingerprint(List.of("DELIVER", operatorId,
                request.expectedCredentialRevision())), operatorId)) fail("INBOX_CREDENTIAL_ALREADY_DELIVERED");
            var key = secrets.resolveSecret(operator.secretName);
            if (key == null || key.length() < 32) fail("INBOX_OPERATOR_SECRET_MISSING");
            operator.delivered = true;
            delivery[0] = new Delivery(operatorId, node(state, operator.slot).bindingId, operator.credentialRevision, key);
        });
        lifecycle.audit(graph.getId(), "INBOX_OPERATOR_CREDENTIAL_DELIVERED", Map.of("operatorId", operatorId, "credentialRevision", request.expectedCredentialRevision()));
        return delivery[0];
    }
    private void owner(String deploymentId, String targetProfileId) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.CUSTOMER_ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Customer owner enrollment is required.");
        }
        lifecycle.requireScope(deploymentId, targetProfileId, true);
    }
    private static ManagedDependencyState.Operator operator(ManagedDependencyState state, String id) {
        return state.operators.stream().filter(o -> o.id.equals(id)).findFirst().orElseThrow(() -> conflict("INBOX_OPERATOR_NOT_FOUND"));
    }
    private static void requireIdle(ManagedDependencyGraphEntity graph) {
        if (graph.getWorkerToken() != null || "RETIRING".equals(graph.getStatus()) || "RETIRED".equals(graph.getStatus())) fail("DEPENDENCY_OPERATION_IN_PROGRESS");
    }
    private static void requireNoPendingRevocation(ManagedDependencyState state) {
        if (state.operators.stream().anyMatch(operator -> "REVOKED".equals(operator.status)
            && operator.effectiveAt == null) || state.operatorRevocationLifecycleKey != null) {
            fail("INBOX_OPERATOR_REVOCATION_IN_PROGRESS");
        }
    }
    private static void requireCompatiblePendingRevocation(ManagedDependencyState state, String operatorId,
                                                            String operationKey, boolean revoke) {
        var pending = state.operators.stream().filter(operator -> "REVOKED".equals(operator.status)
            && operator.effectiveAt == null).toList();
        if (pending.isEmpty() && state.operatorRevocationLifecycleKey == null) return;
        var command = state.commands.get(operationKey);
        if (!revoke || pending.size() != 1 || !operatorId.equals(pending.getFirst().id)
            || command == null || !operatorId.equals(command.resultId())) {
            fail("INBOX_OPERATOR_REVOCATION_IN_PROGRESS");
        }
    }
    private static void changed(ManagedDependencyGraphEntity graph, ManagedDependencyState state) {
        state.configurationRevision++; graph.setStatus("BLOCKED"); graph.setStep("OPERATOR_RECONCILE");
        graph.setFailureCode("INBOX_OPERATOR_CONFIGURATION_PENDING");
    }
}
