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
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Private CP initialization is separate from read-only boot. Use a non-enlisted runtime DataSource. */
@DependsOnDatabaseInitialization
public final class RuntimeBudgetBindingService {
    public enum Account {
        GENERATION("runtime_provider_budget_binding"), RERANKING("runtime_reranking_budget_binding");
        private final String table;
        Account(String table) { this.table = table; }
    }
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
    private final Account account;

    public RuntimeBudgetBindingService(RuntimeBudgetConfiguration configuration, ProviderBudgetLedger ledger,
                                      DataSource dataSource, Clock clock) {
        this(configuration, ledger, dataSource, clock, Account.GENERATION);
    }

    public RuntimeBudgetBindingService(RuntimeBudgetConfiguration configuration, ProviderBudgetLedger ledger,
                                      DataSource dataSource, Clock clock, Account account) {
        this.configuration = Objects.requireNonNull(configuration);
        this.ledger = configuration.enabled() ? Objects.requireNonNull(ledger) : ledger;
        this.dataSource = configuration.enabled() ? Objects.requireNonNull(dataSource) : dataSource;
        this.clock = Objects.requireNonNull(clock);
        this.account = Objects.requireNonNull(account);
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

    public ProviderBudgetLedger.TopUpReceipt topUp(ProviderBudgetLedger.TopUpCommand command) {
        requireEnabled();
        if (command == null) throw new BudgetException(BudgetException.Code.INVALID_REQUEST);
        StoreReceipt current = transaction(connection -> binding(connection, false));
        if (current.state() != State.ACTIVE) throw failure(NOT_ACTIVE);
        requireMatch(current, command.approvalId(), command.storeUuid(), command.runtimeFingerprint());
        return ledger.topUp(readExact(current).approval(), command);
    }

    public Optional<ProviderBudgetLedger.TopUpReceipt> readTopUp(String topUpId) {
        requireEnabled();
        ProviderBudgetLedger.text(topUpId, 128);
        StoreReceipt current = transaction(connection -> binding(connection, false));
        if (current.state() == State.UNINITIALIZED || current.state() == State.INITIALIZING) return Optional.empty();
        readExact(current);
        var receipt = ledger.readTopUp(current.approvalId(), configuration.scope(), topUpId);
        receipt.ifPresent(value -> requireMatch(current, value.approvalId(), value.storeUuid(), value.runtimeFingerprint()));
        return receipt;
    }

    public RuntimeBudgetPricingAmendment.Receipt amendPricing(RuntimeBudgetPricingAmendment.Command command) {
        requireEnabled(); Objects.requireNonNull(command);
        if (account != Account.GENERATION || !configuration.scope().rateCardReference().equals(command.originalRateCardReference()))
            throw failure(ALLOCATION_SCOPE_MISMATCH);
        var current = readStore();
        if (current.binding().state() != State.ACTIVE) throw failure(NOT_ACTIVE);
        requireMatch(current.binding(), command.approvalId(), command.storeUuid(), command.runtimeFingerprint());
        var validUntil = ledger.effectiveExpiresAt(command.approvalId(), configuration.scope());
        String reasonHash = reasonHash(command.reason());
        return transaction(connection -> {
            var locked = binding(connection, true);
            requireMatch(locked, command.approvalId(), command.storeUuid(), command.runtimeFingerprint());
            if (locked.state() != State.ACTIVE) throw failure(NOT_ACTIVE);
            var existing = pricingAmendment(connection, locked);
            if (existing.isPresent()) {
                var receipt = existing.get();
                if (!receipt.amendmentId().equals(command.amendmentId())
                    || !receipt.authorizedBy().equals(command.authorizedBy())
                    || !receipt.approvalEvidenceRef().equals(command.approvalEvidenceRef())
                    || !receipt.reasonSha256().equals(reasonHash)) throw failure(PRICING_AMENDMENT_CONFLICT);
                return receipt;
            }
            if (current.accounting().stopped()) throw new BudgetException(STOPPED);
            if (!clock.instant().isBefore(validUntil)) throw new BudgetException(EXPIRED);
            var now = clock.instant();
            try (var insert = connection.prepareStatement("""
                INSERT INTO runtime_provider_budget_pricing_amendment
                (amendment_id,approval_id,store_uuid,runtime_fingerprint,original_rate_card_reference,
                 additional_rate_card_reference,authorized_by,approval_evidence_ref,reason_sha256,authorized_second,authorized_nano)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
                insert.setString(1, command.amendmentId()); insert.setString(2, locked.approvalId());
                insert.setString(3, locked.storeUuid().toString()); insert.setString(4, locked.runtimeFingerprint());
                insert.setString(5, command.originalRateCardReference()); insert.setString(6, command.additionalRateCardReference());
                insert.setString(7, command.authorizedBy()); insert.setString(8, command.approvalEvidenceRef());
                insert.setString(9, reasonHash); insert.setLong(10, now.getEpochSecond()); insert.setInt(11, now.getNano());
                insert.executeUpdate();
            }
            return pricingAmendment(connection, locked).orElseThrow(() -> failure(STORAGE_UNAVAILABLE));
        });
    }

    public Optional<RuntimeBudgetPricingAmendment.Receipt> pricingAmendment() {
        requireEnabled();
        if (account != Account.GENERATION || !ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE.equals(configuration.scope().rateCardReference()))
            return Optional.empty();
        var current = readStore();
        if (current.accounting() == null) return Optional.empty();
        return transaction(connection -> pricingAmendment(connection, current.binding()));
    }

    private Optional<RuntimeBudgetPricingAmendment.Receipt> pricingAmendment(Connection connection, StoreReceipt binding) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM runtime_provider_budget_pricing_amendment WHERE approval_id=?")) {
            query.setString(1, binding.approvalId());
            try (var row = query.executeQuery()) {
                if (!row.next()) return Optional.empty();
                var receipt = new RuntimeBudgetPricingAmendment.Receipt(1, row.getString("amendment_id"), row.getString("approval_id"),
                    UUID.fromString(row.getString("store_uuid")), row.getString("runtime_fingerprint"), row.getString("original_rate_card_reference"),
                    row.getString("additional_rate_card_reference"), row.getString("authorized_by"), row.getString("approval_evidence_ref"),
                    row.getString("reason_sha256"), java.time.Instant.ofEpochSecond(row.getLong("authorized_second"), row.getInt("authorized_nano")));
                requireMatch(binding, receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint());
                if (!configuration.scope().rateCardReference().equals(receipt.originalRateCardReference())
                    || !ReviewedOpenAiRateCard.ASTRA_REFERENCE.equals(receipt.additionalRateCardReference())) throw failure(BINDING_CONFLICT);
                return Optional.of(receipt);
            }
        } catch (IllegalArgumentException corrupt) { throw failure(STORAGE_UNAVAILABLE); }
    }

    private static String reasonHash(String reason) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reason.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA_256_UNAVAILABLE"); }
    }

    /** Host must resolve verified auth first. Every HTTP retry receives a fresh server attempt. */
    public ChatReceipt recordVerifiedChatIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "chat:query");
    }

    /** A managed task consumes one existing request allowance, with no unrelated chat grant. */
    public ChatReceipt recordVerifiedTaskIngress(RuntimeResolvedIdentity identity, String taskCode) {
        if (taskCode == null || taskCode.length() > 40 || !taskCode.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("A stable managed task code is required.");
        return recordVerifiedInteractiveIngress(identity, "task:" + taskCode + ":execute");
    }

    /** Queries and suggestions share the allocation's nonrefundable interactive-request ceiling. */
    public ChatReceipt recordVerifiedSuggestionsIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "chat:suggestions");
    }

    /** Dedicated authoring service calls consume the same nonrefundable interactive ceiling. */
    public ChatReceipt recordVerifiedAuthoringIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "configuration:author");
    }

    public ChatReceipt recordVerifiedDocumentEvaluationIngress(RuntimeResolvedIdentity identity) {
        return recordVerifiedInteractiveIngress(identity, "documents:evaluate");
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

    private StoreReceipt binding(Connection connection, boolean lock) throws SQLException {
        try (var query = connection.prepareStatement("SELECT binding_state, store_uuid, approval_id, allocation_fingerprint"
            + " FROM " + account.table + " WHERE singleton_id = 1" + (lock ? " FOR UPDATE" : ""));
             var rows = query.executeQuery()) {
            if (!rows.next()) throw failure(STORAGE_UNAVAILABLE);
            String uuid = rows.getString("store_uuid");
            try {
                return new StoreReceipt(State.valueOf(rows.getString("binding_state")), uuid == null ? null : UUID.fromString(uuid),
                    rows.getString("approval_id"), rows.getString("allocation_fingerprint"));
            } catch (IllegalArgumentException corrupt) { throw failure(STORAGE_UNAVAILABLE); }
        }
    }

    private void write(Connection connection, StoreReceipt receipt) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE " + account.table + " SET binding_state = ?, store_uuid = ?,"
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
