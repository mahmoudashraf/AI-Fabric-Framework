package com.ai.fabric.runtime.budget;

import static com.ai.fabric.budget.BudgetException.Code.EXPIRED;
import static com.ai.fabric.budget.BudgetException.Code.STOPPED;
import static com.ai.fabric.runtime.budget.RuntimeBudgetBindingException.Code.*;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt;
import com.ai.fabric.budget.ProviderBudgetLedger.Snapshot;
import com.ai.fabric.budget.ProviderBudgetLedger.ValidityExtensionCommand;
import com.ai.fabric.budget.ProviderBudgetLedger.ValidityExtensionReceipt;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Private CP initialization is separate from read-only boot. Use a non-enlisted runtime DataSource. */
@DependsOnDatabaseInitialization
public final class RuntimeBudgetBindingService {
    public enum State { UNINITIALIZED, INITIALIZING, INITIALIZED, ACTIVE }
    public record StoreReceipt(State state, UUID storeUuid, String approvalId, String runtimeFingerprint) { }
    public record Activation(String approvalId, UUID storeUuid, String runtimeFingerprint) {
        public Activation {
            Objects.requireNonNull(approvalId); Objects.requireNonNull(storeUuid); Objects.requireNonNull(runtimeFingerprint);
        }
    }
    public record StoreSnapshot(StoreReceipt binding, Snapshot accounting) { }
    public record Readiness(boolean ready, String code) { }

    private final RuntimeBudgetConfiguration configuration;
    private final ProviderBudgetLedger ledger;
    private final DataSource dataSource;
    private final Clock clock;

    public RuntimeBudgetBindingService(RuntimeBudgetConfiguration configuration, ProviderBudgetLedger ledger,
                                      DataSource dataSource, Clock clock) {
        this.configuration = Objects.requireNonNull(configuration);
        this.ledger = configuration.enabled() ? Objects.requireNonNull(ledger) : ledger;
        this.dataSource = configuration.enabled() ? Objects.requireNonNull(dataSource) : dataSource;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Accounting readiness only; host must also prove provider/path coverage. No probes or writes. */
    public Readiness readiness() {
        if (!configuration.enabled()) return new Readiness(true, "DISABLED");
        try { requireActive(); return new Readiness(true, "ACTIVE"); }
        catch (RuntimeBudgetBindingException denied) { return new Readiness(false, denied.code().name()); }
        catch (BudgetException denied) { return new Readiness(false, denied.code().name()); }
    }

    /** Trusted private CP call only. CP must durably save this store receipt BEFORE activating it. */
    public StoreReceipt initialize(RuntimeBudgetAllocation allocation) {
        requireEnabled();
        Objects.requireNonNull(allocation);
        if (!configuration.scope().equals(allocation.approval().scope())) throw failure(ALLOCATION_SCOPE_MISMATCH);
        StoreReceipt claimed = transaction(connection -> {
            StoreReceipt existing = binding(connection, true);
            if (existing.state() != State.UNINITIALIZED) {
                requireMatch(existing, allocation.approval().id(), existing.storeUuid(), allocation.runtimeFingerprint());
                return existing;
            }
            if (!clock.instant().isBefore(allocation.approval().expiresAt())) throw new BudgetException(EXPIRED);
            StoreReceipt claim = new StoreReceipt(State.INITIALIZING, UUID.randomUUID(), allocation.approval().id(), allocation.runtimeFingerprint());
            write(connection, claim);
            return claim;
        });
        // No binding lock/connection is held across the ledger's independently committed transaction.
        if (claimed.state() != State.INITIALIZING) {
            if (!readExact(claimed).approval().equals(allocation.approval())) throw failure(BINDING_CONFLICT);
            return claimed;
        }
        Snapshot approved = ledger.approve(allocation.approval());
        if (!approved.approval().equals(allocation.approval())) throw failure(BINDING_CONFLICT);
        return transaction(connection -> {
            StoreReceipt current = binding(connection, true);
            requireMatch(current, claimed.approvalId(), claimed.storeUuid(), claimed.runtimeFingerprint());
            if (current.state() != State.INITIALIZING) return current;
            StoreReceipt initialized = new StoreReceipt(State.INITIALIZED, current.storeUuid(), current.approvalId(), current.runtimeFingerprint());
            write(connection, initialized);
            return initialized;
        });
    }

    public StoreReceipt activate(Activation activation) {
        requireEnabled();
        Objects.requireNonNull(activation);
        StoreReceipt candidate = transaction(connection -> binding(connection, false));
        if (candidate.state() == State.UNINITIALIZED) throw failure(NOT_INITIALIZED);
        if (candidate.state() == State.INITIALIZING) throw failure(NOT_ACTIVE);
        requireMatch(candidate, activation.approvalId(), activation.storeUuid(), activation.runtimeFingerprint());
        Snapshot snapshot = readExact(candidate);
        if (candidate.state() == State.ACTIVE) return candidate;
        active(snapshot);
        return transaction(connection -> {
            StoreReceipt existing = binding(connection, true);
            requireMatch(existing, activation.approvalId(), activation.storeUuid(), activation.runtimeFingerprint());
            if (existing.state() == State.ACTIVE) return existing;
            if (existing.state() != State.INITIALIZED) throw failure(NOT_ACTIVE);
            StoreReceipt activated = new StoreReceipt(State.ACTIVE, existing.storeUuid(), existing.approvalId(), existing.runtimeFingerprint());
            write(connection, activated);
            return activated;
        });
    }

    /** Pure read, including inactive/expired/stopped state; never repairs an empty store. */
    public StoreSnapshot readStore() {
        requireEnabled();
        StoreReceipt binding = transaction(connection -> binding(connection, false));
        return new StoreSnapshot(binding, binding.state() == State.UNINITIALIZED || binding.state() == State.INITIALIZING
            ? null : readExact(binding));
    }

    public Snapshot readCurrent() {
        var store = readStore();
        if (store.accounting() == null) throw failure(store.binding().state() == State.UNINITIALIZED ? NOT_INITIALIZED : NOT_ACTIVE);
        return store.accounting();
    }

    public Snapshot requireActive() {
        var store = readStore();
        if (store.binding().state() == State.UNINITIALIZED) throw failure(NOT_INITIALIZED);
        if (store.binding().state() != State.ACTIVE) throw failure(NOT_ACTIVE);
        return active(store.accounting());
    }

    public Approval approval() { return requireActive().approval(); }
    public ProviderBudgetLedger.Scope scope() { requireEnabled(); return configuration.scope(); }

    public java.time.Instant effectiveExpiresAt() {
        var snapshot = readCurrent();
        return ledger.effectiveExpiresAt(snapshot.approval().id(), configuration.scope());
    }

    /** ACTIVE is a store identity fence, not a demand that the original validity is still current. */
    public ValidityExtensionReceipt extendValidity(ValidityExtensionCommand command) {
        requireEnabled();
        if (command == null) throw new BudgetException(BudgetException.Code.INVALID_REQUEST);
        StoreReceipt current = transaction(connection -> binding(connection, false));
        if (current.state() != State.ACTIVE) throw failure(NOT_ACTIVE);
        requireMatch(current, command.approvalId(), command.storeUuid(), command.runtimeFingerprint());
        Snapshot original = readExact(current);
        // ACTIVE bindings are immutable after activation; no nested binding/ledger connection is held.
        return ledger.extendValidity(original.approval(), command);
    }

    public Optional<ValidityExtensionReceipt> readValidityExtension(String extensionId) {
        requireEnabled();
        ProviderBudgetLedger.text(extensionId, 128);
        StoreReceipt current = transaction(connection -> binding(connection, false));
        if (current.state() == State.UNINITIALIZED || current.state() == State.INITIALIZING) return Optional.empty();
        readExact(current);
        var receipt = ledger.readValidityExtension(current.approvalId(), configuration.scope(), extensionId);
        receipt.ifPresent(value -> requireMatch(current, value.approvalId(), value.storeUuid(), value.runtimeFingerprint()));
        return receipt;
    }

    /** Host must resolve verified auth first. Every HTTP retry receives a fresh server attempt. */
    public ChatReceipt recordVerifiedChatIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "chat:query");
    }

    /** Queries and suggestions share the allocation's nonrefundable interactive-request ceiling. */
    public ChatReceipt recordVerifiedSuggestionsIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "chat:suggestions");
    }

    private ChatReceipt recordVerifiedInteractiveIngress(RuntimeResolvedIdentity identity, String requiredScope) {
        RuntimeAuthContext auth = requireVerifiedDeployment(identity);
        if (auth.getGrantedScopes() == null || !auth.getGrantedScopes().contains(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_BUDGET_INTERACTIVE_SCOPE_REQUIRED");
        }
        Approval expected = approval();
        ChatReceipt receipt = ledger.recordChatIngress(expected.id(), expected.scope(), UUID.randomUUID());
        if (receipt == null || !receipt.newlyCounted()) throw failure(STORAGE_UNAVAILABLE);
        return receipt;
    }

    public RuntimeAuthContext requireVerifiedDeployment(RuntimeResolvedIdentity identity) {
        requireEnabled();
        RuntimeAuthContext auth = identity == null ? null : identity.getAuthContext();
        if (auth == null || auth.getAuthMode() == null || auth.getSubjectId() == null || auth.getSubjectId().isBlank()
            || auth.getExpiresAt() == null || !clock.instant().isBefore(auth.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "VERIFIED_RUNTIME_BUDGET_IDENTITY_REQUIRED");
        }
        if (!configuration.scope().customerId().equals(auth.getCustomerId())
            || !configuration.scope().deploymentId().equals(auth.getDeploymentId())
            || !configuration.tenantId().equals(auth.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_BUDGET_SCOPE_MISMATCH");
        }
        return auth;
    }

    private Snapshot readExact(StoreReceipt binding) {
        Snapshot snapshot = ledger.read(binding.approvalId(), configuration.scope());
        if (!RuntimeBudgetAllocation.fingerprint(snapshot.approval()).equals(binding.runtimeFingerprint())) throw failure(BINDING_CONFLICT);
        return snapshot;
    }

    private Snapshot active(Snapshot snapshot) {
        if (snapshot.stopped()) throw new BudgetException(STOPPED);
        if (!clock.instant().isBefore(ledger.effectiveExpiresAt(snapshot.approval().id(), configuration.scope()))) throw new BudgetException(EXPIRED);
        return snapshot;
    }

    private static void requireMatch(StoreReceipt stored, String approvalId, UUID storeUuid, String fingerprint) {
        if (!stored.approvalId().equals(approvalId) || !stored.storeUuid().equals(storeUuid)
            || !stored.runtimeFingerprint().equals(fingerprint)) throw failure(BINDING_CONFLICT);
    }

    private static StoreReceipt binding(Connection connection, boolean lock) throws SQLException {
        try (var query = connection.prepareStatement("SELECT binding_state, store_uuid, approval_id, allocation_fingerprint"
            + " FROM runtime_provider_budget_binding WHERE singleton_id = 1" + (lock ? " FOR UPDATE" : ""));
             var rows = query.executeQuery()) {
            if (!rows.next()) throw failure(STORAGE_UNAVAILABLE);
            String uuid = rows.getString("store_uuid");
            try {
                return new StoreReceipt(State.valueOf(rows.getString("binding_state")), uuid == null ? null : UUID.fromString(uuid),
                    rows.getString("approval_id"), rows.getString("allocation_fingerprint"));
            } catch (IllegalArgumentException corrupt) { throw failure(STORAGE_UNAVAILABLE); }
        }
    }

    private static void write(Connection connection, StoreReceipt receipt) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE runtime_provider_budget_binding SET binding_state = ?, store_uuid = ?,"
            + " approval_id = ?, allocation_fingerprint = ? WHERE singleton_id = 1")) {
            update.setString(1, receipt.state().name()); update.setString(2, receipt.storeUuid().toString());
            update.setString(3, receipt.approvalId()); update.setString(4, receipt.runtimeFingerprint());
            if (update.executeUpdate() != 1) throw failure(STORAGE_UNAVAILABLE);
        }
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) throw failure(STORAGE_UNAVAILABLE);
            connection.setAutoCommit(false);
            boolean ended = false;
            try {
                T result = work.run(connection);
                connection.commit();
                ended = true;
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); ended = true; } catch (SQLException ignored) { }
                throw error;
            } finally {
                if (ended) connection.setAutoCommit(true);
            }
        } catch (SQLException error) { throw failure(STORAGE_UNAVAILABLE); }
    }

    private void requireEnabled() { if (!configuration.enabled()) throw failure(DISABLED); }
    private static RuntimeBudgetBindingException failure(RuntimeBudgetBindingException.Code code) { return new RuntimeBudgetBindingException(code); }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
}
