package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingMode;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.DeploymentSecretPurposeCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentProviderConnectivitySecretOwnershipTest {
    private static final String REF = "MANAGED_SUPPORT_DIAGNOSTICS_STAGE";
    private static final String OWNER = "dep-support";
    private static final String TARGET = "support-stage";
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
    private final PlatformSecretService secrets;
    private final HttpClient http = mock(HttpClient.class);

    DeploymentProviderConnectivitySecretOwnershipTest() {
        Map<String, PlatformSecretEntity> rows = new HashMap<>();
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> {
            PlatformSecretEntity row = call.getArgument(0);
            rows.put(row.getName(), row);
            return row;
        });
        secrets = spy(new PlatformSecretService(repository, mock(PlatformAuditService.class),
            new MockEnvironment().withProperty("OPENAI_API_KEY", "shared-openai-test-value")
                .withProperty("MILVUS_USERNAME", "unprivileged-test-user")));
        secrets.upsertTargetManagedSecret(REF, "scoped-support-test-value", OWNER, TARGET, Map.of());
    }

    @ParameterizedTest
    @CsvSource({"orchestration,false", "orchestration,true", "generation,false", "generation,true",
        "embedding,false", "embedding,true", "qdrant,false", "qdrant,true"})
    void draftAndRequestProbesDenyBoundRefsBeforeSendingHttp(String purpose, boolean savedDraft) throws Exception {
        var config = mapper.createObjectNode().put("llmProvider", "onnx").put("embeddingProvider", "onnx")
            .put("orchestrationLlmProvider", "onnx").put("generationLlmProvider", "onnx")
            .put("vectorStrategy", "lucene")
            .put("deploymentId", OWNER).put("targetProfileId", TARGET);
        if ("qdrant".equals(purpose)) {
            config.put("vectorStrategy", "qdrant").put("qdrantRuntimeApiKeySecretName", REF)
                .put("qdrantHost", "https://customer-controlled.example");
        } else {
            config.put("embedding".equals(purpose) ? "embeddingProvider" : purpose + "LlmProvider", "openai")
                .put(purpose + "ApiKeySecretRef", REF)
                .put(purpose + "BaseUrl", "https://customer-controlled.example/v1");
        }
        var service = new DeploymentProviderConnectivityService(secrets, mapper, http);
        for (String deploymentId : new String[]{"dep-customer", OWNER}) {
            DeploymentEntity deployment = new DeploymentEntity();
            deployment.setId(deploymentId);
            deployment.setName("Test deployment");
            DeploymentDraftEntity draft = new DeploymentDraftEntity();
            draft.setProviderConfigJson(mapper.writeValueAsString(config));
            draft.setEntityConfigJson("{}");
            assertThatThrownBy(() -> {
                if (savedDraft) service.probe(deployment, draft);
                else service.probe(deploymentId, deployment.getName(), config, mapper.createObjectNode());
            }).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.getReason()).isEqualTo("SECRET_MATERIALIZATION_SCOPE_MISMATCH");
            }).hasMessageNotContaining("scoped-support-test-value");
        }
        verifyNoInteractions(http);
        verify(secrets, never()).resolveSecret(REF);
        verify(secrets, never()).isSecretPresent(REF);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void primaryAndPairedOverrideCandidatesCannotBypassTargetOwnership(boolean paired) {
        var bindings = mock(DeploymentProviderSecretBindingRepository.class);
        var binding = new DeploymentProviderSecretBindingEntity();
        String purpose = paired ? "MILVUS_RUNTIME_CREDENTIALS" : "OPENAI_API_KEY";
        binding.setDeploymentId("dep-customer");
        binding.setSecretPurpose(purpose);
        binding.setBindingMode(DeploymentProviderSecretBindingMode.ALLOW_STANDARD_PRECEDENCE);
        binding.setSecretName(paired ? "MILVUS_USERNAME" : REF);
        if (paired) binding.setSecondarySecretName(REF);
        when(bindings.findByDeploymentIdAndSecretPurpose("dep-customer", purpose)).thenReturn(Optional.of(binding));
        var resolver = new DeploymentProviderSecretResolutionService(secrets, repository, bindings,
            new DeploymentSecretPurposeCatalog());

        assertThatThrownBy(() -> resolver.resolve("dep-customer", purpose, null))
            .hasMessageContaining("SECRET_MATERIALIZATION_SCOPE_MISMATCH");
        verify(secrets, never()).resolveSecret(REF);
        verifyNoInteractions(http);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sharedOpenaiFallbackAndExistingDeploymentOwnedKeysStillProbe(boolean deploymentOwned) throws Exception {
        var config = mapper.createObjectNode().put("llmProvider", "onnx").put("embeddingProvider", "onnx")
            .put("generationLlmProvider", "onnx").put("vectorStrategy", "lucene")
            .put("orchestrationLlmProvider", "openai").put("orchestrationModel", "test-model")
            .put("orchestrationBaseUrl", "https://provider.example/v1");
        String expected = "shared-openai-test-value";
        if (deploymentOwned) {
            secrets.upsertManagedSecret("MANAGED_OWNED_PROVIDER_KEY", "owned-provider-test-value",
                Map.of("deploymentId", OWNER));
            config.put("orchestrationApiKeySecretRef", "MANAGED_OWNED_PROVIDER_KEY");
            expected = "owned-provider-test-value";
        }
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new DeploymentProviderConnectivityService(secrets, mapper, http);

        var summary = service.probe(OWNER, "Test deployment", config, mapper.createObjectNode());

        assertThat(summary.probes()).filteredOn(probe -> "orchestration_inference_endpoint".equals(probe.key()))
            .singleElement().satisfies(probe -> assertThat(probe.status()).isEqualTo("READY"));
        String expectedHeader = "Bearer " + expected;
        verify(http).send(argThat(request -> request.uri().toString().equals("https://provider.example/v1/chat/completions")
            && expectedHeader.equals(request.headers().firstValue("Authorization").orElse(null))), any(HttpResponse.BodyHandler.class));
    }
}
