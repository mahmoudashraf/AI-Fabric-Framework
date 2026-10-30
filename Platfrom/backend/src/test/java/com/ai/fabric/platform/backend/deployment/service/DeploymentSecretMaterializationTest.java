package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentSecretMaterializationTest {
    private static final String REF = "MANAGED_SUPPORT_DIAGNOSTICS_STAGE";
    private static final String OWNER = "dep-support";
    private static final String TARGET = "support-stage";
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlatformSecretService secrets;
    private final RailwayProvisioningPlanService plans = mock(RailwayProvisioningPlanService.class, CALLS_REAL_METHODS);

    DeploymentSecretMaterializationTest() {
        var repository = mock(PlatformSecretRepository.class);
        Map<String, PlatformSecretEntity> rows = new HashMap<>();
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> {
            PlatformSecretEntity row = call.getArgument(0);
            rows.put(row.getName(), row);
            return row;
        });
        secrets = new PlatformSecretService(repository, mock(PlatformAuditService.class),
            new MockEnvironment().withProperty("OPENAI_API_KEY", "shared-openai-test-value"));
        secrets.upsertTargetManagedSecret(REF, "scoped-support-test-value", OWNER, TARGET, Map.of());
        ReflectionTestUtils.setField(plans, "platformSecretService", secrets);
    }

    @ParameterizedTest @ValueSource(strings = {"coolify", "railway"})
    void providerKeyRefAndCustomerUrlCannotExportAnotherDeploymentOrTargetsCredential(String provider) throws Exception {
        JsonNode config = mapper.createObjectNode().put("orchestrationLlmProvider", "openai")
            .put("orchestrationApiKeySecretRef", REF).put("orchestrationBaseUrl", "https://customer-controlled.example/v1");
        List<RailwayEnvVarSummary> environment = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(plans, "addPurposeSpecificLlmEnv", environment, config);
        var key = environment.stream().filter(value -> "AI_PROVIDERS_ORCHESTRATION_API_KEY".equals(value.key())).findFirst().orElseThrow();
        assertThat(key.value()).isEqualTo("${secret:" + REF + "}");
        assertThat(environment).anyMatch(value -> "https://customer-controlled.example/v1".equals(value.value()));
        assertDenied(provider, key, "dep-customer", TARGET);
        assertDenied(provider, key, OWNER, "support-production");
        assertDenied(provider, key, OWNER, null);
        assertThat(materialize(provider, key, OWNER, TARGET)).isEqualTo("scoped-support-test-value");
    }

    @ParameterizedTest @ValueSource(strings = {"coolify", "railway"})
    void marketplaceManagedHeaderRefIsMaterializedOnlyForItsExactOwnerAndTarget(String provider) throws Exception {
        var compiler = mock(DeploymentMarketplaceDraftCompilerService.class, CALLS_REAL_METHODS);
        JsonNode route = ReflectionTestUtils.invokeMethod(compiler, "compileActionRoute", mapper.readTree("""
            {"method":"GET","url":"https://customer-controlled.example/read",
             "managedHeaderSecretRefFields":{"X-Support-Key":"diagnosticServiceKey"}}
            """), mapper.createObjectNode(), mapper.createObjectNode().put("diagnosticServiceKey", REF), "inspect");
        assertThat(route.at("/headers/X-Support-Key").asText()).isEqualTo("${" + REF + "}");
        List<RailwayEnvVarSummary> environment = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(plans, "addConnectorSecretEnv", environment, (Object) new JsonNode[]{route});
        assertThat(environment).containsExactly(new RailwayEnvVarSummary(REF, "${secret:" + REF + "}"));
        assertDenied(provider, environment.getFirst(), "dep-customer", TARGET);
        assertDenied(provider, environment.getFirst(), OWNER, "support-production");
        assertThat(materialize(provider, environment.getFirst(), OWNER, TARGET)).isEqualTo("scoped-support-test-value");
    }

    @ParameterizedTest @ValueSource(strings = {"coolify", "railway"})
    void deliberateSharedOpenaiFallbackStillMaterializes(String provider) {
        assertThat(materialize(provider, new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"),
            "dep-customer", TARGET)).isEqualTo("shared-openai-test-value");
    }

    @Test
    void managedBindingAliasesWithoutPersistedSecretsRemainResolverOwned() throws Exception {
        List<RailwayEnvVarSummary> environment = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(plans, "addConnectorSecretEnv", environment, (Object) new JsonNode[]{
            mapper.readTree("{\"url\":\"${MANAGED_EXTERNAL_INTEGRATION_ABC123}\"}")});
        assertThat(environment).isEmpty();
    }

    private void assertDenied(String provider, RailwayEnvVarSummary variable, String deployment, String target) {
        assertThatThrownBy(() -> materialize(provider, variable, deployment, target))
            .hasMessageContaining("SECRET_MATERIALIZATION_SCOPE_MISMATCH")
            .hasMessageNotContaining("scoped-support-test-value");
    }

    private String materialize(String provider, RailwayEnvVarSummary variable, String deployment, String target) {
        Object implementation = "coolify".equals(provider) ? mock(CoolifyDeploymentProvider.class, CALLS_REAL_METHODS)
            : mock(RailwayApiProvisioningProvider.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(implementation, "platformSecretService", secrets);
        return ReflectionTestUtils.invokeMethod(implementation, "resolveEnvVarValue", variable, deployment, target,
            "https://runtime.example", "https://connector.example");
    }
}
