package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivityProbeSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentReleaseVerificationServiceTest {

    @Test void verifiedEffectiveRuntimeSourceOverridesExpectationsWithoutRewritingOriginalProvenance() {
        var service = releaseVerificationService(runtimePrivateSecrets(), mock(DeploymentArtifactService.class));
        var resolver = mock(com.ai.fabric.platform.backend.deployment.admission.EffectiveRuntimeArtifactResolver.class);
        ReflectionTestUtils.setField(service, "effectiveRuntimeArtifacts", resolver);
        var release = release(); release.setSourceArtifactId("original-artifact");
        String original = "{\"sourceCapabilityManifestHash\":\"original-hash\",\"sourceGitCommit\":\"original-commit\"}";
        release.setProvisioningDetailsJson(original);
        var artifact = new com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity();
        artifact.setCapabilityManifestHash("c".repeat(64)); artifact.setGitCommitSha("d".repeat(40));
        artifact.setCapabilityManifestJson("{\"migrationIds\":[\"provider-budget-validity-extensions-v1\"]}");
        when(resolver.resolve(release)).thenReturn(artifact);
        Object expected = ReflectionTestUtils.invokeMethod(service, "expectedRuntimeSource", release, objectMapper.valueToTree(Map.of(
            "sourceCapabilityManifestHash", "original-hash", "sourceGitCommit", "original-commit")));
        assertThat((String) ReflectionTestUtils.invokeMethod(expected, "capabilityManifestHash")).isEqualTo("c".repeat(64));
        assertThat((String) ReflectionTestUtils.invokeMethod(expected, "commit")).isEqualTo("d".repeat(40));
        assertThat((Set<?>) ReflectionTestUtils.invokeMethod(expected, "additionalMigrationIds"))
            .isEqualTo(Set.of("provider-budget-validity-extensions-v1"));
        assertThat(release.getSourceArtifactId()).isEqualTo("original-artifact");
        assertThat(release.getProvisioningDetailsJson()).isEqualTo(original);
    }

    @Test void artifactBackedVerificationCannotSilentlyUseOriginalDetailsWhenResolverIsMissing() {
        var service = releaseVerificationService(runtimePrivateSecrets(), mock(DeploymentArtifactService.class));
        var release = release(); release.setSourceArtifactId("original-artifact");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "expectedRuntimeSource", release, objectMapper.createObjectNode()))
            .hasMessageContaining("EFFECTIVE_RUNTIME_ARTIFACT_RESOLVER_REQUIRED");
    }

    @Test void asynchronousVerificationUsesOnlyTheTypedWorkerHeaderBoundary() {
        var service = releaseVerificationService(mock(PlatformSecretService.class), mock(DeploymentArtifactService.class));
        var authority = mock(com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService.class);
        ReflectionTestUtils.setField(service, "runtimeAuthorityService", authority);
        var deployment = deployment("https://runtime.example", null); var release = release();
        when(authority.issueReleaseVerificationHeaders(deployment, release, false)).thenReturn(Map.of("test", "read-only"));
        when(authority.issueReleaseVerificationHeaders(deployment, release, true)).thenReturn(Map.of("test", "documents"));
        assertThat((Map<?, ?>) ReflectionTestUtils.invokeMethod(service, "runtimeAdminHeaders", deployment, release)).isEqualTo(Map.of("test", "read-only"));
        assertThat((Map<?, ?>) ReflectionTestUtils.invokeMethod(service, "runtimeDocumentReadHeaders", deployment, release)).isEqualTo(Map.of("test", "documents"));
        org.mockito.Mockito.verify(authority).issueReleaseVerificationHeaders(deployment, release, false);
        org.mockito.Mockito.verify(authority).issueReleaseVerificationHeaders(deployment, release, true);
        org.mockito.Mockito.verifyNoMoreInteractions(authority);
    }

    @Test
    void targetAuthorityExpectationsRejectSharedDefaultsAndAnotherTarget() throws Exception {
        var service = releaseVerificationService(mock(PlatformSecretService.class), mock(DeploymentArtifactService.class));
        var revisions = new java.util.EnumMap<RuntimeAuthorityPurpose, RuntimeAuthorityBinding.SecretRevision>(RuntimeAuthorityPurpose.class);
        for (var purpose : List.of(RuntimeAuthorityPurpose.TRUSTED_BACKEND_API_KEY,
            RuntimeAuthorityPurpose.PRIVATE_ASSERTION_SIGNING_KEY, RuntimeAuthorityPurpose.PUBLIC_TOKEN_SIGNING_KEY)) {
            revisions.put(purpose, new RuntimeAuthorityBinding.SecretRevision("synthetic-" + purpose, "revision-1"));
        }
        var binding = new RuntimeAuthorityBinding("customer-123", "tenant-123", "dep-123", "target-123", "ver-123", "hash-123",
            1, "target-private-issuer", "target-private-audience", "target-public-issuer", "target-public-audience",
            true, true, List.of("https://workspace.example"), revisions);
        Object expected = ReflectionTestUtils.invokeMethod(service, "buildExpectations",
            deployment("https://runtime.example", null), version(), release(), artifactBundle(), binding);
        for (String posture : List.of("exact", "another-target", "shared-default")) {
            ObjectNode auth = objectMapper.createObjectNode();
            auth.put("ingressMode", "VERIFIED_CONTEXT_REQUIRED");
            auth.put("rejectConflictingRequestIdentity", true);
            auth.put("rejectRequestIdentityWhenVerifiedContextPresent", true);
            auth.put("trustedBackendConfigured", true);
            auth.put("privateAssertionValidationConfigured", true);
            auth.put("publicTokenValidationConfigured", true);
            auth.putObject("publicBootstrap").put("enabled", true);
            String privateIssuer = "exact".equals(posture) ? binding.privateIssuer()
                : "shared-default".equals(posture) ? "platform-release-verification" : "another-target-private-issuer";
            String privateAudience = "exact".equals(posture) ? binding.privateAudience() : "dep-123";
            String publicIssuer = "exact".equals(posture) ? binding.publicIssuer() : "runtime-public-bootstrap";
            String publicAudience = "exact".equals(posture) ? binding.publicAudience() : "loomai-ai-workspace";
            auth.putArray("privateAssertionAcceptedIssuers").add(privateIssuer);
            auth.putArray("privateAssertionAcceptedAudiences").add(privateAudience);
            auth.put("publicTokenIssuer", publicIssuer);
            auth.putArray("publicAcceptedIssuers").add(publicIssuer);
            auth.putArray("publicAcceptedAudiences").add(publicAudience);
            auth.put("publicDefaultAudience", publicAudience);
            var checks = objectMapper.createArrayNode();
            ReflectionTestUtils.invokeMethod(service, "validateRuntimeAuth", checks, auth, expected);
            assertThat(checks.get(0).path("status").asText()).as(posture)
                .isEqualTo("exact".equals(posture) ? "PASSED" : "FAILED");
        }
    }

    @Test
    void unavailableFrozenTargetAuthorityBlocksVerificationBeforeRuntimeProbes() throws Exception {
        var artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(any())).thenReturn(artifactBundle());
        var service = releaseVerificationService(runtimePrivateSecrets(), artifactService);
        var authority = mock(DeploymentRuntimeAuthorityService.class);
        ReflectionTestUtils.setField(service, "runtimeAuthorityService", authority);
        when(authority.requireReleaseUnchangedForProvisioning(any(), any(), any()))
            .thenThrow(new IllegalStateException("Frozen authority changed"));
        var run = verifyWithTargetEvidence(service, deployment("http://127.0.0.1:1", null), version(), release(), "POST_DEPLOY");
        var checks = objectMapper.readTree(run.getChecksJson());
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(checks.size()).isEqualTo(1);
        assertThat(checks.get(0).path("name").asText()).isEqualTo("release_target_context");
        org.mockito.Mockito.verify(authority).requireReleaseUnchangedForProvisioning(any(), any(), any());
        org.mockito.Mockito.verifyNoMoreInteractions(authority);
    }

    @Test
    void documentConnectorSlotUsesTheOwnedTargetResolverAndNeverTheLiteralSlot() throws Exception {
        var service = releaseVerificationService(runtimePrivateSecrets(), mock(DeploymentArtifactService.class));
        var resolver = mock(DeploymentDocumentBindingResolver.class);
        ReflectionTestUtils.setField(service, "documentBindingResolver", resolver);
        var handle = new com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity();
        handle.setId("owned-document-handle");
        when(resolver.resolve("dep-123", "target-123", "slot:document-storage")).thenReturn(handle);
        var version = documentEnabledRuntimeOnlyVersion();
        JsonNode config = objectMapper.readTree(version.getMarketplaceDatasetConfigJson());
        ((ObjectNode) config.path("datasets").get(0).path("sourceConnector")).put("bindingRef", "slot:document-storage");
        var release = release(); release.setTargetProfileId("target-123");
        Set<?> expected = ReflectionTestUtils.invokeMethod(service, "expectedDocumentSourceConnectorValues",
            config, "bindingRef", deployment("https://runtime.example", null), release);
        assertThat(expected).isEqualTo(Set.of("owned-document-handle"));
        assertThat(expected.contains("slot:document-storage")).isFalse();
        when(resolver.resolve("dep-123", "target-123", "slot:document-storage"))
            .thenThrow(new IllegalStateException("Document storage ownership changed"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "expectedDocumentSourceConnectorValues",
            config, "bindingRef", deployment("https://runtime.example", null), release))
            .hasMessageContaining("Document storage ownership changed");
        assertThat(version.getMarketplaceDatasetConfigJson()).contains("documents-primary").doesNotContain("slot:document-storage");
    }

    @Test
    void tokenSecretVerificationUsesOnlyConfiguredReferenceAndSafePurposeMetadata() throws Exception {
        var secrets = mock(PlatformSecretService.class);
        when(secrets.isSecretPresent("MILVUS_TOKEN")).thenReturn(true);
        var service = releaseVerificationService(secrets, mock(DeploymentArtifactService.class));
        var provider = objectMapper.readTree("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusRuntimeTokenSecretName":"MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET"}
            """);
        var checks = objectMapper.createArrayNode();
        ReflectionTestUtils.invokeMethod(service, "verifyManagedSecrets", checks,
            deployment("https://runtime.example", null), provider, objectMapper.createObjectNode());
        assertThat(StreamSupport.stream(checks.spliterator(), false)
            .filter(check -> "milvus_secret_available".equals(check.path("name").asText()))
            .findFirst().orElseThrow().path("status").asText()).isEqualTo("FAILED");
        when(secrets.isSecretPresent("MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET")).thenReturn(true);
        checks.removeAll();
        ReflectionTestUtils.invokeMethod(service, "verifyManagedSecrets", checks,
            deployment("https://runtime.example", null), provider, objectMapper.createObjectNode());
        assertThat(StreamSupport.stream(checks.spliterator(), false)
            .filter(check -> "milvus_secret_available".equals(check.path("name").asText()))
            .findFirst().orElseThrow().path("status").asText()).isEqualTo("PASSED");
        assertThat(checks.toString()).contains("MILVUS_TOKEN", "TOKEN").doesNotContain("MILVUS_USERNAME", "MILVUS_PASSWORD");
        org.mockito.Mockito.verify(secrets, org.mockito.Mockito.never()).isSecretPresent("MILVUS_TOKEN");
        org.mockito.Mockito.verify(secrets, org.mockito.Mockito.never()).resolveSecret("MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET");
    }

    @Test
    void preApplyRejectsInvalidMilvusAuthenticationWithoutEchoingCredentialOrFetchingArtifacts() {
        var artifacts = mock(DeploymentArtifactService.class);
        var service = releaseVerificationService(mock(PlatformSecretService.class), artifacts);
        var checks = objectMapper.createArrayNode();
        var version = version("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusToken":"fake-private-token",
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED","milvusExternalBindingRequired":true}
            """);
        ReflectionTestUtils.invokeMethod(service, "verifyPreApply", checks,
            deployment("https://runtime.example", null), version, release(), null);
        assertThat(checks.toString()).contains("milvus_authentication_ready", "FAILED").doesNotContain("fake-private-token");
        org.mockito.Mockito.verifyNoInteractions(artifacts);
    }

    @Test
    void preApplyAllowsUnresolvedTemplateButRejectsMissingTokenReferenceFromTargetBinding() throws Exception {
        var artifacts = mock(DeploymentArtifactService.class);
        var secrets = mock(PlatformSecretService.class);
        var service = releaseVerificationService(secrets, artifacts);
        var bindings = mock(DeploymentExistingVectorBindingService.class);
        ReflectionTestUtils.setField(service, "existingVectorBindings", bindings);
        var version = version("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED"}
            """);
        var deployment = deployment("https://runtime.example", null);
        var release = release();
        release.setTargetProfileId("target-fake");
        when(bindings.resolve(org.mockito.ArgumentMatchers.eq(deployment), org.mockito.ArgumentMatchers.eq(version),
            any(), org.mockito.ArgumentMatchers.eq("target-fake"))).thenReturn(objectMapper.readTree(version.getProviderConfigJson()));
        var checks = objectMapper.createArrayNode();
        ReflectionTestUtils.invokeMethod(service, "verifyPreApply", checks, deployment, version, release, null);
        assertThat(checks.toString()).contains("milvus_authentication_ready", "FAILED");
        org.mockito.Mockito.verify(bindings).resolve(org.mockito.ArgumentMatchers.eq(deployment), org.mockito.ArgumentMatchers.eq(version),
            any(), org.mockito.ArgumentMatchers.eq("target-fake"));
        org.mockito.Mockito.verifyNoInteractions(artifacts, secrets);
    }

    @Test
    void preApplyProjectsOnlyTheTypedVectorFailureCodeWithoutUpstreamExceptionDetails() {
        var artifacts = mock(DeploymentArtifactService.class);
        var service = releaseVerificationService(mock(PlatformSecretService.class), artifacts);
        var bindings = mock(DeploymentExistingVectorBindingService.class);
        ReflectionTestUtils.setField(service, "existingVectorBindings", bindings);
        var version = version("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED"}
            """);
        var actualBindings = new DeploymentExistingVectorBindingService(null, null, null, null, null, null, null, null, null, objectMapper);
        var failure = (DeploymentExistingVectorBindingService.BindingFailure) ReflectionTestUtils.invokeMethod(
            actualBindings, "failure", "EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED");
        when(bindings.resolve(any(), any(), any(), any())).thenThrow(failure);
        var checks = objectMapper.createArrayNode();
        ReflectionTestUtils.invokeMethod(service, "verifyPreApply", checks,
            deployment("https://runtime.example", null), version, release(), null);
        var check = StreamSupport.stream(checks.spliterator(), false)
            .filter(value -> "milvus_authentication_ready".equals(value.path("name").asText())).findFirst().orElseThrow();
        assertThat(check.path("details").path("errorCode").asText()).isEqualTo("EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED");
        assertThat(check.path("status").asText()).isEqualTo("FAILED");
        checks.removeAll();
        org.mockito.Mockito.doThrow(new IllegalStateException("private-token-and-upstream-body"))
            .when(bindings).resolve(any(), any(), any(), any());
        ReflectionTestUtils.invokeMethod(service, "verifyPreApply", checks,
            deployment("https://runtime.example", null), version, release(), null);
        assertThat(checks.toString()).doesNotContain("private-token-and-upstream-body", "errorCode");
        org.mockito.Mockito.verifyNoInteractions(artifacts);
    }

    @Test
    void serverOwnedRuntimeActionExpectationsComeFromExplicitBehaviorBinding() throws Exception {
        JsonNode behaviorConfig = objectMapper.readTree("""
            {
              "conversationHandover": {
                "actionBinding": {
                  "actionId": "request_conversation_handover",
                  "implementation": "SERVER_OWNED_RUNTIME_ACTION"
                }
              }
            }
            """);

        assertThat(DeploymentReleaseVerificationService.serverOwnedRuntimeActionNames(behaviorConfig))
            .containsExactly("request_conversation_handover");

        ((ObjectNode) behaviorConfig.path("conversationHandover").path("actionBinding"))
            .put("implementation", "EXTERNAL_HTTP");

        assertThat(DeploymentReleaseVerificationService.serverOwnedRuntimeActionNames(behaviorConfig))
            .isEmpty();
        assertThat(DeploymentReleaseVerificationService.serverOwnedRuntimeActionNames(null))
            .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildExpectationsIncludesServerOwnedActionMetadataWithoutExternalRoute() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        DeploymentReleaseVerificationService service = releaseVerificationService(
            platformSecretService,
            artifactService
        );
        DeploymentVersionEntity version = runtimeOnlyImageVersion();
        version.setBehaviorConfigJson("""
            {
              "type": "CONVERSATIONAL",
              "schemaVersion": "loomai-deployment-behavior-v1",
              "contractVersion": 1,
              "executionExtensions": ["CONVERSATION_HANDOVER"],
              "conversationHandover": {
                "actionBinding": {
                  "actionId": "request_conversation_handover",
                  "implementation": "SERVER_OWNED_RUNTIME_ACTION"
                }
              }
            }
            """);
        DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
            "dep-123",
            "ver-123",
            "v1",
            "hash-123",
            "https://platform.example/ai-actions.yml",
            "https://platform.example/ai-entity-config.yml",
            "https://platform.example/actions-routing.yml",
            "https://platform.example/ai-prompt-config.json",
            "https://platform.example/deployment-manifest.json"
        );

        Object expectations = ReflectionTestUtils.invokeMethod(
            service,
            "buildExpectations",
            deployment("https://runtime.example", "https://connector.example"),
            version,
            runtimeOnlyImageRelease(),
            artifacts,
            null
        );

        assertThat((Set<String>) ReflectionTestUtils.invokeMethod(expectations, "expectedActionNames"))
            .containsExactly("request_conversation_handover");
        assertThat((Set<String>) ReflectionTestUtils.invokeMethod(
            expectations,
            "expectedActionNamesWithPresentationHints"
        )).containsExactly("request_conversation_handover");
        assertThat((Set<String>) ReflectionTestUtils.invokeMethod(
            expectations,
            "expectedActionNamesWithProvenance"
        )).containsExactly("request_conversation_handover");
        assertThat((Set<String>) ReflectionTestUtils.invokeMethod(expectations, "expectedRoutingActions"))
            .isEmpty();
    }

    private static final String AI_FABRIC_VERSION = "0.8.38";
    private static final String ENTITY_CONFIG_CONTRACT_VERSION =
        "AI_ENTITY_CONFIG_V0_4";
    private static final String ENTITY_CONFIG_HASH = "entity-hash-123";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expectsDeclarativeChainsOnlyForAgenticSpecialistTeams() throws Exception {
        assertThat(DeploymentReleaseVerificationService.expectsSpecialistChains(
            objectMapper.readTree("{\"type\":\"AGENTIC_SPECIALIST_TEAM\"}")
        )).isTrue();
        assertThat(DeploymentReleaseVerificationService.expectsSpecialistChains(
            objectMapper.readTree("{\"type\":\"SMART_BRAIN\"}")
        )).isFalse();
        assertThat(DeploymentReleaseVerificationService.expectsSpecialistChains(
            objectMapper.readTree("{\"type\":\"CONVERSATIONAL\"}")
        )).isFalse();
    }

    @Test
    void documentIngestionOwnsDocumentOnlyVectorizationWithoutClassicSourceConnection() throws Exception {
        JsonNode documentDatasets = objectMapper.readTree("""
            {
              "datasets": [
                {
                  "datasetId": "document-knowledge",
                  "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE"
                }
              ]
            }
            """);

        assertThat(DeploymentReleaseVerificationService.documentIngestionOwnsVectorization(
            documentDatasets,
            externalDocumentVectorizationSummary(List.of("document"))
        )).isTrue();
        assertThat(DeploymentReleaseVerificationService.documentIngestionOwnsVectorization(
            documentDatasets,
            externalDocumentVectorizationSummary(List.of("document", "product"))
        )).isFalse();
        assertThat(DeploymentReleaseVerificationService.documentIngestionOwnsVectorization(
            objectMapper.readTree("{\"datasets\":[]}"),
            externalDocumentVectorizationSummary(List.of("document"))
        )).isFalse();
    }

    @Test
    void postDeployRoutingExpectationsIncludeMarketplaceHttpAuthority() throws Exception {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        DeploymentReleaseVerificationService service = releaseVerificationService(
            runtimePrivateSecrets(),
            artifactService
        );
        DeploymentEntity deployment = deployment("https://runtime.example", "https://connector.example");
        deployment.setTenantId("ten-123");
        DeploymentVersionEntity version = version();
        version.setActionsConfigJson("""
            {
              "actions": [{
                "name": "provider_search",
                "accessMode": "READ",
                "route": {
                  "connectionProfileRef": "provider-profile",
                  "protectedResourceBindingRef": "provider-account",
                  "requiredCapabilityGrants": ["records:read"],
                  "trustedResourcePlacements": [{"target":"QUERY","field":"accountId"}],
                  "method": "GET",
                  "path": "/records"
                }
              }]
            }
            """);
        version.setRoutingConfigJson("""
            {"connector":{"inbound-auth":{"allow-unauthenticated":false}},"actions":{}}
            """);
        version.setMarketplaceDatasetConfigJson("""
            {
              "contractVersion": "MARKETPLACE_DATASET_CONFIG_V1",
              "datasets": [{
                "datasetId": "provider-records",
                "entityType": "provider-record",
                "ingestionMode": "EXTERNAL_SYNC_HTTP",
                "handleRef": "plugin/provider-records",
                "datasetHash": "dataset-hash",
                "syncConnector": {
                  "connectionProfile": {
                    "profileId": "provider-profile",
                    "baseUrl": "https://provider.example",
                    "capabilityGrants": ["records:read"]
                  },
                  "protectedResource": {
                    "bindingId": "provider-account",
                    "connectionProfileRef": "provider-profile",
                    "resourceId": "account-123",
                    "capabilityGrants": ["records:read"]
                  },
                  "httpSource": {
                    "sourceId": "provider-record-source",
                    "connectionProfileRef": "provider-profile",
                    "protectedResourceBindingRef": "provider-account",
                    "path": "/records",
                    "trustedResourcePlacements": [{"target":"QUERY","field":"accountId"}],
                    "mapping": {"recordsJsonPointer":"/results","idJsonPointer":"/id"},
                    "vectorSpace": "provider-record",
                    "entityType": "provider-record"
                  }
                }
              }]
            }
            """);

        JsonNode routing = service.compileRoutingExpectations(deployment, version);

        assertThat(routing.path("connection-profiles").has("provider-profile")).isTrue();
        assertThat(routing.path("protected-resources").has("provider-account")).isTrue();
        assertThat(routing.path("actions").path("provider_search")
            .path("connection-profile-ref").asText()).isEqualTo("provider-profile");
        assertThat(routing.path("runtime-data-sync").path("deployment-id").asText())
            .isEqualTo("dep-123");
        assertThat(routing.path("runtime-data-sync").path("tenant-id").asText())
            .isEqualTo("ten-123");
    }

    @Test
    void verifyChecksRuntimeAndConnectorAdminStateAgainstPublishedVersion() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = releaseWithCoolifyVectorizationRunner();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_DEPLOY");

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(run.getSummaryMessage()).isEqualTo("29 passed, 0 failed, 4 skipped");

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses).hasSize(33);
            assertThat(statuses.values()).contains("SKIPPED");
            assertThat(statuses)
                .containsEntry("runtime_admin_overview_http_probe", "PASSED")
                .containsEntry("runtime_auth_overview_http_probe", "PASSED")
                .containsEntry("runtime_config_matches_expected", "PASSED")
                .containsEntry("runtime_prompt_config_matches_expected", "PASSED")
                .containsEntry("runtime_knowledge_sources_match_expected", "PASSED")
                .containsEntry("runtime_knowledge_source_routing_ready", "PASSED")
                .containsEntry("runtime_shell_config_matches_expected", "PASSED")
                .containsEntry("runtime_auth_configuration_matches_expected", "PASSED")
                .containsEntry("runtime_actions_match_expected", "PASSED")
                .containsEntry("runtime_action_metadata_matches_expected", "PASSED")
                .containsEntry("runtime_entity_types_match_expected", "PASSED")
                .containsEntry("connector_admin_overview_http_probe", "PASSED")
                .containsEntry("connector_config_matches_expected", "PASSED")
                .containsEntry("connector_actions_match_expected", "PASSED")
                .containsEntry("connector_authz_configuration_matches_expected", "PASSED")
                .containsEntry("document_source_connector_ready", "SKIPPED")
                .containsEntry("document_active_sources_ready", "SKIPPED")
                .containsEntry("document_retrieval_evidence_ready", "SKIPPED")
                .containsEntry("marketplace_dataset_sync_matches_expected", "SKIPPED")
                .containsEntry("vectorization_control_plane_ready", "PASSED")
                .containsEntry("vectorization_runner_registration_ready", "PASSED")
                .containsEntry("vectorization_runner_service_provisioned", "PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifySupportsActionOnlyPublishedVersionWithoutKnowledgeOrShellArtifacts() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            runtimeServer.removeContext("/api/admin/overview");
            runtimeServer.createContext(
                "/api/admin/overview",
                privateRuntimeJsonHandler(
                    """
                        {
                          "success": true,
                          "aiFabricFrameworkVersion": "0.8.38",
                          "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
                          "entityConfigHash": "entity-hash-123",
                          "deploymentVersionId": "ver-123",
                          "deploymentBehavior": {
                            "type": "CONVERSATIONAL",
                            "schemaVersion": "loomai-deployment-behavior-v1",
                            "contractVersion": 1,
                            "compositionHash": "",
                            "specialistChainsEnabled": false
                          },
                          "runtimeMigrations": {"appliedMigrationIds": []},
                          "runtimeCapabilityManifestHash": "",
                          "productSourceCommit": "",
                          "aifabricEntities": {
                            "available": true,
                            "queue": {"ready": true}
                          },
                          "entityConfigLocation": "%s",
                          "promptConfigLocation": "%s",
                          "knowledgeSourceConfigLocation": "",
                          "shellConfigLocation": "",
                          "actionCatalogSources": [
                            {
                              "type": "FILE",
                              "path": "%s",
                              "optional": false
                            }
                          ],
                          "actionsCount": 2,
                          "confirmationInterceptorsCount": 1,
                          "postActionWebhookPoliciesCount": 0,
                          "actionNamesWithPostActionWebhookPolicies": [],
                          "webhookTargetsCount": 0,
                          "webhookTargetIds": [],
                          "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                          "confirmationInterceptorSources": ["%s"],
                          "knowledgeSourcesCount": 0,
                          "knowledgeSourceIds": [],
                          "knowledgeSourceTypes": [],
                          "knowledgeSourceAdapterTypes": [],
                          "shellModulesCount": 0,
                          "shellModuleIds": [],
                          "shellCardsCount": 0,
                          "shellCardIds": [],
                          "shellStarterPromptsCount": 0,
                          "shellGreetingConfigured": false,
                          "supportedEntityTypes": ["product", "policy"],
                          "inferenceProfile": {
                            "llmProvider": "openai",
                            "embeddingProvider": "openai"
                          },
                          "marketplaceSupport": {
                            "knowledgeSourceContractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                            "shellConfigContractVersion": "SHELL_CONFIG_V1",
                            "inferenceProfileContractVersion": "INFERENCE_PROFILE_RUNTIME_V1"
                          },
                          "auth": {
                            "ingressMode": "VERIFIED_CONTEXT_REQUIRED",
                            "verifiedContextRequired": true,
                            "rejectConflictingRequestIdentity": true,
                            "rejectRequestIdentityWhenVerifiedContextPresent": true,
                            "trustedBackendConfigured": true,
                            "privateAssertionValidationConfigured": true,
                            "privateAssertionAcceptedIssuers": ["platform-runtime:SESSION", "platform-runtime:API_KEY", "platform-poc:SESSION", "platform-poc:API_KEY", "platform-poc:SYSTEM", "platform-release-verification", "platform-document-operations", "platform-marketplace-dataset-sync", "platform-vectorization-runner", "platform-vectorization-verification", "platform-runtime-coverage"],
                            "privateAssertionAcceptedAudiences": ["dep-123"],
                            "publicTokenValidationConfigured": false,
                            "publicAuthorizationHeader": "Authorization",
                            "publicTokenScheme": "Bearer",
                            "publicTokenIssuer": "runtime-public-bootstrap",
                            "publicAcceptedIssuers": [],
                            "publicAcceptedAudiences": [],
                            "publicDefaultAudience": "",
                            "publicBootstrap": {
                              "enabled": false,
                              "allowMissingOrigin": false,
                              "allowedOrigins": []
                            }
                          }
                        }
                        """.formatted(
                        artifacts.entityArtifactUrl(),
                        artifacts.promptArtifactUrl(),
                        artifacts.actionsArtifactUrl(),
                        artifacts.actionsArtifactUrl()
                    )
                )
            );
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            version.setKnowledgeSourceConfigJson(null);
            version.setShellConfigJson(null);
            DeploymentReleaseEntity release = releaseWithVectorizationRunner();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_DEPLOY");

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_knowledge_sources_match_expected")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_shell_config_matches_expected")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_actions_match_expected")).isEqualTo("PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifySkipsConnectorChecksForRuntimeOnlyImageWithoutActions() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );
            registerRuntimeOnlyImageHandlers(runtimeServer, artifacts);
            runtimeServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
                .thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
                .thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
                .thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
                .thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            DeploymentProviderConnectivityService connectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService vectorizationVerificationService =
                mock(DeploymentVectorizationVerificationService.class);
            when(vectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofMillis(500)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                mock(RailwayPreflightService.class),
                connectivityService,
                tenantScopedVectorService,
                vectorizationVerificationService
            );

            String runtimeBaseUrl = "http://127.0.0.1:" + runtimeServer.getAddress().getPort();
            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment(runtimeBaseUrl, runtimeBaseUrl),
                runtimeOnlyImageVersion(),
                runtimeOnlyImageRelease(),
                "POST_DEPLOY"
            );

            assertThat(run.getStatus())
                .as(run.getChecksJson())
                .isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_admin_overview_http_probe")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_config_matches_expected")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "connector_base_url_present")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_health_http_probe")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_admin_overview_http_probe")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_config_matches_expected")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_authz_configuration_matches_expected")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_actions_overview_http_probe")).isEqualTo("SKIPPED");
            assertThat(checkStatus(run, "connector_actions_match_expected")).isEqualTo("SKIPPED");
        } finally {
            runtimeServer.stop(0);
        }
    }

    @Test
    void verifyRequiresReadyDocumentConnectorWithExactReadOnlyScopeAndBinding() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<JsonNode> assertionClaims = new AtomicReference<>();
        try {
            DeploymentArtifactBundleSummary artifacts = artifactBundle();
            registerRuntimeOnlyImageHandlers(runtimeServer, artifacts);
            registerDocumentConnectorStatusHandler(
                runtimeServer,
                true,
                "S3_COMPATIBLE_OBJECT_STORAGE",
                "documents-primary",
                assertionClaims
            );
            registerActiveDocumentEvidenceHandlers(runtimeServer);
            runtimeServer.start();

            PlatformSecretService platformSecretService = runtimePrivateSecrets();
            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            DeploymentReleaseVerificationService service = releaseVerificationService(
                platformSecretService,
                artifactService
            );

            String runtimeBaseUrl = "http://127.0.0.1:" + runtimeServer.getAddress().getPort();
            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment(runtimeBaseUrl, runtimeBaseUrl),
                documentEnabledRuntimeOnlyVersion(),
                runtimeOnlyImageRelease(),
                "POST_DEPLOY"
            );

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(checkStatus(run, "document_source_connector_ready")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "document_active_sources_ready")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "document_retrieval_evidence_ready")).isEqualTo("PASSED");
            assertThat(assertionClaims.get()).isNotNull();
            assertThat(assertionClaims.get().path("iss").asText())
                .isEqualTo("platform-release-verification");
            assertThat(StreamSupport.stream(assertionClaims.get().path("scopes").spliterator(), false)
                .map(JsonNode::asText)
                .toList())
                .containsExactly(RuntimePrivateAccessSupport.SCOPE_DOCUMENTS_READ);
        } finally {
            runtimeServer.stop(0);
        }
    }

    @Test
    void verifyFailsWhenDocumentConnectorIsReadyButNoActiveIndexedSourceExists() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = artifactBundle();
            registerRuntimeOnlyImageHandlers(runtimeServer, artifacts);
            registerDocumentConnectorStatusHandler(
                runtimeServer,
                true,
                "S3_COMPATIBLE_OBJECT_STORAGE",
                "documents-primary",
                new AtomicReference<>()
            );
            runtimeServer.createContext("/api/documents/sources", privateRuntimeJsonHandler("[]"));
            runtimeServer.start();

            PlatformSecretService platformSecretService = runtimePrivateSecrets();
            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            DeploymentReleaseVerificationService service = releaseVerificationService(
                platformSecretService,
                artifactService
            );

            String runtimeBaseUrl = "http://127.0.0.1:" + runtimeServer.getAddress().getPort();
            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment(runtimeBaseUrl, runtimeBaseUrl),
                documentEnabledRuntimeOnlyVersion(),
                runtimeOnlyImageRelease(),
                "POST_DEPLOY"
            );

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(checkStatus(run, "document_source_connector_ready")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "document_active_sources_ready")).isEqualTo("FAILED");
            assertThat(checkStatus(run, "document_retrieval_evidence_ready")).isEqualTo("SKIPPED");
        } finally {
            runtimeServer.stop(0);
        }
    }

    @Test
    void verifyFailsDocumentConnectorReadinessWhenImmutableBindingDoesNotMatch() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = artifactBundle();
            registerRuntimeOnlyImageHandlers(runtimeServer, artifacts);
            registerDocumentConnectorStatusHandler(
                runtimeServer,
                true,
                "S3_COMPATIBLE_OBJECT_STORAGE",
                "wrong-binding",
                new AtomicReference<>()
            );
            runtimeServer.start();

            PlatformSecretService platformSecretService = runtimePrivateSecrets();
            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            DeploymentReleaseVerificationService service = releaseVerificationService(
                platformSecretService,
                artifactService
            );

            String runtimeBaseUrl = "http://127.0.0.1:" + runtimeServer.getAddress().getPort();
            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment(runtimeBaseUrl, runtimeBaseUrl),
                documentEnabledRuntimeOnlyVersion(),
                runtimeOnlyImageRelease(),
                "POST_DEPLOY"
            );

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(checkStatus(run, "document_source_connector_ready")).isEqualTo("FAILED");
        } finally {
            runtimeServer.stop(0);
        }
    }

    @Test
    void verifyUsesLongerTimeoutForRuntimeIndexingOverviewProbe() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            runtimeServer.removeContext("/api/admin/indexing/overview");
            runtimeServer.createContext(
                "/api/admin/indexing/overview",
                delayedPrivateRuntimeJsonHandler(
                    1500,
                    """
                        {
                          "success": true,
                          "supportsVectorScan": true,
                          "entityTypes": ["product", "policy"],
                          "countsByEntityType": {
                            "product": 4,
                            "policy": 1
                          },
                          "totalVectors": 5
                        }
                        """
                )
            );
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(1), Duration.ofSeconds(3)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = releaseWithVectorizationRunner();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_DEPLOY");

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_indexing_overview_http_probe")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_entity_types_match_expected")).isEqualTo("PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifyWaitsForRuntimeOverviewToConvergeBeforeScoringPostApply() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary expectedArtifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );
            DeploymentArtifactBundleSummary previousArtifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-old",
                "v0",
                "hash-old",
                "https://platform.example/api/deployments/dep-123/versions/ver-old/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-old/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-old/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-old/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-old/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, expectedArtifacts);
            AtomicInteger runtimeOverviewCalls = new AtomicInteger();
            runtimeServer.removeContext("/api/admin/overview");
            runtimeServer.createContext(
                "/api/admin/overview",
                privateRuntimeJsonHandler(() -> runtimeOverviewCalls.incrementAndGet() == 1
                    ? """
                        {
                          "success": true,
                          "aiFabricFrameworkVersion": "0.8.38",
                          "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
                          "entityConfigHash": "entity-hash-123",
                          "deploymentVersionId": "ver-old",
                          "deploymentBehavior": {
                            "type": "CONVERSATIONAL",
                            "schemaVersion": "loomai-deployment-behavior-v1",
                            "contractVersion": 1,
                            "compositionHash": "",
                            "specialistChainsEnabled": false
                          },
                          "runtimeMigrations": {"appliedMigrationIds": []},
                          "runtimeCapabilityManifestHash": "",
                          "productSourceCommit": "",
                          "aifabricEntities": {
                            "available": true,
                            "queue": {"ready": true}
                          },
                          "entityConfigLocation": "%s",
                          "promptConfigLocation": "%s",
                          "knowledgeSourceConfigLocation": "%s",
                          "shellConfigLocation": "%s",
                          "actionCatalogSources": [
                            {
                              "type": "FILE",
                              "path": "%s",
                              "optional": false
                            }
                          ],
                          "actionsCount": 2,
                          "confirmationInterceptorsCount": 1,
                          "postActionWebhookPoliciesCount": 0,
                          "actionNamesWithPostActionWebhookPolicies": [],
                          "webhookTargetsCount": 0,
                          "webhookTargetIds": [],
                          "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                          "confirmationInterceptorSources": ["%s"],
                          "knowledgeSourcesCount": 1,
                          "knowledgeSourceIds": ["shared-policies"],
                          "knowledgeSourceTypes": ["policy"],
                          "knowledgeSourceAdapterTypes": ["shared-index"],
                          "shellModulesCount": 2,
                          "shellModuleIds": ["product-catalog", "policies"],
                          "shellCardsCount": 1,
                          "shellCardIds": ["policy-summary"],
                          "shellStarterPromptsCount": 2,
                          "shellGreetingConfigured": true,
                          "supportedEntityTypes": ["product", "policy"],
                          "inferenceProfile": {
                            "llmProvider": "openai",
                            "embeddingProvider": "openai"
                          },
                          "marketplaceSupport": {
                            "knowledgeSourceContractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                            "shellConfigContractVersion": "SHELL_CONFIG_V1",
                            "inferenceProfileContractVersion": "INFERENCE_PROFILE_RUNTIME_V1"
                          },
                          "auth": {
                            "ingressMode": "VERIFIED_CONTEXT_REQUIRED"
                          }
                        }
                        """.formatted(
                        previousArtifacts.entityArtifactUrl(),
                        previousArtifacts.promptArtifactUrl(),
                        previousArtifacts.knowledgeSourceArtifactUrl() == null ? "" : previousArtifacts.knowledgeSourceArtifactUrl(),
                        previousArtifacts.shellArtifactUrl() == null ? "" : previousArtifacts.shellArtifactUrl(),
                        previousArtifacts.actionsArtifactUrl(),
                        previousArtifacts.actionsArtifactUrl()
                    ) : """
                        {
                          "success": true,
                          "aiFabricFrameworkVersion": "0.8.38",
                          "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
                          "entityConfigHash": "entity-hash-123",
                          "deploymentVersionId": "ver-123",
                          "deploymentBehavior": {
                            "type": "CONVERSATIONAL",
                            "schemaVersion": "loomai-deployment-behavior-v1",
                            "contractVersion": 1,
                            "compositionHash": "",
                            "specialistChainsEnabled": false
                          },
                          "runtimeMigrations": {"appliedMigrationIds": []},
                          "runtimeCapabilityManifestHash": "",
                          "productSourceCommit": "",
                          "aifabricEntities": {
                            "available": true,
                            "queue": {"ready": true}
                          },
                          "entityConfigLocation": "%s",
                          "promptConfigLocation": "%s",
                          "knowledgeSourceConfigLocation": "%s",
                          "shellConfigLocation": "%s",
                          "actionCatalogSources": [
                            {
                              "type": "FILE",
                              "path": "%s",
                              "optional": false
                            }
                          ],
                          "actionsCount": 2,
                          "confirmationInterceptorsCount": 1,
                          "postActionWebhookPoliciesCount": 0,
                          "actionNamesWithPostActionWebhookPolicies": [],
                          "webhookTargetsCount": 0,
                          "webhookTargetIds": [],
                          "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                          "confirmationInterceptorSources": ["%s"],
                          "knowledgeSourcesCount": 1,
                          "knowledgeSourceIds": ["shared-policies"],
                          "knowledgeSourceTypes": ["policy"],
                          "knowledgeSourceAdapterTypes": ["shared-index"],
                          "shellModulesCount": 2,
                          "shellModuleIds": ["product-catalog", "policies"],
                          "shellCardsCount": 1,
                          "shellCardIds": ["policy-summary"],
                          "shellStarterPromptsCount": 2,
                          "shellGreetingConfigured": true,
                          "supportedEntityTypes": ["product", "policy"],
                          "inferenceProfile": {
                            "llmProvider": "openai",
                            "embeddingProvider": "openai"
                          },
                          "marketplaceSupport": {
                            "knowledgeSourceContractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                            "shellConfigContractVersion": "SHELL_CONFIG_V1",
                            "inferenceProfileContractVersion": "INFERENCE_PROFILE_RUNTIME_V1"
                          },
                          "auth": {
                            "ingressMode": "VERIFIED_CONTEXT_REQUIRED"
                          }
                        }
                        """.formatted(
                        expectedArtifacts.entityArtifactUrl(),
                        expectedArtifacts.promptArtifactUrl(),
                        expectedArtifacts.knowledgeSourceArtifactUrl() == null ? "" : expectedArtifacts.knowledgeSourceArtifactUrl(),
                        expectedArtifacts.shellArtifactUrl() == null ? "" : expectedArtifacts.shellArtifactUrl(),
                        expectedArtifacts.actionsArtifactUrl(),
                        expectedArtifacts.actionsArtifactUrl()
                    )
                )
            );
            registerConnectorHandlers(connectorServer, expectedArtifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(expectedArtifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                new PlatformVerificationProperties(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(10),
                    "/actuator/health",
                    "/actuator/health",
                    "/api/admin/connector/health",
                    "/api/admin/overview",
                    "/api/admin/auth/overview",
                    "/api/admin/actions/overview",
                    "/api/admin/indexing/overview",
                    "/api/admin/connector/overview",
                    "/api/admin/connector/actions/overview",
                    true
                ),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = releaseWithVectorizationRunner();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_APPLY");

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(runtimeOverviewCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(checkStatus(run, "platform_authenticated_runtime_token_creation_ready")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_config_matches_expected")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "connector_config_matches_expected")).isEqualTo("PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifyFailsWhenRuntimeAuthPostureDoesNotMatchPublishedSecurityConfig() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentReleaseEntity release = releaseWithVectorizationRunner();
            DeploymentVersionEntity version = version(
                """
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai"
                    }
                    """,
                """
                    {
                      "adminApiKeyEnabled": true,
                      "publicRuntimeBootstrapEnabled": true,
                      "publicRuntimeTokenIssuer": "shopify-app",
                      "publicRuntimeAcceptedIssuers": "shopify-app,runtime-public-bootstrap",
                      "publicRuntimeAcceptedAudiences": "storefront-chat",
                      "publicRuntimeDefaultAudience": "storefront-chat"
                    }
                    """
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_DEPLOY");

            assertThat(run.getStatus()).isEqualTo("FAILED");

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses)
                .containsEntry("runtime_admin_overview_http_probe", "PASSED")
                .containsEntry("runtime_auth_overview_http_probe", "PASSED")
                .containsEntry("runtime_auth_configuration_matches_expected", "FAILED")
                .containsEntry("connector_admin_overview_http_probe", "PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifyRetriesConnectorHealthProbeUntilRuntimeProxyBecomesAvailable() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger connectorHealthCalls = new AtomicInteger();
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts, exchange -> {
                if (connectorHealthCalls.incrementAndGet() == 1) {
                    writeJson(exchange, 404, """
                        {"status":"error","code":404,"message":"Application not found"}
                        """);
                    return;
                }
                writeJson(exchange, 200, """
                    {"status":"UP"}
                    """);
            });
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                new PlatformVerificationProperties(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(10),
                    "/actuator/health",
                    "/actuator/health",
                    "/api/admin/connector/health",
                    "/api/admin/overview",
                    "/api/admin/auth/overview",
                    "/api/admin/actions/overview",
                    "/api/admin/indexing/overview",
                    "/api/admin/connector/overview",
                    "/api/admin/connector/actions/overview",
                    true
                ),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = release();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_APPLY");

            assertThat(checkStatus(run, "connector_health_http_probe")).isEqualTo("PASSED");
            assertThat(connectorHealthCalls.get()).isGreaterThanOrEqualTo(2);
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifyRetriesRuntimeAdminJsonProbesUntilRuntimeApplicationBecomesAvailable() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger authOverviewCalls = new AtomicInteger();
        AtomicInteger actionsOverviewCalls = new AtomicInteger();
        AtomicInteger indexingOverviewCalls = new AtomicInteger();
        AtomicInteger connectorActionsOverviewCalls = new AtomicInteger();
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            runtimeServer.removeContext("/api/admin/auth/overview");
            runtimeServer.createContext("/api/admin/auth/overview", exchange -> {
                String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
                String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
                if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                    writeJson(exchange, 401, """
                        {"success":false,"message":"Unauthorized"}
                        """);
                    return;
                }
                if (authOverviewCalls.incrementAndGet() == 1) {
                    writeJson(exchange, 404, """
                        {"status":"error","code":404,"message":"Application not found"}
                        """);
                    return;
                }
                writeJson(exchange, 200, """
                    {
                      "success": true,
                      "contractVersion": "RUNTIME_AUTH_OVERVIEW_V1",
                      "auth": {
                        "ingressMode": "VERIFIED_CONTEXT_REQUIRED",
                        "verifiedContextRequired": true,
                        "rejectConflictingRequestIdentity": true,
                        "rejectRequestIdentityWhenVerifiedContextPresent": true,
                        "trustedBackendConfigured": true,
                        "privateAssertionValidationConfigured": true,
                        "privateAssertionAcceptedIssuers": ["platform-runtime:SESSION", "platform-runtime:API_KEY", "platform-poc:SESSION", "platform-poc:API_KEY", "platform-poc:SYSTEM", "platform-release-verification", "platform-document-operations", "platform-marketplace-dataset-sync", "platform-vectorization-runner", "platform-vectorization-verification", "platform-runtime-coverage"],
                        "privateAssertionAcceptedAudiences": ["dep-123"],
                        "publicTokenValidationConfigured": false,
                        "publicAuthorizationHeader": "Authorization",
                        "publicTokenScheme": "Bearer",
                        "publicTokenIssuer": "runtime-public-bootstrap",
                        "publicAcceptedIssuers": [],
                        "publicAcceptedAudiences": [],
                        "publicDefaultAudience": "",
                        "publicBootstrap": {
                          "enabled": false,
                          "allowMissingOrigin": false,
                          "allowedOrigins": []
                        }
                      },
                      "warnings": [],
                      "warningCount": 0
                    }
                    """);
            });
            runtimeServer.removeContext("/api/admin/actions/overview");
            runtimeServer.createContext("/api/admin/actions/overview", exchange -> {
                String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
                String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
                if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                    writeJson(exchange, 401, """
                        {"success":false,"message":"Unauthorized"}
                        """);
                    return;
                }
                if (actionsOverviewCalls.incrementAndGet() == 1) {
                    writeJson(exchange, 404, """
                        {"status":"error","code":404,"message":"Application not found"}
                        """);
                    return;
                }
                writeJson(exchange, 200, """
                    {
                      "success": true,
                      "contractVersion": "RUNTIME_ACTION_CATALOG_OVERVIEW_V3",
                      "count": 2,
                      "withPresentationHintsCount": 1,
                      "withBuiltInModuleMappingsCount": 1,
                      "withBuiltInCardMappingsCount": 1,
                      "withProvenanceCount": 2,
                      "confirmationInterceptorsCount": 1,
                      "postActionWebhookPoliciesCount": 0,
                      "actionNamesWithPostActionWebhookPolicies": [],
                      "webhookTargetsCount": 0,
                      "webhookTargetIds": [],
                      "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                      "confirmationInterceptorSources": ["%s"],
                      "actions": [
                        {
                          "name": "list_products",
                          "resultPresentationHint": "TABLE",
                          "builtInModuleId": "product-catalog",
                          "builtInCardId": "product-list",
                          "provenance": {
                            "sourceType": "ACTION_CATALOG"
                          }
                        },
                        {
                          "name": "view_cart",
                          "resultPresentationHint": "DEFAULT",
                          "provenance": {
                            "sourceType": "ACTION_CATALOG"
                          }
                        }
                      ]
                    }
                    """.formatted(artifacts.actionsArtifactUrl()));
            });
            runtimeServer.removeContext("/api/admin/indexing/overview");
            runtimeServer.createContext("/api/admin/indexing/overview", exchange -> {
                String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
                String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
                if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                    writeJson(exchange, 401, """
                        {"success":false,"message":"Unauthorized"}
                        """);
                    return;
                }
                if (indexingOverviewCalls.incrementAndGet() == 1) {
                    writeJson(exchange, 404, """
                        {"status":"error","code":404,"message":"Application not found"}
                        """);
                    return;
                }
                writeJson(exchange, 200, """
                    {
                      "success": true,
                      "supportsVectorScan": true,
                      "entityTypes": ["product", "policy"],
                      "countsByEntityType": {
                        "product": 4,
                        "policy": 1
                      },
                      "totalVectors": 5
                    }
                    """);
            });
            runtimeServer.removeContext("/api/admin/connector/actions/overview");
            runtimeServer.createContext("/api/admin/connector/actions/overview", exchange -> {
                String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
                String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
                if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                    writeJson(exchange, 401, """
                        {"success":false,"message":"Unauthorized"}
                        """);
                    return;
                }
                if (connectorActionsOverviewCalls.incrementAndGet() == 1) {
                    writeJson(exchange, 404, """
                        {"status":"error","code":404,"message":"Application not found"}
                        """);
                    return;
                }
                writeJson(exchange, 200, """
                    {
                      "success": true,
                      "count": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """);
            });
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                new PlatformVerificationProperties(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(10),
                    "/actuator/health",
                    "/actuator/health",
                    "/api/admin/connector/health",
                    "/api/admin/overview",
                    "/api/admin/auth/overview",
                    "/api/admin/actions/overview",
                    "/api/admin/indexing/overview",
                    "/api/admin/connector/overview",
                    "/api/admin/connector/actions/overview",
                    true
                ),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = release();

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service, deployment, version, release, "POST_APPLY");

            assertThat(checkStatus(run, "runtime_auth_overview_http_probe")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_actions_overview_http_probe")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "runtime_indexing_overview_http_probe")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "connector_actions_overview_http_probe")).isEqualTo("PASSED");
            assertThat(authOverviewCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(actionsOverviewCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(indexingOverviewCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(connectorActionsOverviewCalls.get()).isGreaterThanOrEqualTo(2);
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyBlocksWhenManagedSecretIsMissing() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                null,
                null,
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(false);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(false);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                java.util.List.of(
                    new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"),
                    new RailwayPreflightCheckSummary("public_base_url", "PASSED", "Public base URL is reachable.", "https://platform.example")
                )
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("actions_artifact_fetch_probe", "PASSED")
                .containsEntry("routing_artifact_fetch_probe", "PASSED")
                .containsEntry("railway_preflight_provisioning_mode", "PASSED")
                .containsEntry("platform_authenticated_runtime_token_creation_ready", "FAILED")
                .containsEntry("runtime_trusted_backend_api_key_available", "FAILED")
                .containsEntry("runtime_private_assertion_signing_key_available", "FAILED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyRunsCoolifyGateAndFailsWhenTargetPreflightFails() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/marketplace-datasets.json", exchange -> writeJson(exchange, 200, "{\"datasets\":[]}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                null,
                null,
                baseUrl + "/artifacts/marketplace-datasets.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
            when(platformSecretService.resolveSecret(anyString())).thenReturn("secret-value");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(localVectorConnectivitySummary());

            DeploymentTargetProfileEntity targetProfile = coolifyTargetProfile();
            DeploymentTargetProfileService targetProfileService = mock(DeploymentTargetProfileService.class);
            when(targetProfileService.requireActiveProfile("dtp-coolify-staging")).thenReturn(targetProfile);
            DeploymentProvisioningProvider coolifyProvider = mock(DeploymentProvisioningProvider.class);
            when(coolifyProvider.preflight(targetProfile)).thenReturn(new DeploymentProviderPreflightSummary(
                "dtp-coolify-staging",
                DeploymentProviderType.COOLIFY,
                "FAILED",
                "Coolify API is not reachable from Platform.",
                "http://coolify.example:8000",
                null,
                List.of("credential_resolved", "coolify_api_failed"),
                objectMapper.createObjectNode().put("errorType", "IllegalStateException"),
                Instant.parse("2026-05-02T00:00:00Z")
            ));
            DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
            when(providerRegistry.require(DeploymentProviderType.COOLIFY)).thenReturn(coolifyProvider);

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentProviderSecretResolutionService(platformSecretService),
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService,
                null,
                null,
                targetProfileService,
                providerRegistry
            );

            DeploymentReleaseEntity release = release();
            release.setProvisioningTarget("COOLIFY");
            release.setProviderType(DeploymentProviderType.COOLIFY);
            release.setTargetProfileId("dtp-coolify-staging");

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release,
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("actions_artifact_fetch_probe", "PASSED")
                .containsEntry("runtime_trusted_backend_api_key_available", "PASSED")
                .containsEntry("provider_connectivity_local_vector_backend", "SKIPPED")
                .containsEntry("provider_preflight", "FAILED")
                .doesNotContainKey("railway_preflight_provisioning_mode")
                .doesNotContainEntry("live_rollout_prerequisites", "SKIPPED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyTreatsManagedQdrantAndZillizSecretsAsPlatformScoped() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                null,
                null,
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");
            when(platformSecretService.isSecretPresent("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ZILLIZ_CLOUD_API_KEY")).thenReturn(true);

            DeploymentProviderSecretResolutionService resolver = mock(DeploymentProviderSecretResolutionService.class);
            when(resolver.resolve(anyString(), anyString(), any())).thenAnswer(invocation -> {
                String deploymentId = invocation.getArgument(0, String.class);
                String secretPurpose = invocation.getArgument(1, String.class);
                if ("QDRANT_CLOUD_MANAGEMENT_API_KEY".equals(secretPurpose)
                    || "ZILLIZ_CLOUD_API_KEY".equals(secretPurpose)) {
                    throw new AssertionError("Management-plane secret should not use deployment override resolution: " + secretPurpose);
                }
                return resolvedSecretValue(deploymentId, secretPurpose);
            });

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platform-V4",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));

            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "qdrant",
                    "PLATFORM_MANAGED",
                    true,
                    "MANAGED_CLUSTER",
                    List.of(),
                    "Managed vector provisioning is configured.",
                    List.of(),
                    "0 ready, 0 blocked, 0 failed, 0 skipped.",
                    List.of()
                )
            );

            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                resolver,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity qdrantRun = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorProvisioningMode": "PLATFORM_MANAGED",
                      "vectorStrategy": "qdrant",
                      "qdrantCloudAccountId": "acct-123",
                      "qdrantCloudProviderId": "aws",
                      "qdrantCloudRegionId": "eu-west-1",
                      "qdrantCloudPackageId": "pkg-123"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            DeploymentVerificationRunEntity milvusRun = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorProvisioningMode": "PLATFORM_MANAGED",
                      "vectorStrategy": "milvus",
                      "zillizCloudProjectId": "proj-123",
                      "zillizCloudRegionId": "aws-eu-central-1",
                      "zillizCloudClusterPlan": "Serverless"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            assertThat(checkStatus(qdrantRun, "qdrant_secret_available")).isEqualTo("PASSED");
            assertThat(checkStatus(milvusRun, "milvus_secret_available")).isEqualTo("PASSED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyFailsWhenExternalVendorConnectivityProbeFails() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                java.util.List.of(
                    new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API")
                )
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "rest",
                    "pinecone",
                    "PLATFORM_MANAGED",
                    true,
                    "MANAGED_SERVERLESS_INDEX",
                    java.util.List.of("dep-123 (aws/eu-west-1)"),
                    "Apply will create or reconcile the Pinecone serverless index for this deployment.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "pinecone_control_plane",
                            "Pinecone control plane",
                            "FAILED",
                            "https://api.pinecone.io/indexes",
                            "Pinecone control plane responded with HTTP 503."
                        )
                    ),
                    "0 ready, 0 blocked, 1 failed, 0 skipped.",
                    java.util.List.of()
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorStrategy": "pinecone"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("provider_connectivity_summary", "FAILED")
                .containsEntry("provider_connectivity_pinecone_control_plane", "FAILED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyFailsWhenSharedRootCrossesCustomerBoundary() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));

            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "pinecone",
                    "EXTERNAL_EXISTING",
                    false,
                    "NONE",
                    List.of(),
                    "Shared index already exists.",
                    List.of(),
                    "0 ready, 0 blocked, 0 failed, 0 skipped.",
                    List.of()
                )
            );

            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(blockedSharedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorStrategy": "pinecone",
                      "vectorStoragePosture": "SHARED"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses).containsEntry("tenant_scoped_shared_storage_boundary", "FAILED");
            assertThat(checks.toString()).contains("must not cross customer boundaries");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyAllowsPlatformManagedSharedQdrantRootWithoutCloudManagementKey() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("QDRANT_CLOUD_MANAGEMENT_API_KEY")).thenReturn(false);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platform-V5",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));

            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "qdrant",
                    "PLATFORM_MANAGED",
                    true,
                    "MANAGED_COLLECTIONS",
                    List.of(),
                    "Platform-managed shared Qdrant root is configured.",
                    List.of(),
                    "1 ready, 0 blocked, 0 failed, 0 skipped.",
                    List.of()
                )
            );

            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(platformManagedSharedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorStrategy": "qdrant",
                      "vectorProvisioningMode": "PLATFORM_MANAGED",
                      "vectorStoragePosture": "SHARED",
                      "qdrantHost": "https://shared-qdrant.platform.internal",
                      "qdrantManagedCollectionsEnabled": true,
                      "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            assertThat(checkStatus(run, "managed_vector_provisioning_ready")).isEqualTo("PASSED");
            assertThat(checkStatus(run, "tenant_scoped_shared_storage_boundary")).isEqualTo("PASSED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyAllowsPlatformManagedSharedStorageRootReuseAcrossCustomers() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                null,
                null,
                baseUrl + "/artifacts/deployment-manifest.json"
            );
            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platform-V5",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));

            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "qdrant",
                    "PLATFORM_MANAGED",
                    true,
                    "MANAGED_CLOUD_CLUSTER",
                    List.of(),
                    "Shared endpoint already exists.",
                    List.of(),
                    "1 ready, 0 blocked, 0 failed, 0 skipped.",
                    List.of()
                )
            );

            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(platformManagedSharedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "openai",
                      "vectorStrategy": "qdrant",
                      "vectorProvisioningMode": "PLATFORM_MANAGED",
                      "vectorStoragePosture": "SHARED"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses).containsEntry("tenant_scoped_shared_storage_boundary", "PASSED");
            assertThat(checks.toString()).doesNotContain("must not cross customer boundaries");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyAllowsPlatformManagedVectorizationRunnerProvisioningBeforeRegistration() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                null,
                null,
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
            when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("trusted-backend-secret");
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("private-assertion-secret");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredManagedVectorizationSummaryWithoutRunner());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses)
                .containsEntry("vectorization_control_plane_ready", "PASSED")
                .containsEntry("vectorization_runner_registration_ready", "PASSED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyFailsWhenCustomerManagedVectorizationRunnerRegistrationIsMissing() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(configuredCustomerManagedVectorizationSummaryWithoutRunner());

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("vectorization_control_plane_ready", "PASSED")
                .containsEntry("vectorization_runner_registration_ready", "FAILED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyAcceptsExpiredRunnerTokenWhenCustomerManagedRunnerSessionIsActive() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                List.of(new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"))
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped.",
                    List.of()
                )
            );
            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
            when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(dedicatedSummary());
            DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
            when(deploymentVectorizationVerificationService.build(any(), any())).thenReturn(
                configuredCustomerManagedVectorizationSummary(
                    new VectorizationRunnerSummary(
                        "vrr-123",
                        "CUSTOMER_MANAGED_REMOTE",
                        "ACTIVE",
                        "CURRENT",
                        "hint-1234",
                        Instant.parse("2026-04-04T00:00:00Z"),
                        "customer-managed-runner",
                        "2026.04.04",
                        "2026.04",
                        Instant.parse("2026-04-04T00:02:00Z"),
                        Instant.parse("2026-04-04T00:04:00Z"),
                        Instant.parse("2099-04-04T01:04:00Z")
                    )
                )
            );

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                verificationProperties(Duration.ofSeconds(2)),
                platformSecretService,
                new DeploymentConfigCompiler(objectMapper),
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService,
                deploymentTenantScopedVectorService,
                deploymentVectorizationVerificationService
            );

            DeploymentVerificationRunEntity run = verifyWithTargetEvidence(service,
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses)
                .containsEntry("vectorization_control_plane_ready", "PASSED")
                .containsEntry("vectorization_runner_registration_ready", "PASSED");
        } finally {
            artifactServer.stop(0);
        }
    }

    private void registerRuntimeHandlers(HttpServer server, DeploymentArtifactBundleSummary artifacts) {
        registerRuntimeHandlers(
            server,
            artifacts,
            privateRuntimeJsonHandler(
                """
                    {
                      "status": "UP"
                    }
                    """
            )
        );
    }

    private void registerDocumentConnectorStatusHandler(HttpServer server,
                                                        boolean ready,
                                                        String connectorType,
                                                        String bindingRef,
                                                        AtomicReference<JsonNode> assertionClaims) {
        server.createContext("/api/documents/source-connector/status", exchange -> {
            String trustedBackend = exchange.getRequestHeaders()
                .getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
            String authorization = exchange.getRequestHeaders()
                .getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
            if (!"trusted-backend-secret".equals(trustedBackend)
                || authorization == null
                || !authorization.startsWith("Bearer rpa1.")) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            try {
                String[] tokenParts = authorization.substring("Bearer ".length()).split("\\.");
                if (tokenParts.length != 3) {
                    throw new IllegalArgumentException("Malformed private assertion");
                }
                JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(tokenParts[1]));
                assertionClaims.set(claims);
                List<String> scopes = StreamSupport.stream(claims.path("scopes").spliterator(), false)
                    .map(JsonNode::asText)
                    .toList();
                if (!scopes.equals(List.of(RuntimePrivateAccessSupport.SCOPE_DOCUMENTS_READ))) {
                    writeJson(exchange, 403, """
                        {"success":false,"message":"Insufficient scope"}
                        """);
                    return;
                }
            } catch (Exception ex) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Invalid private assertion"}
                    """);
                return;
            }
            writeJson(exchange, 200, """
                {
                  "ready": %s,
                  "connectorType": "%s",
                  "bindingRef": "%s",
                  "scopeDigest": "scope-digest-123",
                  "errorCode": ""
                }
                """.formatted(ready, connectorType, bindingRef));
        });
    }

    private void registerActiveDocumentEvidenceHandlers(HttpServer server) {
        server.createContext("/api/documents/sources/doc-source-1/preview", privateRuntimeJsonHandler("""
            {
              "source": {"sourceId":"doc-source-1","datasetId":"documents-primary","status":"ACTIVE","activeVersion":1},
              "chunks": [{"contentPreview":"Approved collection policy allows a customer to arrange collection after identity verification."}]
            }
            """));
        server.createContext("/api/documents/sources", privateRuntimeJsonHandler("""
            [
              {
                "sourceId":"doc-source-1",
                "datasetId":"documents-primary",
                "status":"ACTIVE",
                "activeVersion":1
              }
            ]
            """));
        server.createContext("/api/documents/retrieval-proof", privateRuntimeJsonHandler("""
            {
              "query":"redacted by the verifier",
              "evidenceCount":1,
              "evidence":[
                {
                  "sourceId":"doc-source-1",
                  "sourceVersion":1,
                  "metadata":{
                    "datasetId":"documents-primary",
                    "deploymentId":"dep-123",
                    "targetProfileId":"dtp-coolify-staging",
                    "releaseId":"rel-123"
                  }
                }
              ]
            }
            """));
    }

    private void registerRuntimeOnlyImageHandlers(HttpServer server, DeploymentArtifactBundleSummary artifacts) {
        registerRuntimeHandlers(server, artifacts);
        server.removeContext("/api/admin/overview");
        server.removeContext("/api/admin/actions/overview");
        server.removeContext("/api/admin/indexing/overview");
        server.removeContext("/api/admin/connector/health");
        server.removeContext("/api/admin/connector/overview");
        server.removeContext("/api/admin/connector/actions/overview");

        server.createContext(
            "/api/admin/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "aiFabricFrameworkVersion": "0.8.38",
                      "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
                      "entityConfigHash": "entity-hash-123",
                      "deploymentVersionId": "ver-123",
                      "deploymentBehavior": {
                        "type": "CONVERSATIONAL",
                        "schemaVersion": "loomai-deployment-behavior-v1",
                        "contractVersion": 1,
                        "compositionHash": "",
                        "specialistChainsEnabled": false
                      },
                      "runtimeMigrations": {"appliedMigrationIds": ["loomai-document-ingestion-v1"]},
                      "runtimeCapabilityManifestHash": "",
                      "productSourceCommit": "",
                      "aifabricEntities": {
                        "available": true,
                        "queue": {"ready": true}
                      },
                      "entityConfigLocation": "%s",
                      "promptConfigLocation": "%s",
                      "knowledgeSourceConfigLocation": "",
                      "shellConfigLocation": "",
                      "actionCatalogSources": [
                        {"type": "FILE", "path": "%s", "optional": false}
                      ],
                      "actionsCount": 0,
                      "confirmationInterceptorsCount": 0,
                      "postActionWebhookPoliciesCount": 0,
                      "actionNamesWithPostActionWebhookPolicies": [],
                      "webhookTargetsCount": 0,
                      "webhookTargetIds": [],
                      "confirmationInterceptorRuleNames": [],
                      "confirmationInterceptorSources": ["%s"],
                      "knowledgeSourcesCount": 0,
                      "knowledgeSourceIds": [],
                      "knowledgeSourceTypes": [],
                      "knowledgeSourceAdapterTypes": [],
                      "shellModulesCount": 0,
                      "shellModuleIds": [],
                      "shellCardsCount": 0,
                      "shellCardIds": [],
                      "shellStarterPromptsCount": 0,
                      "shellGreetingConfigured": false,
                      "supportedEntityTypes": [],
                      "inferenceProfile": {
                        "llmProvider": "openai",
                        "embeddingProvider": "openai"
                      },
                      "marketplaceSupport": {
                        "knowledgeSourceContractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                        "shellConfigContractVersion": "SHELL_CONFIG_V1",
                        "inferenceProfileContractVersion": "INFERENCE_PROFILE_RUNTIME_V1"
                      }
                    }
                    """.formatted(
                        artifacts.entityArtifactUrl(),
                        artifacts.promptArtifactUrl(),
                        artifacts.actionsArtifactUrl(),
                        artifacts.actionsArtifactUrl()
                    )
            )
        );
        server.createContext(
            "/api/admin/actions/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "contractVersion": "RUNTIME_ACTION_CATALOG_OVERVIEW_V3",
                      "count": 0,
                      "withPresentationHintsCount": 0,
                      "withBuiltInModuleMappingsCount": 0,
                      "withBuiltInCardMappingsCount": 0,
                      "withProvenanceCount": 0,
                      "confirmationInterceptorsCount": 0,
                      "postActionWebhookPoliciesCount": 0,
                      "actionNamesWithPostActionWebhookPolicies": [],
                      "webhookTargetsCount": 0,
                      "webhookTargetIds": [],
                      "confirmationInterceptorRuleNames": [],
                      "confirmationInterceptorSources": ["%s"],
                      "actions": []
                    }
                    """.formatted(artifacts.actionsArtifactUrl())
            )
        );
        server.createContext(
            "/api/admin/indexing/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "supportsVectorScan": true,
                      "entityTypes": [],
                      "countsByEntityType": {},
                      "totalVectors": 0
                    }
                    """
            )
        );
    }

    private void registerRuntimeHandlers(HttpServer server,
                                         DeploymentArtifactBundleSummary artifacts,
                                         HttpHandler connectorHealthHandler) {
        server.createContext("/actuator/health", exchange -> writeJson(exchange, 200, """
            {"status":"UP"}
            """));
        server.createContext(
            "/api/admin/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "aiFabricFrameworkVersion": "0.8.38",
                      "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
                      "entityConfigHash": "entity-hash-123",
                      "deploymentVersionId": "ver-123",
                      "deploymentBehavior": {
                        "type": "CONVERSATIONAL",
                        "schemaVersion": "loomai-deployment-behavior-v1",
                        "contractVersion": 1,
                        "compositionHash": "",
                        "specialistChainsEnabled": false
                      },
                      "runtimeMigrations": {"appliedMigrationIds": ["loomai-document-ingestion-v1"]},
                      "runtimeCapabilityManifestHash": "",
                      "productSourceCommit": "",
                      "aifabricEntities": {
                        "available": true,
                        "queue": {"ready": true}
                      },
                      "entityConfigLocation": "%s",
                      "promptConfigLocation": "%s",
                      "knowledgeSourceConfigLocation": "%s",
                      "shellConfigLocation": "%s",
                      "actionCatalogSources": [
                        {
                          "type": "FILE",
                          "path": "%s",
                          "optional": false
                        }
                      ],
                      "actionsCount": 2,
                      "confirmationInterceptorsCount": 1,
                      "postActionWebhookPoliciesCount": 0,
                      "actionNamesWithPostActionWebhookPolicies": [],
                      "webhookTargetsCount": 0,
                      "webhookTargetIds": [],
                      "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                      "confirmationInterceptorSources": ["%s"],
                      "knowledgeSourcesCount": 1,
                      "knowledgeSourceIds": ["shared-policies"],
                      "knowledgeSourceTypes": ["policy"],
                      "knowledgeSourceAdapterTypes": ["shared-index"],
                      "shellModulesCount": 2,
                      "shellModuleIds": ["product-catalog", "policies"],
                      "shellCardsCount": 1,
                      "shellCardIds": ["policy-summary"],
                      "shellStarterPromptsCount": 2,
                      "shellGreetingConfigured": true,
                      "supportedEntityTypes": ["product", "policy"],
                      "inferenceProfile": {
                        "llmProvider": "openai",
                        "embeddingProvider": "openai"
                      },
                      "marketplaceSupport": {
                        "knowledgeSourceContractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
                        "shellConfigContractVersion": "SHELL_CONFIG_V1",
                        "inferenceProfileContractVersion": "INFERENCE_PROFILE_RUNTIME_V1"
                      },
                      "auth": {
                        "ingressMode": "VERIFIED_CONTEXT_REQUIRED",
                        "verifiedContextRequired": true,
                        "rejectConflictingRequestIdentity": true,
                        "rejectRequestIdentityWhenVerifiedContextPresent": true,
                        "trustedBackendConfigured": true,
                        "privateAssertionValidationConfigured": true,
                        "privateAssertionAcceptedIssuers": ["platform-runtime:SESSION", "platform-runtime:API_KEY", "platform-poc:SESSION", "platform-poc:API_KEY", "platform-poc:SYSTEM", "platform-release-verification", "platform-document-operations", "platform-marketplace-dataset-sync", "platform-vectorization-runner", "platform-vectorization-verification", "platform-runtime-coverage"],
                        "privateAssertionAcceptedAudiences": ["dep-123"],
                        "publicTokenValidationConfigured": false,
                        "publicAuthorizationHeader": "Authorization",
                        "publicTokenScheme": "Bearer",
                        "publicTokenIssuer": "runtime-public-bootstrap",
                        "publicAcceptedIssuers": [],
                        "publicAcceptedAudiences": [],
                        "publicDefaultAudience": "",
                        "publicBootstrap": {
                          "enabled": false,
                          "allowMissingOrigin": false,
                          "allowedOrigins": []
                        }
                      }
                    }
                    """.formatted(
                        artifacts.entityArtifactUrl(),
                        artifacts.promptArtifactUrl(),
                        artifacts.knowledgeSourceArtifactUrl() == null ? "" : artifacts.knowledgeSourceArtifactUrl(),
                        artifacts.shellArtifactUrl() == null ? "" : artifacts.shellArtifactUrl(),
                        artifacts.actionsArtifactUrl(),
                        artifacts.actionsArtifactUrl()
                    )
            )
        );
        server.createContext(
            "/api/admin/auth/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "contractVersion": "RUNTIME_AUTH_OVERVIEW_V1",
                      "auth": {
                        "ingressMode": "VERIFIED_CONTEXT_REQUIRED",
                        "verifiedContextRequired": true,
                        "rejectConflictingRequestIdentity": true,
                        "rejectRequestIdentityWhenVerifiedContextPresent": true,
                        "trustedBackendConfigured": true,
                        "privateAssertionValidationConfigured": true,
                        "privateAssertionAcceptedIssuers": ["platform-runtime:SESSION", "platform-runtime:API_KEY", "platform-poc:SESSION", "platform-poc:API_KEY", "platform-poc:SYSTEM", "platform-release-verification", "platform-document-operations", "platform-marketplace-dataset-sync", "platform-vectorization-runner", "platform-vectorization-verification", "platform-runtime-coverage"],
                        "privateAssertionAcceptedAudiences": ["dep-123"],
                        "publicTokenValidationConfigured": false,
                        "publicAuthorizationHeader": "Authorization",
                        "publicTokenScheme": "Bearer",
                        "publicTokenIssuer": "runtime-public-bootstrap",
                        "publicAcceptedIssuers": [],
                        "publicAcceptedAudiences": [],
                        "publicDefaultAudience": "",
                        "publicBootstrap": {
                          "enabled": false,
                          "allowMissingOrigin": false,
                          "allowedOrigins": []
                        }
                      },
                      "warnings": [],
                      "warningCount": 0
                    }
                    """
            )
        );
        server.createContext(
            "/api/admin/actions/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "contractVersion": "RUNTIME_ACTION_CATALOG_OVERVIEW_V3",
                      "count": 2,
                      "withPresentationHintsCount": 1,
                      "withBuiltInModuleMappingsCount": 1,
                      "withBuiltInCardMappingsCount": 1,
                      "withProvenanceCount": 2,
                      "confirmationInterceptorsCount": 1,
                      "postActionWebhookPoliciesCount": 0,
                      "actionNamesWithPostActionWebhookPolicies": [],
                      "webhookTargetsCount": 0,
                      "webhookTargetIds": [],
                      "confirmationInterceptorRuleNames": ["offer_cart_retention"],
                      "confirmationInterceptorSources": ["%s"],
                      "actions": [
                        {
                          "name": "list_products",
                          "resultPresentationHint": "TABLE",
                          "builtInModuleId": "product-catalog",
                          "builtInCardId": "product-list",
                          "provenance": {
                            "sourceType": "ACTION_CATALOG"
                          }
                        },
                        {
                          "name": "view_cart",
                          "resultPresentationHint": "DEFAULT",
                          "provenance": {
                            "sourceType": "ACTION_CATALOG"
                          }
                        }
                      ]
                    }
                    """.formatted(artifacts.actionsArtifactUrl())
            )
        );
        server.createContext(
            "/api/admin/indexing/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "supportsVectorScan": true,
                      "entityTypes": ["product", "policy"],
                      "countsByEntityType": {
                        "product": 4,
                        "policy": 1
                      },
                      "totalVectors": 5
                    }
                    """
            )
        );
        server.createContext("/api/admin/connector/health", connectorHealthHandler);
        server.createContext(
            "/api/admin/connector/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "routingConfigLocation": "%s",
                      "connector": {
                        "inboundAuth": {
                          "apiKey": {
                            "valueConfigured": true
                          }
                        }
                      },
                      "runtimeProxy": {
                        "enabled": true,
                        "baseUrl": "https://runtime.internal"
                      },
                      "authz": {
                        "enabled": true,
                        "path": "/api/authz/check",
                        "upstream": {
                          "baseUrl": "https://commerce.example"
                        }
                      },
                      "actionsCount": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """.formatted(artifacts.routingArtifactUrl())
            )
        );
        server.createContext(
            "/api/admin/connector/actions/overview",
            privateRuntimeJsonHandler(
                """
                    {
                      "success": true,
                      "count": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """
            )
        );
    }

    private void registerConnectorHandlers(HttpServer server, DeploymentArtifactBundleSummary artifacts) {
        server.createContext("/actuator/health", exchange -> writeJson(exchange, 200, """
            {"status":"UP"}
            """));
        server.createContext(
            "/api/admin/overview",
            jsonHandler(
                "X-AIFABRIC-RUNTIME-API-KEY",
                "trusted-backend-secret",
                """
                    {
                      "success": true,
                      "routingConfigLocation": "%s",
                      "connector": {
                        "inboundAuth": {
                          "apiKey": {
                            "valueConfigured": true
                          }
                        }
                      },
                      "runtimeProxy": {
                        "enabled": true,
                        "baseUrl": "https://runtime.internal"
                      },
                      "authz": {
                        "enabled": true,
                        "path": "/api/authz/check",
                        "upstream": {
                          "baseUrl": "https://commerce.example"
                        }
                      },
                      "actionsCount": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """.formatted(artifacts.routingArtifactUrl())
            )
        );
        server.createContext(
            "/api/admin/actions/overview",
            jsonHandler(
                "X-AIFABRIC-RUNTIME-API-KEY",
                "trusted-backend-secret",
                """
                    {
                      "success": true,
                      "count": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """
            )
        );
    }

    private HttpHandler jsonHandler(String requiredHeader, String requiredValue, String body) {
        return exchange -> {
            String actual = exchange.getRequestHeaders().getFirst(requiredHeader);
            if (!requiredValue.equals(actual)) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            writeJson(exchange, 200, body);
        };
    }

    private HttpHandler privateRuntimeJsonHandler(String body) {
        return exchange -> {
            String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
            String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
            if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            writeJson(exchange, 200, body);
        };
    }

    private HttpHandler privateRuntimeJsonHandler(RuntimeBodySupplier bodySupplier) {
        return exchange -> {
            String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
            String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
            if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            writeJson(exchange, 200, bodySupplier.body());
        };
    }

    private HttpHandler delayedPrivateRuntimeJsonHandler(long delayMillis, String body) {
        return exchange -> {
            String trustedBackend = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);
            String privateAuthorization = exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER);
            if (!"trusted-backend-secret".equals(trustedBackend) || privateAuthorization == null || !privateAuthorization.startsWith("Bearer rpa1.")) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, """
                    {"success":false,"message":"Interrupted"}
                    """);
                return;
            }
            writeJson(exchange, 200, body);
        };
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        } finally {
            exchange.close();
        }
    }

    private DeploymentEntity deployment(String runtimeBaseUrl, String connectorBaseUrl) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Sample Commerce Dev");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl(runtimeBaseUrl);
        deployment.setConnectorBaseUrl(connectorBaseUrl);
        deployment.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return deployment;
    }

    private DeploymentArtifactBundleSummary artifactBundle() {
        return new DeploymentArtifactBundleSummary(
            "dep-123",
            "ver-123",
            "v1",
            "hash-123",
            "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
            "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
            "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
            "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
            "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
        );
    }

    private PlatformSecretService runtimePrivateSecrets() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("trusted-backend-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
            .thenReturn("private-assertion-secret");
        when(platformSecretService.isSecretPresent(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
            .thenReturn(true);
        return platformSecretService;
    }

    private DeploymentReleaseVerificationService releaseVerificationService(
        PlatformSecretService platformSecretService,
        DeploymentArtifactService artifactService
    ) {
        DeploymentVectorizationVerificationService vectorizationVerificationService =
            mock(DeploymentVectorizationVerificationService.class);
        when(vectorizationVerificationService.build(any(), any())).thenReturn(notConfiguredVectorizationSummary());
        return new DeploymentReleaseVerificationService(
            objectMapper,
            verificationProperties(Duration.ofMillis(100)),
            platformSecretService,
            new DeploymentConfigCompiler(objectMapper),
            artifactService,
            mock(RailwayPreflightService.class),
            mock(DeploymentProviderConnectivityService.class),
            mock(DeploymentTenantScopedVectorService.class),
            vectorizationVerificationService
        );
    }

    private DeploymentVersionEntity version() {
        return version("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai"
            }
            """);
    }

    private DeploymentVersionEntity version(String providerConfigJson) {
        return version(providerConfigJson, """
            {
              "adminApiKeyEnabled": true
            }
            """);
    }

    private DeploymentVersionEntity version(String providerConfigJson, String securityConfigJson) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("drf-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setAiFabricFrameworkVersion(AI_FABRIC_VERSION);
        version.setEntityConfigContractVersion(
            ENTITY_CONFIG_CONTRACT_VERSION
        );
        version.setReindexRequired(false);
        version.setActionsConfigJson("""
            {
              "actions": [
                {
                  "name": "list_products",
                  "accessMode": "READ",
                  "resultPresentationHint": "TABLE",
                  "builtInModuleId": "product-catalog",
                  "builtInCardId": "product-list",
                  "provenance": {
                    "sourceType": "ACTION_CATALOG"
                  }
                },
                {
                  "name": "view_cart",
                  "accessMode": "READ"
                }
              ],
              "confirmationInterceptors": [
                {
                  "name": "offer_cart_retention",
                  "when": {
                    "confirmation": "NO"
                  },
                  "then": {
                    "type": "REPLY",
                    "message": "Would you like to keep your cart instead?"
                  }
                }
              ]
            }
            """);
        version.setKnowledgeSourceConfigJson("""
            {
              "contractVersion": "KNOWLEDGE_SOURCE_CONFIG_V1",
              "sources": [
                {
                  "id": "shared-policies",
                  "type": "policy",
                  "adapterType": "shared-index"
                }
              ]
            }
            """);
        version.setShellConfigJson("""
            {
              "contractVersion": "SHELL_CONFIG_V1",
              "modules": [
                {"id": "product-catalog"},
                {"id": "policies"}
              ],
              "cards": [
                {"id": "policy-summary"}
              ],
              "greeting": {
                "title": "Commerce Assistant",
                "message": "How can I help?"
              },
              "starterPrompts": [
                {"label": "Show products", "query": "Show products", "moduleId": "product-catalog"},
                {"label": "Explain returns", "query": "Explain return policy", "cardId": "policy-summary"}
              ]
            }
            """);
        version.setEntityConfigJson("""
            {
              "ai-entities": {
                "product": {},
                "policy": {}
              }
            }
            """);
        version.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {
                  "allow-unauthenticated": false,
                  "api-key": {
                    "enabled": true,
                    "header": "X-AIFABRIC-API-KEY",
                    "value": "ignored"
                  }
                }
              },
              "authz": {
                "enabled": true,
                "path": "/api/authz/check",
                "upstream": {
                  "base-url": "https://commerce.example"
                }
              },
              "actions": {
                "list_products": {},
                "view_cart": {}
              }
            }
            """);
        version.setProviderConfigJson(providerConfigJson);
        version.setSecurityConfigJson(securityConfigJson);
        version.setActionsArtifactYaml("actions: []");
        version.setEntityArtifactYaml("ai-entities: {}");
        version.setRoutingArtifactYaml("actions: {}");
        version.setManifestJson("""
            {
              "deploymentId": "dep-123",
              "versionId": "ver-123",
              "aiFabricFrameworkVersion": "%s",
              "entityConfigContractVersion": "%s",
              "entityConfigHash": "%s"
            }
            """.formatted(
                AI_FABRIC_VERSION,
                ENTITY_CONFIG_CONTRACT_VERSION,
                ENTITY_CONFIG_HASH
            ));
        version.setPublishedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return version;
    }

    private DeploymentVersionEntity runtimeOnlyImageVersion() {
        DeploymentVersionEntity version = version();
        version.setActionsConfigJson("""
            {"actions":[],"confirmationInterceptors":[],"webhookTargets":[]}
            """);
        version.setEntityConfigJson("""
            {"ai-entities":{}}
            """);
        version.setRoutingConfigJson("""
            {
              "connector":{"inbound-auth":{"allow-unauthenticated":false}},
              "authz":{"enabled":false},
              "actions":{}
            }
            """);
        version.setKnowledgeSourceConfigJson(null);
        version.setShellConfigJson(null);
        return version;
    }

    private DeploymentVersionEntity documentEnabledRuntimeOnlyVersion() {
        DeploymentVersionEntity version = runtimeOnlyImageVersion();
        version.setMarketplaceDatasetConfigJson("""
            {
              "contractVersion": "MARKETPLACE_DATASET_CONFIG_V1",
              "datasets": [
                {
                  "datasetId": "documents-primary",
                  "handleRef": "documents-primary",
                  "datasetHash": "document-dataset-hash-123",
                  "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE",
                  "sourceConnector": {
                    "connectorType": "S3_COMPATIBLE_OBJECT_STORAGE",
                    "bindingRef": "documents-primary"
                  }
                }
              ]
            }
            """);
        return version;
    }

    private DeploymentVerificationRunEntity verifyWithTargetEvidence(
        DeploymentReleaseVerificationService service,
        DeploymentEntity deployment,
        DeploymentVersionEntity version,
        DeploymentReleaseEntity release,
        String verificationType
    ) throws Exception {
        String summaryRuntimeUrl = deployment.getRuntimeBaseUrl();
        String summaryConnectorUrl = deployment.getConnectorBaseUrl();
        String summaryVersion = deployment.getActiveVersionId();
        if (!"PRE_APPLY".equalsIgnoreCase(verificationType)) {
            var evidence = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(release.getProvisioningDetailsJson());
            evidence.put("runtimeBaseUrl", deployment.getRuntimeBaseUrl());
            evidence.put("connectorBaseUrl", deployment.getConnectorBaseUrl());
            release.setProvisioningDetailsJson(evidence.toString());
            release.setTargetProfileId("dtp-coolify-staging");
            deployment.setRuntimeBaseUrl("http://127.0.0.1:1");
            deployment.setConnectorBaseUrl("http://127.0.0.1:1");
            deployment.setActiveVersionId("summary-points-to-another-version");
        }
        try {
            return service.verify(deployment, version, release, verificationType);
        } finally {
            deployment.setRuntimeBaseUrl(summaryRuntimeUrl);
            deployment.setConnectorBaseUrl(summaryConnectorUrl);
            deployment.setActiveVersionId(summaryVersion);
        }
    }

    @Test
    void missingReleaseEndpointEvidenceDoesNotProbeTheDeploymentSummary() throws Exception {
        DeploymentArtifactService artifacts = mock(DeploymentArtifactService.class);
        when(artifacts.toBundleSummary(any())).thenReturn(artifactBundle());
        var service = releaseVerificationService(mock(PlatformSecretService.class), artifacts);
        var release = release();
        release.setTargetProfileId("dtp-coolify-staging");
        var deployment = deployment("https://wrong-production-summary.example", null);
        var result = service.verify(deployment, version(), release, "POST_APPLY");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        JsonNode checks = objectMapper.readTree(result.getChecksJson());
        assertThat(checks.size()).isEqualTo(1);
        assertThat(checks.get(0).path("name").asText()).isEqualTo("release_target_context");
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("ACTIVE");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("COMPLETED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {"projectId":"project-123"}
            """);
        release.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return release;
    }

    private DeploymentReleaseEntity runtimeOnlyImageRelease() {
        DeploymentReleaseEntity release = release();
        release.setProvisioningTarget("COOLIFY");
        release.setProvisioningDetailsJson("""
            {
              "sourceStrategy":"IMAGE_SOURCE",
              "effectiveProviderConfig":{
                "llmProvider":"openai",
                "embeddingProvider":"openai"
              }
            }
            """);
        return release;
    }

    private DeploymentTargetProfileEntity coolifyTargetProfile() {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        profile.setName("Coolify staging");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("staging");
        profile.setActive(true);
        profile.setDefaultForRuntime(true);
        profile.setDefaultForRestartableServices(true);
        profile.setSourceStrategy("GIT_SOURCE");
        profile.setProviderConfigJson("{}");
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(Instant.parse("2026-05-02T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-02T00:00:00Z"));
        return profile;
    }

    private String checkStatus(DeploymentVerificationRunEntity run, String checkName) throws Exception {
        JsonNode checks = objectMapper.readTree(run.getChecksJson());
        for (JsonNode check : checks) {
            if (checkName.equals(check.path("name").asText())) {
                return check.path("status").asText();
            }
        }
        return null;
    }

    private DeploymentProviderSecretResolutionService.ResolvedSecretValue resolvedSecretValue(String deploymentId,
                                                                                              String secretPurpose) {
        return new DeploymentProviderSecretResolutionService.ResolvedSecretValue(
            new DeploymentSecretResolutionSummary(
                deploymentId,
                secretPurpose,
                secretPurpose,
                false,
                true,
                "ALLOW_STANDARD_PRECEDENCE",
                "PLATFORM_SECRET",
                "PLATFORM",
                secretPurpose,
                null,
                false,
                "PLATFORM_SECRET_PRESENT",
                secretPurpose + " is available."
            ),
            "secret-value",
            null
        );
    }

    private DeploymentReleaseEntity releaseWithVectorizationRunner() {
        DeploymentReleaseEntity release = release();
        release.setProvisioningDetailsJson("""
            {
              "projectId":"project-123",
              "railway":{
                "services":{
                  "vectorizationRunner":{
                    "serviceId":"svc-vectorization",
                    "serviceName":"vectorization-runner-dep-123",
                    "deploymentId":"railway-dep-vectorization",
                    "deploymentStatus":"SUCCESS"
                  }
                }
              }
            }
            """);
        return release;
    }

    private DeploymentReleaseEntity releaseWithCoolifyVectorizationRunner() {
        DeploymentReleaseEntity release = release();
        release.setProvisioningDetailsJson("""
            {
              "projectId":"project-123",
              "coolify":{
                "services":{
                  "vectorizationRunner":{
                    "serviceId":"runner-app",
                    "serviceName":"vectorization-runner-dep-123",
                    "deploymentId":"coolify-dep-vectorization",
                    "deploymentStatus":"SUCCESS"
                  }
                }
              }
            }
            """);
        return release;
    }

    private PlatformVerificationProperties verificationProperties(Duration timeout) {
        return new PlatformVerificationProperties(
            timeout,
            null,
            null,
            null,
            "/actuator/health",
            "/actuator/health",
            "/api/admin/connector/health",
            "/api/admin/overview",
            "/api/admin/auth/overview",
            "/api/admin/actions/overview",
            "/api/admin/indexing/overview",
            "/api/admin/connector/overview",
            "/api/admin/connector/actions/overview",
            true
        );
    }

    private PlatformVerificationProperties verificationProperties(Duration timeout,
                                                                  Duration runtimeIndexingOverviewTimeout) {
        return new PlatformVerificationProperties(
            timeout,
            runtimeIndexingOverviewTimeout,
            null,
            null,
            "/actuator/health",
            "/actuator/health",
            "/api/admin/connector/health",
            "/api/admin/overview",
            "/api/admin/auth/overview",
            "/api/admin/actions/overview",
            "/api/admin/indexing/overview",
            "/api/admin/connector/overview",
            "/api/admin/connector/actions/overview",
            true
        );
    }

    private DeploymentTenantScopedVectorSummary dedicatedSummary() {
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            "lucene",
            "LOCAL_MANAGED",
            "DEDICATED",
            false,
            "RUNTIME_LOCAL_STORAGE",
            null,
            null,
            null,
            null,
            "DEDICATED_RESOURCE",
            null,
            null,
            null,
            null,
            null,
            false,
            "editable",
            "local",
            new DeploymentTenantScopedVectorRegistrySummary(
                "INFO",
                null,
                0,
                0,
                Instant.parse("2026-04-03T00:00:00Z"),
                "INFO",
                "No shared handles.",
                "No shared handles."
            ),
            "Dedicated storage."
        );
    }

    private DeploymentProviderConnectivitySummary localVectorConnectivitySummary() {
        return new DeploymentProviderConnectivitySummary(
            "dep-123",
            "Sample Commerce Dev",
            "openai",
            "openai",
            "lucene",
            "LOCAL_MANAGED",
            false,
            "NONE",
            List.of(),
            "Platform-managed external vector provisioning is not enabled for this draft.",
            List.of(
                new DeploymentProviderConnectivityProbeSummary(
                    "local_vector_backend",
                    "Local vector backend",
                    "SKIPPED",
                    "lucene",
                    "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                )
            ),
            "0 ready, 0 blocked, 0 failed, 1 skipped.",
            List.of()
        );
    }

    private DeploymentTenantScopedVectorSummary blockedSharedSummary() {
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            "pinecone",
            "EXTERNAL_EXISTING",
            "SHARED",
            true,
            "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
            "cust-acme",
            "Acme",
            "ten-retail",
            "Retail",
            "NAMESPACE_PREFIX",
            "Index",
            "shared-index",
            "cust-acme--ten-retail",
            null,
            "cust-acme--ten-retail__<entity-type>",
            false,
            "editable",
            "provider-owned",
            new DeploymentTenantScopedVectorRegistrySummary(
                "BLOCKED",
                "tsv-123",
                1,
                0,
                Instant.parse("2026-04-03T00:00:00Z"),
                "BLOCKED",
                "Customer boundary conflict.",
                "Shared index 'shared-index' is already active for customer cust-other. Shared vector infrastructure must not cross customer boundaries."
            ),
            "Shared storage is configured."
        );
    }

    private DeploymentTenantScopedVectorSummary platformManagedSharedSummary() {
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            "qdrant",
            "PLATFORM_MANAGED",
            "SHARED",
            true,
            "PLATFORM_MANAGED_SHARED_RESOURCE",
            "cust-acme",
            "Acme",
            "ten-retail",
            "Retail",
            "COLLECTION_PREFIX",
            "Endpoint",
            "shared-qdrant.platform.internal",
            "cust-acme--ten-retail",
            null,
            "cust-acme--ten-retail__<entity-type>",
            false,
            "editable",
            "platform-managed shared backup posture",
            new DeploymentTenantScopedVectorRegistrySummary(
                "WARNING",
                null,
                0,
                0,
                Instant.parse("2026-04-03T00:00:00Z"),
                "INFO",
                "No shared handles.",
                "The tenant-scoped shared handle resolves cleanly, but no registry record exists yet. Apply the deployment to register or reconcile it."
            ),
            "Shared storage is configured."
        );
    }

    private DeploymentVectorizationVerificationSummary notConfiguredVectorizationSummary() {
        return new DeploymentVectorizationVerificationSummary(
            "dep-123",
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of("policy", "product"),
            List.of(),
            null,
            null,
            null
        );
    }

    private DeploymentVectorizationVerificationSummary externalDocumentVectorizationSummary(List<String> entityScope) {
        return new DeploymentVectorizationVerificationSummary(
            "dep-123",
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of("document"),
            entityScope,
            null,
            new VectorizationPlanSummary(
                "vpl-document",
                "dep-123",
                "Document ingestion vectorization",
                "ACTIVE",
                "PLATFORM_MANAGED_AUTO",
                "BOOTSTRAP_REQUIRED",
                List.of("BOOTSTRAP_REQUIRED"),
                json("{}"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-09-26T00:00:00Z"),
                Instant.parse("2026-09-26T00:00:00Z")
            ),
            null
        );
    }

    private DeploymentVectorizationVerificationSummary configuredManagedVectorizationSummary() {
        return configuredManagedVectorizationSummaryWithRunner(
            new VectorizationRunnerSummary(
                "vrr-123",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "CURRENT",
                "hint-1234",
                Instant.parse("2030-04-05T00:00:00Z"),
                "vectorization-runner-dep-123",
                "2026.04.04",
                "2026.04",
                Instant.parse("2026-04-04T00:02:00Z"),
                Instant.parse("2026-04-04T00:04:00Z"),
                Instant.parse("2026-04-04T01:04:00Z")
            )
        );
    }

    private DeploymentVectorizationVerificationSummary configuredManagedVectorizationSummaryWithoutRunner() {
        return configuredManagedVectorizationSummaryWithRunner(null);
    }

    private DeploymentVectorizationVerificationSummary configuredCustomerManagedVectorizationSummary(VectorizationRunnerSummary runner) {
        return new DeploymentVectorizationVerificationSummary(
            "dep-123",
            true,
            true,
            true,
            true,
            runner != null,
            true,
            false,
            List.of("policy", "product"),
            List.of("policy", "product"),
            new VectorizationSourceConnectionSummary(
                "vcn-123",
                "dep-123",
                "Commerce API",
                "REST_API",
                "API_KEY",
                "READY",
                json("{}"),
                json("{}"),
                json("""
                    {"countsByEntityType":{"product":4,"policy":1}}
                    """),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            new VectorizationPlanSummary(
                "vpl-123",
                "dep-123",
                "Onboarding vectorization",
                "ACTIVE",
                "CUSTOMER_MANAGED_REMOTE",
                "IN_SYNC",
                List.of("IN_SYNC"),
                json("{}"),
                "hash-123",
                "hash-123",
                "vpr-123",
                "vcn-123",
                "vrn-123",
                "vrn-123",
                null,
                null,
                new VectorizationPlanRevisionSummary(
                    "vpr-123",
                    1,
                    "ACTIVE",
                    "vcn-123",
                    json("""
                        ["policy","product"]
                        """),
                    json("{}"),
                    json("{}"),
                    "hash-123",
                    Instant.parse("2026-04-04T00:00:00Z"),
                    Instant.parse("2026-04-04T00:00:00Z")
                ),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            runner
        );
    }

    private DeploymentVectorizationVerificationSummary configuredCustomerManagedVectorizationSummaryWithoutRunner() {
        return configuredCustomerManagedVectorizationSummary(null);
    }

    private DeploymentVectorizationVerificationSummary configuredManagedVectorizationSummaryWithRunner(VectorizationRunnerSummary runner) {
        return new DeploymentVectorizationVerificationSummary(
            "dep-123",
            true,
            true,
            true,
            true,
            runner != null,
            true,
            true,
            List.of("policy", "product"),
            List.of("policy", "product"),
            new VectorizationSourceConnectionSummary(
                "vcn-123",
                "dep-123",
                "Commerce API",
                "REST_API",
                "API_KEY",
                "READY",
                json("{}"),
                json("{}"),
                json("""
                    {"countsByEntityType":{"product":4,"policy":1}}
                    """),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            new VectorizationPlanSummary(
                "vpl-123",
                "dep-123",
                "Onboarding vectorization",
                "ACTIVE",
                "PLATFORM_MANAGED_AUTO",
                "IN_SYNC",
                List.of("IN_SYNC"),
                json("{}"),
                "hash-123",
                "hash-123",
                "vpr-123",
                "vcn-123",
                "vrn-123",
                "vrn-123",
                null,
                null,
                new VectorizationPlanRevisionSummary(
                    "vpr-123",
                    1,
                    "ACTIVE",
                    "vcn-123",
                    json("""
                        ["policy","product"]
                        """),
                    json("{}"),
                    json("{}"),
                    "hash-123",
                    Instant.parse("2026-04-04T00:00:00Z"),
                    Instant.parse("2026-04-04T00:00:00Z")
                ),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            runner
        );
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    private interface RuntimeBodySupplier {
        String body();
    }
}
