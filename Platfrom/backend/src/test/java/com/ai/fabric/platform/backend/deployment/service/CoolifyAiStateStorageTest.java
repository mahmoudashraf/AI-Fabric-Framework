package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.generation.DeploymentRuntimeGenerationService;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoolifyAiStateStorageTest {
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    private final DeploymentAiStateStorageBindingService bindings = mock(DeploymentAiStateStorageBindingService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(targets, handles,
        mock(DeploymentSourceArtifactService.class), mock(RailwayProvisioningPlanService.class), resolver, client,
        mock(PlatformSecretService.class), mapper);

    @Test void externalSelectionResolvesBeforeAnyProviderEffectAndNeverReconcilesManagedDatabase() {
        provider.setAiStateStorageBindings(bindings);
        var deployment = deployment(); var version = version(); var release = release(); var target = target();
        var binding = binding();
        when(bindings.resolve(deployment, version, target)).thenReturn(binding);
        assertThat(provider.validateAiStateStorageBeforeEffects(deployment, version, release, target)).isSameAs(binding);
        verify(bindings).requireUnchanged(deployment, version, release);
        verifyNoInteractions(client, resolver, handles);
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-deployment", "wrong-version", "wrong-target", "not-published", "missing-service"})
    void wrongScopeOrMissingBindingServiceFailsBeforeProviderCalls(String change) {
        var deployment = deployment(); var version = version(); var release = release(); var target = target();
        if (!change.equals("missing-service")) provider.setAiStateStorageBindings(bindings);
        switch (change) {
            case "wrong-deployment" -> release.setDeploymentId("other");
            case "wrong-version" -> release.setDeploymentVersionId("other");
            case "wrong-target" -> release.setTargetProfileId("production");
            case "not-published" -> version.setStatus("DRAFT");
        }
        assertThatThrownBy(() -> provider.validateAiStateStorageBeforeEffects(deployment, version, release, target))
            .hasMessageContaining("AI_STATE_STORAGE_");
        verifyNoInteractions(client, resolver, handles);
    }

    @Test void aProductizedDeploymentCannotInferItsStorageChoiceFromLegacyTargetDefaults() {
        var offers = mock(CustomerOfferDeploymentRepository.class); when(offers.existsById("deployment")).thenReturn(true);
        provider.setCustomerOfferDeploymentRepository(offers);
        var version = version(); version.setProviderConfigJson("{}");
        var target = target(); target.setResourceDefaultsJson("{\"runtimeDatabaseMode\":\"COOLIFY_POSTGRES\"}");
        assertThatThrownBy(() -> provider.validateAiStateStorageBeforeEffects(deployment(), version, release(), target))
            .hasMessageContaining("EXPLICIT_CHOICE_REQUIRED");
        verifyNoInteractions(client, resolver, handles);
    }

    @Test void externalGenerationPinsActualSchemaConnectionsAndRemovesBootstrapAuthority() throws Exception {
        var version = version();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[{\"ingestionMode\":\"EXTERNAL_SYNC_HTTP\"}]}");
        var runtime = environment("runtime", version, List.of(new RailwayEnvVarSummary("SPRING_FLYWAY_URL", "jdbc:unsafe"),
            new RailwayEnvVarSummary("SPRING_DATASOURCE_HIKARI_JDBC_URL", "jdbc:unsafe"),
            new RailwayEnvVarSummary("SPRING_FLYWAY_ENABLED", "false")));
        assertThat(runtime).containsEntry("SPRING_DATASOURCE_URL", binding().jdbcUrl())
            .containsEntry("SPRING_FLYWAY_URL", binding().jdbcUrl())
            .containsEntry("SPRING_DATASOURCE_HIKARI_JDBC_URL", binding().jdbcUrl())
            .containsEntry("SPRING_FLYWAY_ENABLED", "true")
            .containsEntry("SPRING_FLYWAY_SCHEMAS", "ai_runtime")
            .containsEntry("SPRING_FLYWAY_CREATE_SCHEMAS", "false")
            .containsEntry("SPRING_FLYWAY_CLEAN_DISABLED", "true")
            .containsEntry("SPRING_DATASOURCE_HIKARI_SCHEMA", "ai_runtime")
            .containsEntry("SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA", "ai_runtime")
            .containsEntry("PLATFORM_AI_STATE_STORAGE_BINDING_ID", "binding")
            .doesNotContainKeys("PLATFORM_RUNTIME_DATABASE_RESOURCE_UUID", "PLATFORM_RUNTIME_DATABASE_PASSWORD_SECRET");
        var connector = environment("connector", version, List.of(
            new RailwayEnvVarSummary("REST_CONNECTOR_PERSISTENCE_ENABLED", "true"),
            new RailwayEnvVarSummary("REST_CONNECTOR_DATABASE_ROLE", "unsafe_role"),
            new RailwayEnvVarSummary("REST_CONNECTOR_BOOTSTRAP_JDBC_URL", "jdbc:unsafe"),
            new RailwayEnvVarSummary("REST_CONNECTOR_BOOTSTRAP_JDBC_USERNAME", "root"),
            new RailwayEnvVarSummary("REST_CONNECTOR_BOOTSTRAP_JDBC_PASSWORD", "unsafe-password")));
        assertThat(connector).containsEntry("REST_CONNECTOR_JDBC_URL", binding().connectorJdbcUrl())
            .containsEntry("REST_CONNECTOR_PERSISTENCE_SCHEMA", "ai_connector")
            .containsEntry("REST_CONNECTOR_JDBC_USERNAME", "ai_role")
            .containsEntry("REST_CONNECTOR_JDBC_PASSWORD", "fixture-password")
            .doesNotContainKeys("REST_CONNECTOR_DATABASE_ROLE", "REST_CONNECTOR_BOOTSTRAP_JDBC_URL",
                "REST_CONNECTOR_BOOTSTRAP_JDBC_USERNAME", "REST_CONNECTOR_BOOTSTRAP_JDBC_PASSWORD",
                "PLATFORM_INTEGRATION_DATABASE_RESOURCE_UUID");
    }

    @ParameterizedTest @ValueSource(strings = {"start", "stop", "restart", "delete", "status", "logs"})
    void retainedAiStateCannotReachProviderLifecycleEvenViaDirectProviderEntryPoints(String operation) {
        var handle = new DeploymentProviderResourceHandleEntity(); handle.setResourceKind(PreservedExternalBindingKinds.AI_STATE_STORAGE);
        assertThatThrownBy(() -> {
            switch (operation) {
                case "start" -> provider.start(handle, "test");
                case "stop" -> provider.stop(handle, "test");
                case "restart" -> provider.restart(handle, "test");
                case "delete" -> provider.delete(handle, "test");
                case "status" -> provider.status(handle);
                case "logs" -> provider.logs(handle, 100);
            }
        }).hasMessageContaining("CUSTOMER_SOURCE_PRESERVED");
        verifyNoInteractions(client, resolver, targets);
    }

    @ParameterizedTest @ValueSource(strings = {"authority", "fingerprint", "drain-service", "stop-checkpoint"})
    void replacementCredentialsCannotBypassExistingSequentialGenerationProof(String missing) {
        var release = release(); release.setRuntimeAuthoritySnapshotId("authority-snapshot");
        release.setRuntimeAuthorityFingerprint("f".repeat(64));
        if (!missing.equals("drain-service")) provider.setDeploymentRuntimeGenerationService(mock(DeploymentRuntimeGenerationService.class));
        if (!missing.equals("stop-checkpoint")) provider.setRuntimeGenerationCheckpointService(mock(RuntimeGenerationCheckpointService.class));
        if (missing.equals("authority")) release.setRuntimeAuthoritySnapshotId(null);
        if (missing.equals("fingerprint")) release.setRuntimeAuthorityFingerprint(null);
        var prior = new RuntimeGenerationCheckpointService.PriorRuntime("old-release", "runtime-uuid", "https://runtime.test");
        assertThatThrownBy(() -> provider.requireAiStateSequentialReplacement(release, prior))
            .hasMessageContaining("SEQUENTIAL_REPLACEMENT_REQUIRED");
        verifyNoInteractions(client, resolver, handles);
    }

    @Test void validSequentialInfrastructureIsRequiredOnlyWhenReplacingAnExistingGeneration() {
        provider.requireAiStateSequentialReplacement(release(), null);
        var release = release(); release.setRuntimeAuthoritySnapshotId("authority-snapshot");
        release.setRuntimeAuthorityFingerprint("f".repeat(64));
        provider.setDeploymentRuntimeGenerationService(mock(DeploymentRuntimeGenerationService.class));
        provider.setRuntimeGenerationCheckpointService(mock(RuntimeGenerationCheckpointService.class));
        provider.requireAiStateSequentialReplacement(release,
            new RuntimeGenerationCheckpointService.PriorRuntime("old-release", "runtime-uuid", "https://runtime.test"));
        verifyNoInteractions(client, resolver, handles);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> environment(String role, DeploymentVersionEntity version, List<RailwayEnvVarSummary> env) throws Exception {
        Object scope = nested("CoolifyResourceScope", "project", "project-name", "staging", "environment", false);
        Object source = nested("CoolifyProvisioningSource", "PREBUILT_IMAGE", null, null, null, null, null, null, null, null, null, null);
        var plan = new RailwayServicePlanSummary(role, "/", "/Dockerfile", "https://runtime.test", env);
        List<CoolifyEnvVar> result = ReflectionTestUtils.invokeMethod(provider, "buildEnvironment", deployment(), version,
            release(), target(), scope, source, plan, role, "https://runtime.test", "http://connector.test", "https://connector.test", binding());
        assertThat(result).filteredOn(value -> List.of("SPRING_DATASOURCE_URL", "SPRING_FLYWAY_URL", "SPRING_DATASOURCE_PASSWORD",
            "REST_CONNECTOR_JDBC_URL", "REST_CONNECTOR_JDBC_PASSWORD").contains(value.key()))
            .allSatisfy(value -> assertThat(value.shownOnce()).isTrue());
        return result.stream().filter(value -> !value.preview()).collect(Collectors.toMap(CoolifyEnvVar::key, CoolifyEnvVar::value));
    }
    private Object nested(String name, Object... arguments) throws Exception {
        var type = Arrays.stream(CoolifyDeploymentProvider.class.getDeclaredClasses()).filter(value -> name.equals(value.getSimpleName()))
            .findFirst().orElseThrow();
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true); return constructor.newInstance(arguments);
    }
    private CoolifyRuntimeDatabaseBinding binding() {
        var connection = new DeploymentAiStateStorageConnection("customer.example", 5432, "database", "ai_runtime", "ai_connector", "ai_role", "fixture-password");
        return new CoolifyRuntimeDatabaseBinding("CUSTOMER_POSTGRES", null, connection.runtimeJdbcUrl(), connection.username(),
            connection.password(), null, "binding", "fingerprint", "ai_runtime", "ai_connector", connection.connectorJdbcUrl());
    }
    private DeploymentEntity deployment() { var value = new DeploymentEntity(); value.setId("deployment"); value.setCustomerId("customer"); value.setTenantId("tenant"); return value; }
    private DeploymentVersionEntity version() {
        var value = new DeploymentVersionEntity(); value.setId("version"); value.setDeploymentId("deployment"); value.setStatus("PUBLISHED");
        value.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}"); return value;
    }
    private DeploymentReleaseEntity release() { var value = new DeploymentReleaseEntity(); value.setId("release"); value.setDeploymentId("deployment"); value.setDeploymentVersionId("version"); value.setTargetProfileId("staging"); return value; }
    private DeploymentTargetProfileEntity target() { var value = new DeploymentTargetProfileEntity(); value.setId("staging"); value.setEnvironmentName("staging"); value.setActive(true); value.setProviderType(DeploymentProviderType.COOLIFY); value.setResourceDefaultsJson("{}"); return value; }
}
