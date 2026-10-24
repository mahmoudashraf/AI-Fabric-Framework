package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;

@Service
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProviderUsageTopUpService {
    private final ProviderUsageAllowanceService allowances;
    private final ProviderUsageRuntimeClient runtime;
    private final ProviderUsageValidityRuntimeAccess access;
    private final ProviderUsageTopUpCapabilityGate capabilities;
    private final DeploymentProviderSecretResolutionService secrets;
    private final ProviderUsageValidityExtensionStore locks;
    private final ProviderUsageTopUpStore store;
    private final ProviderUsageAllowanceCodec codec;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ProviderUsageTopUpService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        ProviderUsageAllowanceService allowances, ProviderUsageRuntimeClient runtime, ProviderUsageValidityRuntimeAccess access,
        ProviderUsageTopUpCapabilityGate capabilities, DeploymentProviderSecretResolutionService secrets) {
        this(source, manager, mapper, allowances, runtime, access, capabilities, secrets, Clock.systemUTC());
    }

    ProviderUsageTopUpService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        ProviderUsageAllowanceService allowances, ProviderUsageRuntimeClient runtime, ProviderUsageValidityRuntimeAccess access,
        ProviderUsageTopUpCapabilityGate capabilities, DeploymentProviderSecretResolutionService secrets, Clock clock) {
        this.allowances = allowances; this.runtime = runtime; this.access = access; this.capabilities = capabilities;
        this.secrets = secrets; this.clock = clock; this.codec = new ProviderUsageAllowanceCodec(mapper);
        this.jdbc = new JdbcTemplate(source); this.locks = new ProviderUsageValidityExtensionStore(jdbc, manager, codec);
        this.store = new ProviderUsageTopUpStore(jdbc, codec);
    }

    public Plan plan(ReviewRequest request) {
        String actor = staff(); if (request == null) throw invalid();
        var allocation = allocation(request);
        var resolved = access.resolve(request.scope(), request.releaseId(), false);
        String capabilityHash = capabilities.require(request.scope(), request.releaseId(), resolved);
        verifyReadback(runtime.read(resolved.baseUrl(), resolved.headers()), allocation, request.expectedEffectiveLimits());
        var body = new RuntimeCommand(1, request.topUpId(), allocation.approvalId(), allocation.storeUuid(),
            allocation.runtimeAllocationFingerprint(), request.expectedEffectiveLimits(), request.additionalLimits(), actor,
            request.approvalEvidenceRef(), request.payerRef(), request.reason());
        var command = new Command(request, body, resolved.baseUrl(), resolved.authorityFingerprint(), capabilityHash,
            codec.fingerprint(List.of(request, body, resolved.baseUrl(), resolved.authorityFingerprint(), capabilityHash)));
        return locks.locked(allocation, () -> {
            requireEffective(allocation, request.expectedEffectiveLimits()); requireUnexpired(allocation);
            var previous = store.read(request.topUpId());
            if (previous != null) { exact(previous, command); return new Plan(previous.command(), allocation.caps(), allocation.expiresAt(), false); }
            store.noPending(allocation.approvalId()); store.requireNewFunding(request);
            store.save(new Review(command, actor, clock.instant()));
            return new Plan(command, allocation.caps(), allocation.expiresAt(), false);
        });
    }

    public Result execute(String id, Command command) {
        staff(); identifier(id);
        if (command == null || !id.equals(command.review().topUpId())) throw invalid();
        var allocation = allocation(command.review()); var review = required(id); exact(review, command);
        var resolved = access.resolve(command.review().scope(), command.review().releaseId(), false); exactAccess(command, resolved);
        // A durable intent is never another send, including a replay after a process restart.
        var previous = locks.locked(allocation, () -> resultIfAttempted(review, allocation));
        if (previous != null) return previous;
        if (!command.capabilityManifestHash().equals(capabilities.require(command.review().scope(), command.review().releaseId(), resolved)))
            throw conflict("PROVIDER_USAGE_TOP_UP_CAPABILITY_CHANGED");
        verifyReadback(runtime.read(resolved.baseUrl(), resolved.headers()), allocation, command.runtimeCommand().expectedEffectiveLimits());
        var write = access.resolve(command.review().scope(), command.review().releaseId(), true); exactAccess(command, write);
        boolean claimed = locks.locked(allocation, () -> {
            if (store.event(review, "INTENT") != null) return false;
            store.noPending(allocation.approvalId()); requireEffective(allocation, command.runtimeCommand().expectedEffectiveLimits());
            requireUnexpired(allocation);
            store.append(review, "INTENT", staff(), clock.instant(), null);
            return true;
        });
        if (!claimed) return locks.locked(allocation, () -> Objects.requireNonNull(resultIfAttempted(review, allocation)));
        try {
            return acknowledge(review, allocation, runtime.topUp(write.baseUrl(), command.runtimeCommand(), write.headers()));
        } catch (RuntimeException uncertain) {
            // Transport errors and non-exact receipts are equally uncertain. Only GET reconciliation can settle them.
            return locks.locked(allocation, () -> {
                if (store.event(review, "ACK") == null && store.event(review, "UNKNOWN") == null)
                    store.append(review, "UNKNOWN", staff(), clock.instant(), null);
                return Objects.requireNonNull(resultIfAttempted(review, allocation));
            });
        }
    }

    public Result read(String id) {
        staff(); var review = required(identifier(id)); var allocation = allocation(review.command().review());
        return locks.locked(allocation, () -> result(review, allocation));
    }

    /** Missing, wrong or unavailable GET receipts retain the durable pending intent. Never resends POST. */
    public Result reconcile(String id) {
        staff(); var review = required(identifier(id)); var command = review.command(); var allocation = allocation(command.review());
        var previous = locks.locked(allocation, () -> result(review, allocation));
        if (previous.status() != Status.UNKNOWN) return previous;
        var resolved = access.resolve(command.review().scope(), command.review().releaseId(), false); exactAccess(command, resolved);
        try { return acknowledge(review, allocation, runtime.readTopUp(resolved.baseUrl(), id, resolved.headers())); }
        catch (RuntimeException unavailable) { return locks.locked(allocation, () -> result(review, allocation)); }
    }

    private Result acknowledge(Review review, SafeAllowanceProjection allocation, RuntimeReceipt receipt) {
        ProviderUsageTopUpStore.verifyReceipt(review.command(), receipt); ProviderUsageTopUpStore.verifyScope(receipt, allocation);
        return locks.locked(allocation, () -> {
            var intent = store.event(review, "INTENT"); if (intent == null) throw conflict("PROVIDER_USAGE_TOP_UP_INTENT_REQUIRED");
            ProviderUsageTopUpStore.verifyReceiptTime(receipt, intent.recordedAt(), clock.instant());
            var ack = store.event(review, "ACK");
            if (ack != null && !ack.receipt().equals(receipt)) throw conflict("PROVIDER_USAGE_TOP_UP_RECEIPT_CHANGED");
            if (ack == null) {
                requireEffective(allocation, receipt.previousEffectiveLimits());
                store.append(review, "ACK", staff(), clock.instant(), receipt);
            }
            return result(review, allocation);
        });
    }

    private Result resultIfAttempted(Review review, SafeAllowanceProjection allocation) {
        return store.event(review, "INTENT") == null ? null : result(review, allocation);
    }

    private Result result(Review review, SafeAllowanceProjection allocation) {
        var effective = store.effectiveLimits(allocation);
        var ack = store.event(review, "ACK"); var intent = store.event(review, "INTENT");
        return new Result(review.command().review().topUpId(), ack != null ? Status.ACKNOWLEDGED : intent != null ? Status.UNKNOWN : Status.REVIEWED,
            allocation.caps(), effective, allocation.expiresAt(), ProviderUsageValidityExtensionStore.effectiveExpiry(jdbc, codec, allocation),
            ack == null ? null : ack.receipt());
    }

    private SafeAllowanceProjection allocation(ReviewRequest request) {
        var allocation = allowances.readForValidityExtension(request.scope());
        if (allocation.state() != InitializationState.ACTIVE || allocation.storeUuid() == null
            || !allocation.storeUuid().equals(request.storeUuid())
            || !allocation.approvalFingerprint().equals(request.expectedApprovalFingerprint())
            || !allocation.runtimeAllocationFingerprint().equals(allocation.initializationFingerprint()))
            throw conflict("PROVIDER_USAGE_TOP_UP_ALLOCATION_CHANGED");
        // The shared validity reader validates lifecycle/ownership; also fence today's resolved provider credential.
        var binding = allocation.binding();
        var secret = secrets.resolve(request.scope().deploymentId(), binding.secretPurpose(), binding.managedSecretRef());
        if (secret == null || !secret.resolved() || secret.value() == null || secret.value().isBlank()
            || !ProviderUsageAllowanceCodec.sha256(secret.value()).equals(allocation.credentialSha256()))
            throw conflict("PROVIDER_USAGE_TOP_UP_CREDENTIAL_CHANGED");
        return allocation;
    }

    private void verifyReadback(ProviderUsageRuntimeClient.Readback observed, SafeAllowanceProjection allocation, Limits expected) {
        var scope = allocation.scope();
        if (observed == null || !"ACTIVE".equals(observed.state()) || !Boolean.FALSE.equals(observed.stopped())
            || !allocation.storeUuid().equals(observed.storeUuid()) || !allocation.approvalId().equals(observed.approvalId())
            || !allocation.runtimeAllocationFingerprint().equals(observed.runtimeFingerprint())
            || !scope.customerId().equals(observed.customerId()) || !scope.deploymentId().equals(observed.deploymentId())
            || !scope.targetProfileId().equals(observed.targetProfileId()) || !scope.environment().equals(observed.environment())
            || !allocation.pricingReference().equals(observed.rateCardReference()) || !allocation.caps().equals(observed.limits())
            || !allocation.expiresAt().equals(observed.expiresAt()) || !allocation.effectiveExpiresAt().equals(observed.effectiveExpiresAt())
            || !expected.equals(observed.effectiveLimits())) throw conflict("PROVIDER_USAGE_TOP_UP_RUNTIME_CHANGED");
    }

    private void requireEffective(SafeAllowanceProjection allocation, Limits expected) {
        if (!store.effectiveLimits(allocation).equals(expected)) throw conflict("PROVIDER_USAGE_TOP_UP_EFFECTIVE_LIMITS_CHANGED");
    }

    private void requireUnexpired(SafeAllowanceProjection allocation) {
        var expiry = ProviderUsageValidityExtensionStore.effectiveExpiry(jdbc, codec, allocation);
        if (!allocation.effectiveExpiresAt().equals(expiry) || !clock.instant().isBefore(expiry))
            throw conflict("PROVIDER_USAGE_TOP_UP_VALIDITY_CHANGED");
    }

    private Review required(String id) {
        var review = store.read(id); if (review == null) throw missing("PROVIDER_USAGE_TOP_UP_REVIEW_REQUIRED"); return review;
    }
    private static void exact(Review review, Command command) {
        if (!review.command().equals(command)) throw conflict("PROVIDER_USAGE_TOP_UP_COMMAND_CHANGED");
    }
    private static void exactAccess(Command command, ProviderUsageValidityRuntimeAccess.Access resolved) {
        if (!command.runtimeBaseUrl().equals(resolved.baseUrl()) || !command.authorityFingerprint().equals(resolved.authorityFingerprint()))
            throw conflict("PROVIDER_USAGE_TOP_UP_AUTHORITY_CHANGED");
    }
    private static String staff() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw denied("PROVIDER_USAGE_ADMIN_REVIEW_REQUIRED");
        return text(principal.actorId(), 255);
    }
}
