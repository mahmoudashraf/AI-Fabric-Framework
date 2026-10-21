package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
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
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;

@Service
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProviderUsageValidityExtensionService {
    private final ProviderUsageAllowanceService allowances;
    private final ProviderUsageRuntimeClient runtime;
    private final ProviderUsageValidityRuntimeAccess access;
    private final ProviderUsageValidityCapabilityGate capabilities;
    private final ProviderUsageValidityExtensionStore store;
    private final ProviderUsageAllowanceCodec codec;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ProviderUsageValidityExtensionService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        ProviderUsageAllowanceService allowances, ProviderUsageRuntimeClient runtime, ProviderUsageValidityRuntimeAccess access,
        ProviderUsageValidityCapabilityGate capabilities) {
        this(source, manager, mapper, allowances, runtime, access, capabilities, Clock.systemUTC());
    }

    ProviderUsageValidityExtensionService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper,
        ProviderUsageAllowanceService allowances, ProviderUsageRuntimeClient runtime, ProviderUsageValidityRuntimeAccess access,
        ProviderUsageValidityCapabilityGate capabilities, Clock clock) {
        this.allowances = allowances; this.runtime = runtime; this.access = access; this.capabilities = capabilities; this.clock = clock;
        this.codec = new ProviderUsageAllowanceCodec(mapper); this.jdbc = new JdbcTemplate(source);
        this.store = new ProviderUsageValidityExtensionStore(jdbc, manager, codec);
    }

    public Plan plan(ReviewRequest request) {
        staff(); if (request == null) throw invalid();
        var allocation = allocation(request);
        var resolved = access.resolve(request.scope(), request.releaseId(), false);
        String capabilityHash = capabilities.require(request.scope(), request.releaseId(), resolved);
        var observed = runtime.read(resolved.baseUrl(), resolved.headers());
        verifyReadback(observed, allocation, true);
        validWindow(request);
        var body = new RuntimeCommand(1, request.extensionId(), allocation.approvalId(), allocation.storeUuid(),
            allocation.runtimeAllocationFingerprint(), request.expectedEffectiveExpiresAt(), request.expiresAt(), request.reason());
        var command = new Command(request, body, resolved.baseUrl(), resolved.authorityFingerprint(), capabilityHash,
            codec.fingerprint(List.of(request, body, resolved.baseUrl(), resolved.authorityFingerprint(), capabilityHash)));
        return store.locked(allocation, () -> {
            requireEffective(allocation, request.expectedEffectiveExpiresAt());
            var previous = store.read(request.extensionId());
            if (previous != null) {
                exact(previous, command);
                return new Plan(previous.command(), allocation.expiresAt(), false);
            }
            store.noPending(allocation.approvalId());
            store.save(new Review(command, staff(), clock.instant()));
            return new Plan(command, allocation.expiresAt(), false);
        });
    }

    public Result execute(String id, Command command) {
        staff(); identifier(id);
        if (command == null || command.review() == null || !id.equals(command.review().extensionId())) throw invalid();
        var allocation = allocation(command.review());
        var review = required(id); exact(review, command);
        var resolved = access.resolve(command.review().scope(), command.review().releaseId(), false);
        exactAccess(command, resolved);
        // Historical ACK and an uncertain intent never issue another POST, even on explicit replay.
        var previous = store.locked(allocation, () -> resultIfAttempted(review, allocation));
        if (previous != null) return previous;
        if (!command.capabilityManifestHash().equals(capabilities.require(command.review().scope(), command.review().releaseId(), resolved)))
            throw conflict("PROVIDER_USAGE_VALIDITY_CAPABILITY_CHANGED");
        validWindow(command.review());
        verifyReadback(runtime.read(resolved.baseUrl(), resolved.headers()), allocation, true);
        var write = access.resolve(command.review().scope(), command.review().releaseId(), true);
        exactAccess(command, write);
        boolean claimed = store.locked(allocation, () -> {
            if (store.event(review, "INTENT") != null) return false;
            store.noPending(allocation.approvalId());
            requireEffective(allocation, command.runtimeCommand().expectedEffectiveExpiresAt());
            validWindow(command.review());
            store.append(review, "INTENT", staff(), clock.instant(), null);
            return true;
        });
        if (!claimed) return store.locked(allocation, () -> Objects.requireNonNull(resultIfAttempted(review, allocation)));
        try {
            var receipt = runtime.extendValidity(write.baseUrl(), command.runtimeCommand(), write.headers());
            return acknowledge(review, allocation, receipt);
        } catch (RuntimeException uncertain) {
            // Even a transport 4xx/timeout or invalid body is UNKNOWN here; only exact GET may settle it.
            return store.locked(allocation, () -> {
                if (store.event(review, "ACK") == null && store.event(review, "UNKNOWN") == null)
                    store.append(review, "UNKNOWN", staff(), clock.instant(), null);
                return Objects.requireNonNull(resultIfAttempted(review, allocation));
            });
        }
    }

    public Result read(String id) {
        staff(); var review = required(identifier(id)); var allocation = allocation(review.command().review());
        return store.locked(allocation, () -> result(review, allocation));
    }

    /** Explicit GET reconciliation. Missing receipt does not release the durable intent or authorize a retry. */
    public Result reconcile(String id) {
        staff(); var review = required(identifier(id)); var command = review.command(); var allocation = allocation(command.review());
        var previous = store.locked(allocation, () -> result(review, allocation));
        if (previous.status() != Status.UNKNOWN) return previous;
        var resolved = access.resolve(command.review().scope(), command.review().releaseId(), false); exactAccess(command, resolved);
        try {
            var receipt = runtime.readValidityExtension(resolved.baseUrl(), id, resolved.headers());
            return acknowledge(review, allocation, receipt);
        } catch (RuntimeException unavailable) {
            return store.locked(allocation, () -> result(review, allocation));
        }
    }

    private Result acknowledge(Review review, SafeAllowanceProjection allocation, RuntimeReceipt receipt) {
        ProviderUsageValidityExtensionStore.verifyReceipt(review.command(), receipt);
        ProviderUsageValidityExtensionStore.verifyScope(receipt, allocation);
        return store.locked(allocation, () -> {
            var intent = store.event(review, "INTENT"); if (intent == null) throw conflict("PROVIDER_USAGE_EXTENSION_INTENT_REQUIRED");
            ProviderUsageValidityExtensionStore.verifyReceiptTime(receipt, intent.recordedAt(), clock.instant());
            var ack = store.event(review, "ACK");
            if (ack != null && !ack.receipt().equals(receipt)) throw conflict("PROVIDER_USAGE_EXTENSION_RECEIPT_CHANGED");
            if (ack == null) {
                requireEffective(allocation, receipt.previousEffectiveExpiresAt());
                store.append(review, "ACK", staff(), clock.instant(), receipt);
            }
            return result(review, allocation);
        });
    }

    private Result resultIfAttempted(Review review, SafeAllowanceProjection allocation) {
        return store.event(review, "INTENT") == null ? null : result(review, allocation);
    }

    private Result result(Review review, SafeAllowanceProjection allocation) {
        var ack = store.event(review, "ACK"); var intent = store.event(review, "INTENT");
        if (ack != null && intent == null) throw conflict("PROVIDER_USAGE_EXTENSION_RECORD_CHANGED");
        return new Result(review.command().review().extensionId(), ack != null ? Status.ACKNOWLEDGED : intent != null ? Status.UNKNOWN : Status.REVIEWED,
            allocation.expiresAt(), ProviderUsageValidityExtensionStore.effectiveExpiry(jdbc, codec, allocation), ack == null ? null : ack.receipt());
    }

    private SafeAllowanceProjection allocation(ReviewRequest request) {
        var allocation = allowances.readForValidityExtension(request.scope());
        if (allocation.state() != InitializationState.ACTIVE || allocation.storeUuid() == null
            || !allocation.storeUuid().equals(request.storeUuid())
            || !allocation.approvalFingerprint().equals(request.expectedApprovalFingerprint())
            || !allocation.runtimeAllocationFingerprint().equals(allocation.initializationFingerprint()))
            throw conflict("PROVIDER_USAGE_EXTENSION_ALLOCATION_CHANGED");
        return allocation;
    }

    private void verifyReadback(ProviderUsageRuntimeClient.Readback observed, SafeAllowanceProjection allocation, boolean currentExpiry) {
        var scope = allocation.scope();
        if (observed == null || !"ACTIVE".equals(observed.state()) || !Boolean.FALSE.equals(observed.stopped())
            || !allocation.storeUuid().equals(observed.storeUuid()) || !allocation.approvalId().equals(observed.approvalId())
            || !allocation.runtimeAllocationFingerprint().equals(observed.runtimeFingerprint())
            || !scope.customerId().equals(observed.customerId()) || !scope.deploymentId().equals(observed.deploymentId())
            || !scope.targetProfileId().equals(observed.targetProfileId()) || !scope.environment().equals(observed.environment())
            || !allocation.pricingReference().equals(observed.rateCardReference()) || !allocation.caps().equals(observed.limits())
            || !allocation.expiresAt().equals(observed.expiresAt())
            || (currentExpiry && !allocation.effectiveExpiresAt().equals(observed.effectiveExpiresAt())))
            throw conflict("PROVIDER_USAGE_EXTENSION_RUNTIME_CHANGED");
    }

    private void requireEffective(SafeAllowanceProjection allocation, Instant expected) {
        if (!ProviderUsageValidityExtensionStore.effectiveExpiry(jdbc, codec, allocation).equals(expected))
            throw conflict("PROVIDER_USAGE_EXTENSION_EFFECTIVE_EXPIRY_CHANGED");
    }

    private void validWindow(ReviewRequest request) {
        Instant now = clock.instant();
        if (!request.expiresAt().isAfter(request.expectedEffectiveExpiresAt()) || !request.expiresAt().isAfter(now)
            || request.expiresAt().isAfter(now.plusSeconds(86_400))) throw conflict("PROVIDER_USAGE_EXTENSION_WINDOW_INVALID");
    }

    private Review required(String id) {
        var review = store.read(id); if (review == null) throw missing("PROVIDER_USAGE_EXTENSION_REVIEW_REQUIRED"); return review;
    }
    private static void exact(Review review, Command command) {
        if (!review.command().equals(command)) throw conflict("PROVIDER_USAGE_EXTENSION_COMMAND_CHANGED");
    }
    private static void exactAccess(Command command, ProviderUsageValidityRuntimeAccess.Access resolved) {
        if (!command.runtimeBaseUrl().equals(resolved.baseUrl()) || !command.authorityFingerprint().equals(resolved.authorityFingerprint()))
            throw conflict("PROVIDER_USAGE_EXTENSION_AUTHORITY_CHANGED");
    }
    private static String staff() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw denied("PROVIDER_USAGE_ADMIN_REVIEW_REQUIRED");
        return text(principal.actorId(), 255);
    }
}
