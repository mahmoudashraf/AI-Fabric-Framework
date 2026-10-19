package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Explicit local integration gate: mvn -o -Dtest='*Test,LeadProviderIdentityPostgresIT' test. */
class LeadProviderIdentityPostgresIT {
    private static final String STOCK_ID = "PROVIDER-ONLY-99";
    private static LocalPostgres postgres;

    @BeforeAll
    static void startOwnedPostgres() throws Exception {
        postgres = LocalPostgres.start();
    }

    @AfterAll
    static void stopOwnedPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @Test
    void upgradesV3WithoutRewritingExistingReceiptsEncryptedDataOrWebsiteInventory() throws Exception {
        var data = postgres.newSchema();
        var jdbc = new JdbcTemplate(data);
        var v3 = Flyway.configure().dataSource(data).locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("3")).load();
        assertThat(v3.migrate().migrationsExecuted).isEqualTo(3);
        var properties = properties();
        var encryption = encryption(properties);
        String encrypted = encryption.encrypt(Map.of("name", "Existing Buyer", "email", "existing@example.test", "phone", "+44 7700 900111"));
        insertLegacyInventory(jdbc);
        Instant createdAt = Instant.parse("2026-01-02T03:04:05.123456Z");
        jdbc.update("""
            INSERT INTO dealership_lead_request
              (id,idempotency_key,action_type,vehicle_id,encrypted_contact,status,consent_recorded,source_session_id,receipt_code,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, "old-lead", "legacy-request", "dealership_request_test_drive", "legacy-website-id", encrypted,
            "CONTACTED", false, "session", "EXISTING-RECEIPT", java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        jdbc.update("INSERT INTO loomai_managed_dependency_operation (operation_key,request_sha256,response_json,created_at) VALUES (?,?,?,?)",
            "existing-operation", "a".repeat(64), "{\"preserved\":true}", java.sql.Timestamp.from(createdAt));
        var originalLead = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
        var originalInventory = jdbc.queryForMap("SELECT * FROM dealership_vehicle");
        var originalOperations = jdbc.queryForList("SELECT * FROM loomai_managed_dependency_operation");
        var originalChecksums = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3') ORDER BY version");

        assertThat(migrate(data).migrationsExecuted).isEqualTo(1);

        var migrated = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
        assertThat(migrated).containsAllEntriesOf(originalLead)
            .containsEntry("provider_stock_id", null).containsEntry("provider_advertiser_id", null);
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_vehicle")).isEqualTo(originalInventory);
        assertThat(jdbc.queryForList("SELECT * FROM loomai_managed_dependency_operation")).isEqualTo(originalOperations);
        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3') ORDER BY version"))
            .isEqualTo(originalChecksums);
        try (var provider = new StockProvider()) {
            var service = service(data, provider.client(), properties);
            var legacyRequest = new LeadService.ActionExecutionRequest("dealership_request_test_drive", "legacy-request", true,
                "legacy-website-id", "Existing Buyer", "existing@example.test", "+44 7700 900111", null, null, false, auth("session", "tenant"));
            var receipt = inTransaction(data, () -> service.execute(legacyRequest));
            assertThat(receipt.receiptCode()).isEqualTo("EXISTING-RECEIPT");
            assertThat(receipt.status()).isEqualTo("CONTACTED");
            assertThat(receipt.createdAt()).isEqualTo(createdAt);
            assertThat(service.detail("old-lead").contact()).containsEntry("email", "existing@example.test");
            assertThat(jdbc.queryForObject("SELECT encrypted_contact FROM dealership_lead_request WHERE id='old-lead'", String.class)).isEqualTo(encrypted);
            assertThat(provider.calls()).isZero();

            var fresh = inTransaction(data, () -> service.execute(request("upgraded-provider-request", "session", "tenant", true)));
            assertThat(fresh.vehicle()).isEqualTo("2025 Meridian E7");
            assertThat(inTransaction(data, () -> service.execute(request("upgraded-provider-request", "session", "tenant", true)))).isEqualTo(fresh);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM dealership_vehicle")).isEqualTo(originalInventory);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle WHERE id=? OR stock_id=?", Long.class, STOCK_ID, STOCK_ID)).isZero();
        assertThat(migrate(data).migrationsExecuted).isZero();
    }

    @Test
    void freshPostgresPersistsCanonicalIdentityAndEncryptedSnapshotThenReplaysAfterServiceRestart() throws Exception {
        var data = postgres.newSchema();
        assertThat(migrate(data).migrationsExecuted).isEqualTo(4);
        var jdbc = new JdbcTemplate(data);
        var properties = properties();
        try (var provider = new StockProvider()) {
            var service = service(data, provider.client(), properties);
            var request = request("provider-request", "session", "tenant", true);
            var first = inTransaction(data, () -> service.execute(request));
            var record = new LeadRepository(jdbc).findByIdempotencyKey("provider-request").orElseThrow();
            assertThat(record.vehicleId()).isEqualTo(STOCK_ID);
            assertThat(record.providerStockId()).isEqualTo(STOCK_ID);
            assertThat(record.providerAdvertiserId()).isEqualTo("advertiser-1");
            assertThat(record.encryptedContact()).startsWith("v1.").doesNotContain("New Buyer", "new@example.test", "_providerStock");
            Map<String, Object> privateData = encryption(properties).decrypt(record.encryptedContact());
            assertThat(privateData).containsEntry("email", "new@example.test");
            assertThat(privateData.get("_providerStock")).isInstanceOfSatisfying(Map.class, snapshot -> {
                assertThat(snapshot.get("stockId")).isEqualTo(STOCK_ID);
                assertThat(snapshot.get("advertiserId")).isEqualTo("advertiser-1");
                assertThat(snapshot.get("displayName")).isEqualTo("2025 Meridian E7");
            });
            var beforeReplay = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
            int calls = provider.calls();
            provider.responseStatus = 503;

            var restarted = service(data, provider.client(), properties);
            assertThat(inTransaction(data, () -> restarted.execute(request))).isEqualTo(first);
            assertThat(provider.calls()).isEqualTo(calls);
            assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request")).isEqualTo(beforeReplay);
            assertThat(restarted.detail(record.id()).contact()).doesNotContainKey("_providerStock");
            assertThat(restarted.latest(10).getFirst().vehicle()).isEqualTo("2025 Meridian E7");

            assertThatThrownBy(() -> inTransaction(data, () -> restarted.execute(request("provider-request", "other-session", "tenant", true))))
                .hasMessageContaining("different confirmed request");
            assertThatThrownBy(() -> inTransaction(data, () -> restarted.execute(request("provider-request", "session", "other-tenant", true))))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> inTransaction(data, () -> restarted.execute(request("provider-request", "session", "tenant", false))))
                .hasMessageContaining("Confirmed action evidence is required");
            var otherAdvertiser = service(data, new ProviderStockClient(provider.baseUrl(), "key", "secret", "other-advertiser", Duration.ofSeconds(2)), properties);
            assertThatThrownBy(() -> inTransaction(data, () -> otherAdvertiser.execute(request)))
                .hasMessageContaining("different confirmed request");
            assertThat(provider.calls()).isEqualTo(calls);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isEqualTo(1);
        }
    }

    @Test
    void providerAndStoreFailuresRemainConfidentialAndCannotCreateLeadsOrWebsiteStock() throws Exception {
        var data = postgres.newSchema();
        migrate(data);
        var jdbc = new JdbcTemplate(data);
        try (var provider = new StockProvider()) {
            var service = service(data, provider.client(), properties());
            provider.responseStatus = 503;
            assertThatThrownBy(() -> inTransaction(data, () -> service.execute(request("unavailable", "session", "tenant", true))))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getMessage()).doesNotContain("secret", "new@example.test", "New Buyer");
                });
            provider.responseStatus = 200;
            provider.returnedAdvertiser = "another-advertiser";
            assertThatThrownBy(() -> inTransaction(data, () -> service.execute(request("foreign-stock", "session", "tenant", true))))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
            provider.returnedAdvertiser = "advertiser-1";
            jdbc.execute("ALTER TABLE dealership_lead_request ADD CONSTRAINT test_reject_new CHECK (status = 'TEST_REJECT')");
            assertThatThrownBy(() -> inTransaction(data, () -> service.execute(request("store-reject", "session", "tenant", true))))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getReason()).isEqualTo("The dealership lead store is temporarily unavailable.");
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).doesNotContain("secret", "new@example.test", "New Buyer", "test_reject_new", "INSERT");
                });
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
    }

    @Test
    void postgresRejectsPartialProviderIdentityWithoutWeakeningLegacyReceiptStorage() {
        var data = postgres.newSchema();
        migrate(data);
        var jdbc = new JdbcTemplate(data);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO dealership_lead_request
              (id,idempotency_key,action_type,vehicle_id,provider_stock_id,encrypted_contact,status,consent_recorded,receipt_code,created_at,updated_at)
            VALUES ('partial','partial','dealership_request_test_drive','PROVIDER-ONLY-99','PROVIDER-ONLY-99','ciphertext','NEW',false,'partial',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_READ_COMMITTED, Connection.TRANSACTION_REPEATABLE_READ})
    void simultaneousIdenticalRequestsRecoverTheCommittedWinnerAndPreserveItsEncryptedSnapshot(int defaultIsolation) throws Exception {
        var migrated = postgres.newSchema();
        migrate(migrated);
        var data = withDefaultIsolation(migrated, defaultIsolation);
        var jdbc = new JdbcTemplate(data);
        var properties = properties();
        var repository = spy(new LeadRepository(jdbc));
        try (var provider = new StockProvider()) {
            var service = transactionalService(data, repository, provider.client(), properties);
            var request = request("concurrent-identical", "session", "tenant", true);

            var attempts = race(data, provider, service, request, request);

            assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.failure()).isNull());
            var first = attempts.getFirst().receipt();
            assertThat(attempts.getLast().receipt()).isEqualTo(first);
            verify(repository, times(2)).insert(any());
            verify(repository, times(3)).findByIdempotencyKey(request.idempotencyKey());
            var persisted = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
            var record = repository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow();
            assertThat(record.receiptCode()).isEqualTo(first.receiptCode());
            assertThat(record.createdAt()).isEqualTo(first.createdAt());
            assertThat(encryption(properties).decrypt(record.encryptedContact()).get("_providerStock"))
                .isInstanceOfSatisfying(Map.class, snapshot -> assertThat(snapshot.get("displayName")).isEqualTo(first.vehicle()));
            assertThat(first.vehicle()).isIn("2025 Meridian-1 E7", "2025 Meridian-2 E7");
            assertThat(record.encryptedContact()).doesNotContain("new@example.test", "Meridian");
            provider.responseStatus = 503;
            assertThat(inTransaction(data, () -> service.execute(request))).isEqualTo(first);
            assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request")).isEqualTo(persisted);
            assertThat(provider.stockReads.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "phone", "date", "message", "session", "consent", "action"})
    void simultaneousDifferentPayloadsCommitOneRequestAndRejectTheOtherWithoutReplacingIt(String changed) throws Exception {
        var data = postgres.newSchema();
        migrate(data);
        var jdbc = new JdbcTemplate(data);
        var properties = properties();
        var repository = spy(new LeadRepository(jdbc));
        try (var provider = new StockProvider()) {
            var service = transactionalService(data, repository, provider.client(), properties);
            var original = request("concurrent-conflict", "session", "tenant", true);
            var other = new LeadService.ActionExecutionRequest(
                changed.equals("action") ? "dealership_request_callback" : original.actionId(), original.idempotencyKey(), true,
                original.vehicleReference(), changed.equals("name") ? "Other Buyer" : original.name(),
                changed.equals("email") ? "other@example.test" : original.email(),
                changed.equals("phone") ? "+44 7700 900456" : original.phone(), changed.equals("date") ? "2026-11-01" : null,
                changed.equals("message") ? "Different confirmed instructions" : null, changed.equals("consent") || changed.equals("action"),
                changed.equals("session") ? auth("other-session", "tenant") : original.authContext());

            var attempts = race(data, provider, service, original, other);

            verify(repository, times(2)).insert(any());
            verify(repository, times(3)).findByIdempotencyKey(original.idempotencyKey());
            assertThat(attempts.stream().filter(attempt -> attempt.receipt() != null)).hasSize(1);
            assertThat(attempts.stream().filter(attempt -> attempt.failure() != null)).hasSize(1);
            var winner = attempts.stream().filter(attempt -> attempt.receipt() != null).findFirst().orElseThrow();
            var loser = attempts.stream().filter(attempt -> attempt.failure() != null).findFirst().orElseThrow();
            assertThat(loser.failure().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(loser.failure().getReason()).isEqualTo("The idempotency key belongs to a different confirmed request.");
            assertThat(loser.failure().getMessage()).doesNotContain("new@example.test", "other@example.test", "Other Buyer");
            var persisted = jdbc.queryForMap("SELECT * FROM dealership_lead_request");
            var record = repository.findByIdempotencyKey(original.idempotencyKey()).orElseThrow();
            var privateData = encryption(properties).decrypt(record.encryptedContact());
            assertThat(record.sourceSessionId()).isEqualTo(winner.request().authContext().sessionId());
            assertThat(record.actionType()).isEqualTo(winner.request().actionId());
            assertThat(record.consentRecorded()).isEqualTo(winner.request().consent());
            assertThat(privateData.get("name")).isEqualTo(winner.request().name());
            assertThat(privateData.get("email")).isEqualTo(winner.request().email());
            assertThat(privateData.get("phone")).isEqualTo(winner.request().phone());
            assertThat(privateData.get("preferredDate")).isEqualTo(winner.request().preferredDate());
            assertThat(privateData.get("message")).isEqualTo(winner.request().message());
            assertThat(privateData.get("_providerStock")).isInstanceOfSatisfying(Map.class,
                snapshot -> assertThat(snapshot.get("displayName")).isEqualTo(winner.receipt().vehicle()));
            provider.responseStatus = 503;
            assertThat(inTransaction(data, () -> service.execute(winner.request()))).isEqualTo(winner.receipt());
            assertThatThrownBy(() -> inTransaction(data, () -> service.execute(loser.request())))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            assertThat(jdbc.queryForMap("SELECT * FROM dealership_lead_request")).isEqualTo(persisted);
            assertThat(provider.stockReads.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "receipt"})
    void unrelatedUniqueViolationsAreNotHiddenEvenWhenAnIdempotencyWinnerExists(String collision) throws Exception {
        var data = postgres.newSchema();
        migrate(data);
        var jdbc = new JdbcTemplate(data);
        var properties = properties();
        try (var provider = new StockProvider()) {
            var service = service(data, provider.client(), properties);
            inTransaction(data, () -> service.execute(request("original", "session", "tenant", true)));
            inTransaction(data, () -> service.execute(request("contended-key", "session", "tenant", true)));
            var original = new LeadRepository(jdbc).findByIdempotencyKey("original").orElseThrow();
            var before = jdbc.queryForList("SELECT * FROM dealership_lead_request ORDER BY id");
            var initialLookup = new AtomicBoolean(true);
            var conflicting = new LeadRepository(jdbc) {
                @Override public Optional<LeadRecord> findByIdempotencyKey(String key) {
                    return initialLookup.getAndSet(false) ? Optional.empty() : super.findByIdempotencyKey(key);
                }
                @Override public LeadRecord insert(LeadRecord input) {
                    return super.insert(new LeadRecord(collision.equals("id") ? original.id() : input.id(), input.idempotencyKey(),
                        input.actionType(), input.vehicleId(), input.encryptedContact(), input.status(), input.consentRecorded(),
                        input.sourceSessionId(), collision.equals("receipt") ? original.receiptCode() : input.receiptCode(),
                        input.createdAt(), input.updatedAt(), input.providerStockId(), input.providerAdvertiserId()));
                }
            };
            var failing = new LeadService(conflicting, provider.client(), encryption(properties), properties);

            assertThatThrownBy(() -> inTransaction(data, () -> failing.execute(request("contended-key", "session", "tenant", true))))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getReason()).isEqualTo("The dealership lead store is temporarily unavailable.");
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).doesNotContain("new@example.test", "INSERT", "constraint", original.receiptCode());
                });
            assertThat(jdbc.queryForList("SELECT * FROM dealership_lead_request ORDER BY id")).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class)).isZero();
        }
    }

    private static List<Attempt> race(DriverManagerDataSource data, StockProvider provider, LeadService service,
                                      LeadService.ActionExecutionRequest first, LeadService.ActionExecutionRequest second) throws Exception {
        provider.blockStockReads();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> attempt(data, service, first));
            var right = workers.submit(() -> attempt(data, service, second));
            try {
                assertThat(provider.stockRequestsArrived.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(new JdbcTemplate(data).queryForObject("SELECT COUNT(*) FROM dealership_lead_request", Long.class)).isZero();
                provider.releaseStockRequests.countDown();
                return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
            } finally { provider.releaseStockRequests.countDown(); }
        }
    }

    private static Attempt attempt(DriverManagerDataSource data, LeadService service, LeadService.ActionExecutionRequest request) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try { return new Attempt(request, service.execute(request), null); }
        catch (ResponseStatusException failure) { return new Attempt(request, null, failure); }
        finally { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    }

    private record Attempt(LeadService.ActionExecutionRequest request, LeadService.LeadReceipt receipt, ResponseStatusException failure) { }

    private static LeadService transactionalService(DriverManagerDataSource data, LeadRepository repository,
                                                     ProviderStockClient provider, DealershipDemoProperties properties) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(data));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(new LeadService(repository, provider, encryption(properties), properties));
        factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
        var service = (LeadService) factory.getProxy();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(new JdbcTemplate(data).queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            return call.callRealMethod();
        }).when(repository).insert(any());
        return service;
    }

    private static DriverManagerDataSource withDefaultIsolation(DriverManagerDataSource original, int isolation) {
        return new DriverManagerDataSource(original.getUrl(), original.getUsername(), original.getPassword()) {
            @Override public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                connection.setTransactionIsolation(isolation);
                return connection;
            }
        };
    }

    private static org.flywaydb.core.api.output.MigrateResult migrate(DriverManagerDataSource data) {
        var flyway = Flyway.configure().dataSource(data).locations("classpath:db/migration").load();
        var result = flyway.migrate();
        flyway.validate();
        return result;
    }

    private static DealershipDemoProperties properties() {
        var properties = new DealershipDemoProperties();
        var access = properties.getRuntime().getPrivateAccess();
        access.setCustomerId("customer"); access.setTenantId("tenant"); access.setDeploymentId("deployment");
        properties.getPrivacy().setEncryptionKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }

    private static PiiEncryptionService encryption(DealershipDemoProperties properties) {
        return new PiiEncryptionService(properties, new ObjectMapper());
    }

    private static LeadService service(DriverManagerDataSource data, ProviderStockClient provider, DealershipDemoProperties properties) {
        return new LeadService(new LeadRepository(new JdbcTemplate(data)), provider, encryption(properties), properties);
    }

    private static <T> T inTransaction(DriverManagerDataSource data, java.util.function.Supplier<T> body) {
        return new TransactionTemplate(new DataSourceTransactionManager(data)).execute(status -> body.get());
    }

    private static LeadService.ActionExecutionRequest request(String key, String session, String tenant, boolean confirmed) {
        return new LeadService.ActionExecutionRequest("dealership_request_test_drive", key, confirmed, STOCK_ID,
            "New Buyer", "new@example.test", "+44 7700 900123", null, null, false, auth(session, tenant));
    }

    private static LeadService.VerifiedAuthContext auth(String session, String tenant) {
        return new LeadService.VerifiedAuthContext("subject", "ANONYMOUS_SESSION", "PUBLIC_RUNTIME_TOKEN", "PUBLIC_BROWSER",
            session, "deployment", "customer", tenant, "runtime", List.of());
    }

    private static void insertLegacyInventory(JdbcTemplate jdbc) {
        jdbc.execute("""
            INSERT INTO dealership_vehicle
              (id,stock_id,slug,make_name,model_name,derivative,registration_year,price_minor,currency,mileage,
               fuel_type,transmission,body_type,exterior_colour,doors,seats,location_name,lifecycle_state,summary,
               features_json,image_path,source_label,source_updated_at,source_version,created_at,updated_at)
            VALUES ('legacy-website-id','legacy-website-stock','legacy-website-slug','Legacy','Example','Existing',2020,
              1000000,'GBP',10000,'Petrol','Manual','Hatchback','Blue',5,5,'Existing showroom','ACTIVE','Existing display inventory',
              '[]','/legacy.webp','Customer website',TIMESTAMP '2026-01-01 00:00:00',1,TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00')
            """);
    }

    private static final class StockProvider implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService httpWorkers = Executors.newCachedThreadPool();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger stockReads = new AtomicInteger();
        private volatile int responseStatus = 200;
        private volatile String returnedAdvertiser = "advertiser-1";
        private volatile CountDownLatch stockRequestsArrived;
        private volatile CountDownLatch releaseStockRequests;

        private StockProvider() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(httpWorkers);
            server.createContext("/authenticate", exchange -> {
                calls.incrementAndGet();
                String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int status = "POST".equals(exchange.getRequestMethod()) && "key=key&secret=secret".equals(form) ? 200 : 401;
                byte[] body = ("{\"access_token\":\"fixture-token\",\"expires_at\":\"" + Instant.now().plusSeconds(3600) + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.createContext("/stock", exchange -> {
                calls.incrementAndGet();
                boolean authorized = "Bearer fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
                boolean scoped = ("advertiserId=advertiser-1&stockId=" + STOCK_ID + "&page=1&pageSize=2")
                    .equals(exchange.getRequestURI().getRawQuery());
                int status = authorized && scoped ? responseStatus : 403;
                int read = stockReads.incrementAndGet();
                if (stockRequestsArrived != null && authorized && scoped) {
                    stockRequestsArrived.countDown();
                    try {
                        if (!releaseStockRequests.await(12, TimeUnit.SECONDS)) throw new IOException("Provider barrier was not released.");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Provider barrier was interrupted.", failure);
                    }
                }
                ObjectNode body = new ObjectMapper().createObjectNode().put("totalResults", 1);
                ObjectNode stock = body.putArray("results").addObject();
                stock.putObject("advertiser").put("advertiserId", returnedAdvertiser);
                stock.putObject("metadata").put("stockId", STOCK_ID).put("lifecycleState", "FORECOURT")
                    .put("lastUpdated", Instant.now().minusSeconds(60).toString());
                stock.putObject("vehicle").put("yearOfManufacture", 2025)
                    .put("make", stockRequestsArrived == null ? "Meridian" : "Meridian-" + read).put("model", "E7");
                stock.putObject("adverts").putNull("reservationStatus").putObject("retailAdverts")
                    .putObject("advertiserAdvert").put("status", "PUBLISHED");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
        }

        private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        private int calls() { return calls.get(); }
        private ProviderStockClient client() { return new ProviderStockClient(baseUrl(), "key", "secret", "advertiser-1", Duration.ofSeconds(10)); }
        private void blockStockReads() { releaseStockRequests = new CountDownLatch(1); stockRequestsArrived = new CountDownLatch(2); }
        @Override public void close() throws InterruptedException {
            if (releaseStockRequests != null) releaseStockRequests.countDown();
            server.stop(0); httpWorkers.shutdownNow();
            if (!httpWorkers.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("Owned provider fixture did not stop.");
        }
    }

    private static final class LocalPostgres implements AutoCloseable {
        private final Path directory;
        private final Process process;
        private final int port;
        private final String password;

        private LocalPostgres(Path directory, Process process, int port, String password) {
            this.directory = directory; this.process = process; this.port = port; this.password = password;
        }

        private static LocalPostgres start() throws Exception {
            Path directory = Files.createTempDirectory(Path.of("/tmp"), "dealer-provider-pg-");
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            String binaryDirectory = System.getProperty("dealership.test.postgres-bin", "/usr/local/bin");
            Path passwordFile = directory.resolve("password");
            String password = UUID.randomUUID().toString();
            Files.writeString(passwordFile, password, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(passwordFile, PosixFilePermissions.fromString("rw-------"));
            Process process = null;
            try {
                var initialize = new ProcessBuilder(Path.of(binaryDirectory, "initdb").toString(), "-D", directory.resolve("data").toString(),
                    "-U", "demo_fixture", "--auth=scram-sha-256", "--pwfile=" + passwordFile, "--no-locale", "--encoding=UTF8")
                    .redirectErrorStream(true).redirectOutput(directory.resolve("init.log").toFile()).start();
                if (!initialize.waitFor(30, TimeUnit.SECONDS) || initialize.exitValue() != 0) {
                    initialize.destroyForcibly();
                    throw new IllegalStateException("The isolated local PostgreSQL cluster could not be initialized.");
                }
                int port;
                try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
                process = new ProcessBuilder(Path.of(binaryDirectory, "postgres").toString(), "-D", directory.resolve("data").toString(),
                    "-p", Integer.toString(port), "-h", "127.0.0.1", "-k", directory.toString(), "-F",
                    "-c", "shared_buffers=16MB", "-c", "max_connections=12", "-c", "log_min_error_statement=PANIC")
                    .redirectErrorStream(true).redirectOutput(directory.resolve("server.log").toFile()).start();
                var fixture = new LocalPostgres(directory, process, port, password);
                for (int attempt = 0; attempt < 100 && process.isAlive(); attempt++) {
                    try (Connection connection = fixture.dataSource().getConnection()) {
                        try (var query = connection.createStatement(); var result = query.executeQuery("SHOW data_directory")) {
                            result.next();
                            if (!Path.of(result.getString(1)).toRealPath().equals(directory.resolve("data").toRealPath())) {
                                throw new IllegalStateException("PostgreSQL ownership verification failed.");
                            }
                        }
                        return fixture;
                    } catch (java.sql.SQLException notReady) { Thread.sleep(100); }
                }
                throw new IllegalStateException("The isolated local PostgreSQL cluster did not become ready.");
            } catch (Exception failure) {
                new LocalPostgres(directory, process, 0, password).close();
                throw failure;
            }
        }

        private DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=2&socketTimeout=5",
                "demo_fixture", password);
        }

        private DriverManagerDataSource newSchema() {
            String schema = "provider_test_" + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(dataSource()).execute("CREATE SCHEMA " + schema);
            return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=2&socketTimeout=5&currentSchema=" + schema,
                "demo_fixture", password);
        }

        @Override public void close() throws Exception {
            if (process != null && process.isAlive()) {
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("Owned local PostgreSQL process did not stop.");
                }
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
