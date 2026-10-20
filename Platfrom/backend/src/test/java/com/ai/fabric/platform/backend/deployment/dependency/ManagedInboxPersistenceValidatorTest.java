package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.*;
import static org.assertj.core.api.Assertions.*;

class ManagedInboxPersistenceValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    private ManagedDependencySpec template;

    @BeforeEach void reviewedTemplate() throws Exception {
        template = mapper.readValue(Files.readString(Path.of(
            "../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json")),
            ManagedDependencySpec.class);
    }

    @Test void currentStagingTemplateKeepsItsDefaultFilePathAndFingerprint() {
        String fingerprint = validator.fingerprint(template);
        assertThat(template.services().getFirst().database()).isNull();
        assertThat(template.services().getFirst().environment()).doesNotContainKey("BASIC_INBOX_JDBC_URL");
        validator.validate(template);
        assertThat(validator.fingerprint(template)).isEqualTo(fingerprint);
    }

    @ParameterizedTest @ValueSource(strings = {"/app/data", "/app", "/app/data/"})
    void defaultFileMustBeInsideTheDeclaredMount(String mount) {
        assertThatNoException().isThrownBy(() -> validator.validate(file(null, mount)));
    }

    @ParameterizedTest @ValueSource(strings = {"/unrelated", "/app/database", "/app/data/basic-inbox"})
    void anUnrelatedOrFileNamedVolumeDoesNotProvePersistence(String mount) {
        assertThatThrownBy(() -> validator.validate(file(null, mount)))
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @Test void anEmptyVolumeListStillRejectsWithTheExistingFailure() {
        assertThatThrownBy(() -> validator.validate(with(Map.of(), List.of(), null, template.services().getFirst().secretPurposes())))
            .hasMessageContaining("INBOX_PERSISTENCE_MISSING");
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        "jdbc:h2:file:./data/other-inbox|/app/data",
        "jdbc:h2:file:/app/data/other-inbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH|/app/data",
        "jdbc:h2:file:/state/inbox/database|/state/inbox",
        "jdbc:h2:file:/app/data/nested/database|/app/data",
        "jdbc:h2:file:/app/data/./database|/app/data"
    })
    void explicitSupportedFileUrlsAreCheckedAgainstTheirOwnMount(String url, String mount) {
        assertThatNoException().isThrownBy(() -> validator.validate(file(url, mount)));
    }

    @ParameterizedTest @ValueSource(strings = {
        "jdbc:h2:file:/tmp/inbox", "jdbc:h2:file:./elsewhere/inbox", "jdbc:h2:file:/app/data-other/inbox",
        "jdbc:h2:file:/app/data", "jdbc:h2:file:/app/data/../outside/inbox"
    })
    void explicitFileUrlCannotFallBackToTheMountedDefault(String url) {
        assertThatThrownBy(() -> validator.validate(file(url, "/app/data")))
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @ParameterizedTest @ValueSource(strings = {
        "", "jdbc:h2:mem:inbox", "jdbc:h2:tcp://host/inbox", "jdbc:postgresql://host/inbox",
        "jdbc:h2:./data/inbox", "jdbc:h2:file:~/inbox", "jdbc:h2:file:../inbox",
        "jdbc:h2:file:C:/inbox", "jdbc:h2:file:/app/data/inbox;INIT=RUNSCRIPT FROM 'other.sql'",
        "jdbc:h2:file:/app/data/inbox;AUTO_SERVER=TRUE", "jdbc:h2:file:/app/data/inbox;",
        "jdbc:h2:file:/app/data/inbox;MODE=PostgreSQL;MODE=PostgreSQL",
        "jdbc:h2:file:/app/data/", "jdbc:h2:file:/app/data/."
    })
    void unsupportedOrExecutableJdbcFormsCannotClaimAProvenFilePath(String url) {
        assertThatThrownBy(() -> validator.validate(file(url, "/app/data")))
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @Test void invalidMountSyntaxFailsWithTheTypedPersistenceFailure() {
        assertThatThrownBy(() -> validator.validate(file(null, "/app/data\0")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @ParameterizedTest @ValueSource(strings = {"SECRET", "TARGET_VALUE"})
    void runtimeResolvedJdbcValuesCannotClaimStaticMountCoverage(String kind) {
        Value value = new Value(ValueKind.valueOf(kind), kind.equals("SECRET") ? "api" : "runtime-base-url");
        assertThatThrownBy(() -> validator.validate(with(Map.of("BASIC_INBOX_JDBC_URL", value),
            volumes("/app/data"), null, template.services().getFirst().secretPurposes())))
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @ParameterizedTest @ValueSource(strings = {
        "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_HIKARI_JDBC_URL", "SPRING_FLYWAY_URL",
        "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_ADDITIONAL_LOCATION",
        "SPRING_CONFIG_IMPORT", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"
    })
    void alternateConfigurationCannotOverrideTheProvenFileDatasource(String name) {
        assertThatThrownBy(() -> validator.validate(with(Map.of(name, new Value(ValueKind.LITERAL, "unproven-override")),
            volumes("/app/data"), null, template.services().getFirst().secretPurposes())))
            .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @Test void documentedMemoryTuningDoesNotChangeTheProvenPath() {
        assertThatNoException().isThrownBy(() -> validator.validate(with(
            Map.of("JAVA_OPTS", new Value(ValueKind.LITERAL, "-Xms256m -Xmx768m")),
            volumes("/app/data"), null, template.services().getFirst().secretPurposes())));
    }

    @Test void jvmDatasourceOrWorkingDirectoryOverridesAreNotMemoryTuning() {
        for (String value : List.of("-Dspring.datasource.url=jdbc:h2:mem:inbox", "-Duser.dir=/tmp"))
            assertThatThrownBy(() -> validator.validate(with(Map.of("JAVA_OPTS", new Value(ValueKind.LITERAL, value)),
                volumes("/app/data"), null, template.services().getFirst().secretPurposes())))
                .hasMessageContaining("INBOX_FILE_PERSISTENCE_UNPROVEN");
    }

    @Test void theExistingOwnedPostgresContractDoesNotRequireAnApplicationFileVolume() {
        var purposes = new java.util.HashSet<>(template.services().getFirst().secretPurposes()); purposes.add("db");
        var database = new Database("registry.example.test/postgres@sha256:" + "a".repeat(64), "db", new Capacity(250, 512, 4096, 1));
        var spec = with(Map.of(
            "BASIC_INBOX_JDBC_URL", new Value(ValueKind.DATABASE_JDBC, null),
            "BASIC_INBOX_JDBC_USERNAME", new Value(ValueKind.DATABASE_USERNAME, null),
            "BASIC_INBOX_JDBC_PASSWORD", new Value(ValueKind.SECRET, "db")), List.of(), database, purposes);
        assertThatNoException().isThrownBy(() -> validator.validate(spec));
    }

    @Test void otherServiceContractsDoNotAcquireInboxFileRules() {
        var original = template.services().getFirst();
        var environment = new TreeMap<>(original.environment()); environment.remove("BASIC_INBOX_OPERATORS");
        var service = new Service(original.slot(), Contract.HTTP_SERVICE, original.image(), original.sourceArtifactId(),
            original.sourceCommit(), original.port(), original.healthPath(), original.capacity(), original.secretPurposes(),
            environment, null, volumes("/unrelated"), original.dependsOn(), original.initializationContract());
        assertThatNoException().isThrownBy(() -> validator.validate(new ManagedDependencySpec(template.schemaVersion(), List.of(service))));
    }

    private ManagedDependencySpec file(String url, String mount) {
        return with(url == null ? Map.of() : Map.of("BASIC_INBOX_JDBC_URL", new Value(ValueKind.LITERAL, url)),
            volumes(mount), null, template.services().getFirst().secretPurposes());
    }

    private List<Volume> volumes(String mount) { return List.of(new Volume(mount, new Capacity(0, 0, 4096, 0))); }

    private ManagedDependencySpec with(Map<String, Value> changes, List<Volume> volumes, Database database, Set<String> purposes) {
        var original = template.services().getFirst();
        var environment = new TreeMap<>(original.environment()); environment.putAll(changes);
        var service = new Service(original.slot(), original.contract(), original.image(), original.sourceArtifactId(),
            original.sourceCommit(), original.port(), original.healthPath(), original.capacity(), purposes,
            environment, database, volumes, original.dependsOn(), original.initializationContract());
        return new ManagedDependencySpec(template.schemaVersion(), List.of(service), template.runtimeEnvironment());
    }
}
