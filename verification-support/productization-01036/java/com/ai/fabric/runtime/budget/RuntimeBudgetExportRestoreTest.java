package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.AttemptState;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Standalone opt-in synthetic export/restore; intentionally outside runtime image watch paths. */
@EnabledIfEnvironmentVariable(named = "RUN_01036_LOCAL_BUDGET_RESTORE", matches = "true")
public class RuntimeBudgetExportRestoreTest {
    private static final String POSTGRES = "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea";
    private static final String RUNTIME = "ghcr.io/mahmoudashraf/ai-fabric-runtime@sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28";
    private static final String SOURCE_DB = "budget_export_source";
    private static final String RESTORED_DB = "budget_restore_destination";
    private static final String ARCHIVE = "/tmp/synthetic-budget-export.dump";
    private static final String USER = "synthetic_budget_test";
    private static final String OWNER_LABEL = "pro.loomai.local-budget-restore";

    @Test
    @Timeout(120)
    void dumpRestoreIntoDifferentDatabasePreservesActiveIdentityAndUnknownHolds() throws Exception {
        assertThat(System.getenv("RUN_01036_LOCAL_BUDGET_RESTORE")).isEqualTo("true");
        assertThat(System.getenv("TESTCONTAINERS_RYUK_DISABLED")).isEqualTo("true");
        assertThat(System.getenv("TESTCONTAINERS_CHECKS_DISABLE")).isEqualTo("true");
        assertThat(System.getenv("DOCKER_HOST")).matches("unix:/+[^\\r\\n\\x00?#]+");
        String runId = System.getenv("BUDGET_RESTORE_RUN_ID");
        assertThat(runId).matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
        var docker = DockerClientFactory.instance().client();
        assertThat(docker.inspectImageCmd(POSTGRES).exec().getId())
            .isEqualTo("sha256:81bd698b4594e751a3269e4dcd3e03a4a0ec0daf7b72e7aa1abd43cce9887542");
        assertThat(docker.inspectImageCmd(RUNTIME).exec().getId())
            .isEqualTo("sha256:eef208985b497d519004dbe04a0250270ac0231916cbf100a94432c2c20817a7");
        try (var network = Network.builder().createNetworkCmdModifier(command -> command.withInternal(true)
                 .withLabels(Map.of(OWNER_LABEL, runId))).build();
             var postgres = new GenericContainer<>(DockerImageName.parse(POSTGRES)).withImagePullPolicy(ignored -> false)
                 .withLabel(OWNER_LABEL, runId)
                 .withNetwork(network).withNetworkAliases("budget-postgres").withReuse(false).withStartupAttempts(1)
                 .withEnv(Map.of("POSTGRES_DB", SOURCE_DB, "POSTGRES_USER", USER, "POSTGRES_PASSWORD", "synthetic-local-test-only"))
                 .withCommand("postgres", "-c", "shared_buffers=32MB", "-c", "max_connections=20")
                 .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                 .withStartupTimeout(Duration.ofSeconds(45))
                 .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,nosuid,nodev,size=384m", "/tmp", "rw,nosuid,nodev,size=64m"))
                 .withCreateContainerCmdModifier(command -> command.getHostConfig().withNanoCPUs(1_000_000_000L)
                     .withMemory(768L * 1024 * 1024).withMemorySwap(768L * 1024 * 1024).withPidsLimit(128L));
             var client = new GenericContainer<>(DockerImageName.parse(RUNTIME)).withImagePullPolicy(ignored -> false)
                 .withLabel(OWNER_LABEL, runId)
                 .withNetwork(network).withReuse(false).withStartupAttempts(1).withStartupTimeout(Duration.ofSeconds(15))
                 .withCommand("-c", "sleep 120")
                 .withTmpFs(Map.of("/tmp", "rw,nosuid,nodev,size=64m,mode=1777"))
                 .withCreateContainerCmdModifier(command -> {
                     command.withEntrypoint("/bin/sh").withUser("10001:0");
                     command.getHostConfig().withNanoCPUs(1_000_000_000L)
                         .withMemory(512L * 1024 * 1024).withMemorySwap(512L * 1024 * 1024).withPidsLimit(128L);
                 })) {
            String workerResource = Worker.class.getName().replace('.', '/') + ".class";
            try (var input = getClass().getClassLoader().getResourceAsStream(workerResource)) {
                assertThat(input).isNotNull();
                client.withCopyToContainer(Transferable.of(input.readAllBytes(), 0444), "/proof/" + workerResource);
            }
            var assertions = Path.of(org.assertj.core.api.Assertions.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            client.withCopyFileToContainer(MountableFile.forHostPath(assertions, 0444), "/proof/assertj.jar");
            postgres.start(); client.start();
            assertThat(docker.inspectNetworkCmd().withNetworkId(network.getId()).exec().getInternal()).isTrue();
            for (var owned : List.of(postgres, client)) {
                var inspected = docker.inspectContainerCmd(owned.getContainerId()).exec();
                assertThat(inspected.getHostConfig().getPortBindings().getBindings()).isEmpty();
                assertThat(inspected.getHostConfig().getBinds()).isNullOrEmpty();
                assertThat(inspected.getNetworkSettings().getNetworks()).hasSize(1);
                assertThat(inspected.getConfig().getLabels()).containsEntry(OWNER_LABEL, runId);
            }
            String prepared = worker(client, "prepare");
            String checkpoint = prepared.lines().filter(line -> line.startsWith("CHECKPOINT=")).findFirst().orElseThrow()
                .substring("CHECKPOINT=".length());
            command(postgres, "pg_dump", "--username", USER, "--format=custom", "--no-owner", "--no-acl", "--file", ARCHIVE, SOURCE_DB);
            command(postgres, "pg_restore", "--list", ARCHIVE);
            var hash = postgres.execInContainer("sha256sum", ARCHIVE);
            assertThat(hash.getExitCode()).isZero();
            assertThat(hash.getStdout()).matches("[a-f0-9]{64}  " + ARCHIVE + "\\s*");
            // Drop the original before restoring so verification cannot accidentally read the original store.
            command(postgres, "psql", "--username", USER, "--dbname", "postgres", "--set", "ON_ERROR_STOP=1", "--command", "DROP DATABASE " + SOURCE_DB);
            command(postgres, "createdb", "--username", USER, RESTORED_DB);
            command(postgres, "pg_restore", "--username", USER, "--exit-on-error", "--no-owner", "--no-acl", "--dbname", RESTORED_DB, ARCHIVE);
            assertThat(worker(client, "verify", checkpoint)).contains("EXACT_RESTORED_STORE_VERIFIED");
            System.out.println("01036_LOCAL_RESTORE_PASS archiveSha256=" + hash.getStdout().substring(0, 64)
                + " exactImage=true exactStore=true active=true exactApproval=true unknownHolds=true scopeIsolation=6 schema=19 providerCalls=0");
        }
        assertThat(docker.listContainersCmd().withShowAll(true).withLabelFilter(Map.of(OWNER_LABEL, runId)).exec()).isEmpty();
    }

    private static String worker(GenericContainer<?> client, String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of("java", "-XX:ActiveProcessorCount=1", "-Xms64m", "-Xmx384m",
            "-Dloader.path=/proof", "-Dloader.main=" + Worker.class.getName(), "-cp", "/app/runtime.jar",
            "org.springframework.boot.loader.launch.PropertiesLauncher"));
        command.addAll(List.of(arguments));
        var result = client.execInContainer(command.toArray(String[]::new));
        assertThat(result.getExitCode()).withFailMessage("Synthetic runtime worker failed: %s", result.getStdout() + result.getStderr()).isZero();
        return result.getStdout();
    }

    private static void command(GenericContainer<?> postgres, String... arguments) throws Exception {
        assertThat(postgres.execInContainer(arguments).getExitCode()).as("Synthetic PostgreSQL command %s", arguments[0]).isZero();
    }

    public static void main(String[] arguments) throws Exception {
        assertThat(arguments).isEmpty();
        new RuntimeBudgetExportRestoreTest().dumpRestoreIntoDifferentDatabasePreservesActiveIdentityAndUnknownHolds();
        System.out.println("01036_LOCAL_RESTORE_CLEANUP_COMPLETE");
    }

    /** Short-lived client runs on the internal network, using production classes/resources from the exact image. */
    public static final class Worker {
        private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
        private static final UUID CHAT = UUID.fromString("00000000-0000-4000-8000-000000000001");
        private static final UUID AMBIGUOUS = UUID.fromString("00000000-0000-4000-8000-000000000002");
        private static final UUID RESERVED = UUID.fromString("00000000-0000-4000-8000-000000000003");
        private static final UUID SETTLED = UUID.fromString("00000000-0000-4000-8000-000000000004");

        public static void main(String[] arguments) throws Exception {
            assertThat(arguments.length).isBetween(1, 2);
            assertThat(arguments[0]).isIn("prepare", "verify");
            boolean prepare = "prepare".equals(arguments[0]);
            assertThat(arguments.length).isEqualTo(prepare ? 1 : 2);
            var dataSource = new DriverManagerDataSource("jdbc:postgresql://budget-postgres:5432/"
                + (prepare ? SOURCE_DB : RESTORED_DB) + "?connectTimeout=3&socketTimeout=10", USER, "synthetic-local-test-only");
            var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/runtime-migration").load();
            if (prepare) flyway.migrate(); else flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
            var scope = new Scope("synthetic-customer", "synthetic-deployment", "synthetic-target", "staging",
                ReviewedOpenAiRateCard.credentialIdentity("sk-local-no-send-not-a-provider-secret"), ReviewedOpenAiRateCard.REFERENCE);
            var ledger = new JdbcProviderBudgetLedger(dataSource, CLOCK);
            var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, scope, "synthetic-tenant"), ledger, dataSource, CLOCK);
            var approval = new Approval("synthetic-restore-approval", scope, new Limits(50, 200_000, 12_000_000_000L),
                CLOCK.instant().plusSeconds(3600).plusNanos(123456789));
            var chatHold = ReviewedOpenAiRateCard.chat(128_000, 300);
            var embeddingHold = ReviewedOpenAiRateCard.embedding(8192);
            var mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            if (prepare) {
                assertThat(service.readStore().binding().state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
                var initialized = service.initialize(new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval)));
                assertThat(service.readiness().ready()).isFalse();
                service.activate(new RuntimeBudgetBindingService.Activation(initialized.approvalId(), initialized.storeUuid(), initialized.runtimeFingerprint()));
                ledger.recordChatIngress(approval.id(), scope, CHAT);
                ledger.reserveAttempt(approval.id(), scope, AMBIGUOUS, chatHold);
                ledger.markAmbiguous(approval.id(), scope, AMBIGUOUS, "synthetic-unknown-no-provider-send");
                ledger.reserveAttempt(approval.id(), scope, RESERVED, embeddingHold);
                ledger.reserveAttempt(approval.id(), scope, SETTLED, embeddingHold);
                ledger.settle(approval.id(), scope, SETTLED, ReviewedOpenAiRateCard.embedding(100), "synthetic-local-fixture-only");
                assertThat(service.readiness().ready()).isTrue();
                System.out.println("CHECKPOINT=" + Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(service.readStore())));
                return;
            }
            var before = mapper.readValue(Base64.getUrlDecoder().decode(arguments[1]), RuntimeBudgetBindingService.StoreSnapshot.class);
            assertThat(service.readStore()).isEqualTo(before);
            assertThat(service.readiness().ready()).isTrue();
            assertThat(service.readStore().binding().state()).isEqualTo(RuntimeBudgetBindingService.State.ACTIVE);
            assertThat(service.requireActive().approval()).isEqualTo(approval);
            assertThat(service.readCurrent().chatAttempts()).isEqualTo(1);
            assertThat(service.readCurrent().providerAttempts()).isEqualTo(3);
            assertThat(ledger.recordChatIngress(approval.id(), scope, CHAT).newlyCounted()).isFalse();
            var ambiguousReplay = ledger.reserveAttempt(approval.id(), scope, AMBIGUOUS, chatHold);
            assertThat(ambiguousReplay.newlyReserved()).isFalse();
            assertThat(ambiguousReplay.attempt().state()).isEqualTo(AttemptState.AMBIGUOUS);
            assertThat(ambiguousReplay.attempt().upperBound()).isEqualTo(chatHold);
            var reservedReplay = ledger.reserveAttempt(approval.id(), scope, RESERVED, embeddingHold);
            assertThat(reservedReplay.newlyReserved()).isFalse();
            assertThat(reservedReplay.attempt().state()).isEqualTo(AttemptState.RESERVED);
            assertThat(ledger.reserveAttempt(approval.id(), scope, SETTLED, embeddingHold).attempt().state()).isEqualTo(AttemptState.SETTLED);
            assertThatThrownBy(() -> ledger.reserveAttempt(approval.id(), scope, UUID.randomUUID(), chatHold))
                .isInstanceOfSatisfying(BudgetException.class, failure -> assertThat(failure.code()).isEqualTo(BudgetException.Code.LIMIT_EXCEEDED));
            for (var foreign : List.of(
                new Scope("foreign", scope.deploymentId(), scope.targetProfileId(), scope.environment(), scope.credentialIdentity(), scope.rateCardReference()),
                new Scope(scope.customerId(), "foreign", scope.targetProfileId(), scope.environment(), scope.credentialIdentity(), scope.rateCardReference()),
                new Scope(scope.customerId(), scope.deploymentId(), "foreign", scope.environment(), scope.credentialIdentity(), scope.rateCardReference()),
                new Scope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), "production", scope.credentialIdentity(), scope.rateCardReference()),
                new Scope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(), "sha256:" + "f".repeat(64), scope.rateCardReference()),
                new Scope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(), scope.credentialIdentity(), "foreign"))) {
                assertThatThrownBy(() -> ledger.read(approval.id(), foreign)).isInstanceOfSatisfying(BudgetException.class,
                    failure -> assertThat(failure.code()).isEqualTo(BudgetException.Code.SCOPE_MISMATCH));
                var foreignRuntime = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, foreign, "synthetic-tenant"), ledger, dataSource, CLOCK);
                assertThat(foreignRuntime.readiness().ready()).isFalse();
                assertThat(foreignRuntime.readiness().code()).isEqualTo("SCOPE_MISMATCH");
            }
            var raised = new Approval(approval.id(), scope, new Limits(50, 200_001, 12_000_000_000L), approval.expiresAt());
            assertThatThrownBy(() -> service.initialize(new RuntimeBudgetAllocation(raised, RuntimeBudgetAllocation.fingerprint(raised))))
                .isInstanceOf(RuntimeBudgetBindingException.class);
            assertThat(service.readStore()).isEqualTo(before);
            var jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_budget_approval", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_database WHERE datname = ?", Long.class, SOURCE_DB)).isZero();
            System.out.println("EXACT_RESTORED_STORE_VERIFIED");
        }
    }
}
