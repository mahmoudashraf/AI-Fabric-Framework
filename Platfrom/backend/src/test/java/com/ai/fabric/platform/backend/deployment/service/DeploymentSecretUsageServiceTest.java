package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.DeploymentSecretPurposeCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentSecretUsageServiceTest {

    @Test
    void requiredTokenBindingDoesNotReportGlobalCredentialsAsReady() {
        var secrets = mock(PlatformSecretService.class);
        when(secrets.listSecrets()).thenReturn(List.of(new PlatformSecretSummary(
            "MILVUS_TOKEN", "Milvus token", "Fake global credential", false, true, "DATABASE", null,
            "GLOBAL", "PLATFORM", "MILVUS_TOKEN", null, false, "KEEP")));
        var resolutions = mock(DeploymentProviderSecretResolutionService.class);
        var service = new DeploymentSecretUsageService(secrets, resolutions, new DeploymentSecretPurposeCatalog(),
            new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)), new ObjectMapper(),
            mock(DeploymentExistingVectorBindingService.class), mock(ManagedDependencyGraphStore.class),
            new ManagedDependencySpecValidator(new ObjectMapper()), mock(ManagedDependencySpecRepository.class));
        var draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED"}
            """);
        var result = service.build("dep-fake", draft);
        assertThat(result.secrets()).filteredOn(item -> item.secretPurpose() != null && item.secretPurpose().startsWith("MILVUS"))
            .singleElement().satisfies(item -> {
                assertThat(item.secretPurpose()).isEqualTo("MILVUS_TOKEN");
                assertThat(item.required()).isTrue();
                assertThat(item.present()).isFalse();
                assertThat(item.status()).isEqualTo("MISSING");
                assertThat(item.source()).isEqualTo("DEPLOYMENT_MANAGED");
            });
        org.mockito.Mockito.verify(resolutions, org.mockito.Mockito.never()).summarize("dep-fake", "MILVUS_TOKEN");
        draft.setProviderConfigJson("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusRuntimeTokenSecretName":"MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET"}
            """);
        when(secrets.isSecretPresent("MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET")).thenReturn(true);
        assertThat(service.build("dep-fake", draft).secrets()).filteredOn(item -> "MILVUS_TOKEN".equals(item.secretPurpose()))
            .singleElement().satisfies(item -> assertThat(item.present()).isFalse());
        org.mockito.Mockito.verify(secrets, org.mockito.Mockito.never()).resolveSecret(org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void unchangedDraftUsesLocalBindingForExactPublishedVersionAndTarget(String authType) throws Exception {
        var fixture = new BindingFixture(authType);
        String unchangedDraft = fixture.mapper.writeValueAsString(fixture.draft);
        var summary = fixture.scopedSummary();

        assertBinding(summary, authType, true);
        assertThat(summary.missingRequiredCount()).isZero();
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> {
                assertThat(item.summaryMessage()).contains("target target-staging", "published version ver-fake", "Provider health has not been checked");
                assertThat(item.effectiveResolution()).isNull();
            });
        assertThat(fixture.mapper.writeValueAsString(fixture.draft)).isEqualTo(unchangedDraft);
        assertThat(fixture.draft.getProviderConfigJson()).doesNotContain("RuntimeTokenSecretName", "RuntimeUsernameSecretName", "RuntimePasswordSecretName");
        assertThat(fixture.version.getSourceDraftId()).isNotEqualTo(fixture.draft.getId());
        verify(fixture.bindings).resolveLocal(fixture.deployment, fixture.version,
            fixture.mapper.readTree(fixture.version.getProviderConfigJson()), "target-staging");
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void unscopedSummaryCannotInferBindingFromDraftReferencesOrGlobalCredentials(String authType) {
        var fixture = new BindingFixture(authType);
        var summary = fixture.service.build(fixture.deployment.getId(), fixture.draft);
        assertBinding(summary, authType, false);
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> assertThat(item.summaryMessage()).contains("explicit published version and target"));
        verifyNoInteractions(fixture.bindings);
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void explicitProductionTargetUsesItsOwnBindingAndDoesNotClaimBootstrapOrProviderHealth(String authType) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-production")))
            .thenReturn(fixture.effectiveConfig);
        var summary = fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-production");
        assertBinding(summary, authType, true);
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> assertThat(item.summaryMessage()).contains("target target-production", "Provider health has not been checked")
                .doesNotContain("target-staging"));
        verify(fixture.bindings).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-production"));
        verify(fixture.bindings, never()).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
    }

    @ParameterizedTest
    @CsvSource({
        "TOKEN,EXTERNAL_VECTOR_BINDING_REQUIRED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_REQUIRED",
        "TOKEN,EXTERNAL_VECTOR_BINDING_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_CHANGED",
        "TOKEN,EXTERNAL_VECTOR_BINDING_SCOPE_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_SCOPE_CHANGED",
        "TOKEN,EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_CHANGED"
    })
    void expectedLocalBindingDenialsAreMissingAndNeverExposeFailureDetails(String authType, String diagnostic) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging")))
            .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, diagnostic + ": synthetic-private-detail"));
        var summary = fixture.scopedSummary();
        assertBinding(summary, authType, false);
        assertThat(summary.missingRequiredCount()).isEqualTo(1);
        assertThat(summary.toString()).doesNotContain("synthetic-private-detail", diagnostic);
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void wrongTargetCannotReuseWorkingStagingBinding(String authType) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-other")))
            .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "EXTERNAL_VECTOR_BINDING_REQUIRED"));
        assertBinding(fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-other"), authType, false);
        verify(fixture.bindings, never()).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void changedDraftOrWrongVersionScopeCannotReuseLocalEvidence(String authType) {
        var fixture = new BindingFixture(authType);
        fixture.draft.setEntityConfigJson("{\"ai-entities\":{\"changed\":{}}}");
        assertBinding(fixture.scopedSummary(), authType, false);
        fixture.draft.setEntityConfigJson(fixture.version.getEntityConfigJson());
        fixture.version.setDeploymentId("dep-other");
        assertBinding(fixture.scopedSummary(), authType, false);
        verifyNoInteractions(fixture.bindings);
    }

    @Test
    void programmingFailuresAndUnexpectedHttpFailuresAreNotHiddenAsMissingCredentials() {
        var fixture = new BindingFixture("TOKEN");
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging")))
            .thenThrow(new IllegalStateException("synthetic-programming-failure"));
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(IllegalStateException.class).hasMessage("synthetic-programming-failure");
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "synthetic-unexpected-failure"))
            .when(fixture.bindings).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(ResponseStatusException.class).hasMessageContaining("synthetic-unexpected-failure");
    }

    @Test
    void reportsReferencedManagedIntegrationSecretAsPresent() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.listSecrets()).thenReturn(List.of());
        when(secretService.isManagedSecretName("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(true);
        when(secretService.describeSecret("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(new PlatformSecretSummary(
            "MANAGED_CUSTOMER_BACKEND_API_KEY",
            "Customer backend API key",
            "Deployment-managed integration credential.",
            false,
            true,
            "DATABASE",
            "2026-09-30T00:00:00Z",
            "DEPLOYMENT_MANAGED",
            "PLATFORM_MANAGED",
            null,
            "dep-test",
            true,
            "DELETE_WITH_DEPLOYMENT"
        ));

        DeploymentSecretUsageService service = new DeploymentSecretUsageService(
            secretService,
            mock(DeploymentProviderSecretResolutionService.class),
            mock(DeploymentSecretPurposeCatalog.class),
            new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)),
            new ObjectMapper(),
            mock(DeploymentExistingVectorBindingService.class),
            mock(ManagedDependencyGraphStore.class),
            new ManagedDependencySpecValidator(new ObjectMapper()),
            mock(ManagedDependencySpecRepository.class)
        );
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson("{}");
        draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false,\"connectorApiKeyEnabled\":true}");
        draft.setMarketplaceDatasetConfigJson(DeploymentDraftEntity.DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON);
        draft.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {
                  "api-key": {"value": "${CONNECTOR_API_KEY}"}
                },
                "upstream": {
                  "auth": {"value": "${MANAGED_CUSTOMER_BACKEND_API_KEY}"}
                }
              }
            }
            """);

        var result = service.build("dep-test", draft);

        assertThat(result.literalRiskCount()).isZero();
        assertThat(result.secrets())
            .filteredOn(item -> item.secretName().equals("MANAGED_CUSTOMER_BACKEND_API_KEY"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.required()).isTrue();
                assertThat(item.present()).isTrue();
                assertThat(item.status()).isEqualTo("READY");
                assertThat(item.source()).isEqualTo("DATABASE");
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROVISIONED", "READY"})
    void unchangedDraftUsesExactDeclaredDependencySecretReferencesWithoutExposingThem(String graphStatus) throws Exception {
        var fixture = new DependencyFixture();
        fixture.graph.setStatus(graphStatus);
        String draftBefore = fixture.mapper.writeValueAsString(fixture.draft);
        String versionBefore = fixture.mapper.writeValueAsString(fixture.version);

        var summary = fixture.scopedSummary();

        fixture.assertDependencySecrets(summary, true);
        assertThat(summary.missingRequiredCount()).isZero();
        assertThat(summary.literalRiskCount()).isZero();
        assertThat(summary.toString()).contains("target target-synthetic", "published version version-synthetic",
            "Provider health and runtime initialization have not been checked")
            .doesNotContain("MANAGED_TEST_SOURCE_", "private-metadata", "source-application");
        assertThat(fixture.mapper.writeValueAsString(fixture.draft)).isEqualTo(draftBefore);
        assertThat(fixture.mapper.writeValueAsString(fixture.version)).isEqualTo(versionBefore);
        verify(fixture.graphs).find("dep-synthetic", "target-synthetic");
        for (String reference : fixture.references) verify(fixture.secrets).describeSecret(reference);
        for (String name : fixture.names) verify(fixture.secrets, never()).describeSecret(name);
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PLANNED", "PROVISIONING", "INITIALIZING", "BLOCKED", "RETIRING", "RETIRED"})
    void declaredFieldsAndGlobalPlaceholderCredentialsDoNotMakeUnprovisionedDependenciesReady(String status) {
        var fixture = new DependencyFixture();
        fixture.shadowWithGlobalCredentials();
        fixture.graph.setStatus(status);

        var summary = fixture.scopedSummary();

        fixture.assertDependencySecrets(summary, false);
        assertThat(summary.missingRequiredCount()).isEqualTo(3);
        assertThat(summary.toString()).contains("Dependency provisioning is pending or not ready");
        verify(fixture.graphs, never()).state(any());
        for (String reference : fixture.references) verify(fixture.secrets, never()).describeSecret(reference);
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "draft-deployment", "version", "version-deployment", "unpublished", "customer", "tenant",
        "graph-deployment", "graph-target", "archived", "archive-status", "spec-fingerprint"})
    void dependencyEvidenceCannotCrossPublishedDraftOrOwnershipScope(String changed) {
        var fixture = new DependencyFixture();
        fixture.shadowWithGlobalCredentials();
        switch (changed) {
            case "draft" -> fixture.draft.setActionsConfigJson("{\"changed\":true}");
            case "draft-deployment" -> fixture.draft.setDeploymentId("dep-other");
            case "version" -> fixture.version.setId("version-other");
            case "version-deployment" -> fixture.version.setDeploymentId("dep-other");
            case "unpublished" -> fixture.version.setStatus("DRAFT");
            case "customer" -> fixture.graph.setCustomerId("customer-other");
            case "tenant" -> fixture.graph.setTenantId("tenant-other");
            case "graph-deployment" -> fixture.graph.setDeploymentId("dep-other");
            case "graph-target" -> fixture.graph.setTargetProfileId("target-other");
            case "archived" -> fixture.deployment.setArchivedAt(Instant.parse("2026-01-01T00:00:00Z"));
            case "archive-status" -> fixture.deployment.setStatus("ARCHIVED");
            case "spec-fingerprint" -> fixture.graph.setSpecFingerprint("b".repeat(64));
            default -> throw new AssertionError(changed);
        }

        var summary = fixture.scopedSummary();

        fixture.assertDependencySecrets(summary, false);
        assertThat(summary.toString()).contains("Persisted evidence does not match this draft and scope");
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"worker", "reservation", "binding-fingerprint"})
    void incompleteOrInFlightDependencyGraphEvidenceIsMissing(String changed) {
        var fixture = new DependencyFixture();
        switch (changed) {
            case "worker" -> fixture.graph.setWorkerToken("worker-synthetic");
            case "reservation" -> fixture.graph.setReservationRef(null);
            case "binding-fingerprint" -> fixture.graph.setBindingFingerprint(null);
            default -> throw new AssertionError(changed);
        }
        fixture.assertDependencySecrets(fixture.scopedSummary(), false);
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "application", "url", "node-status", "stale-revision", "slot", "duplicate", "planned-secrets-only"})
    void generatedSecretNamesWithoutExactProvisionedNodeEvidenceAreNotReady(String changed) {
        var fixture = new DependencyFixture();
        switch (changed) {
            case "disabled" -> fixture.node.disabled = true;
            case "application" -> fixture.node.applicationUuid = null;
            case "url" -> fixture.node.baseUrl = null;
            case "node-status" -> fixture.node.status = "PLANNED";
            case "stale-revision" -> fixture.node.provisionedConfigurationRevision--;
            case "slot" -> fixture.node.slot = "other";
            case "duplicate" -> fixture.state.nodes.add(fixture.node);
            case "planned-secrets-only" -> fixture.state.nodes.clear();
            default -> throw new AssertionError(changed);
        }
        fixture.assertDependencySecrets(fixture.scopedSummary(), false);
        for (String reference : fixture.references) verify(fixture.secrets, never()).describeSecret(reference);
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "absent", "metadata", "name", "deployment", "managed", "scope"})
    void dependencyCredentialRequiresPresentLocallyOwnedSecretMetadata(String changed) {
        var fixture = new DependencyFixture();
        for (int index = 0; index < fixture.references.size(); index++) {
            String reference = fixture.references.get(index);
            if ("reference".equals(changed)) fixture.node.secrets.remove(fixture.purposes.get(index));
            else if ("metadata".equals(changed)) when(fixture.secrets.describeSecret(reference)).thenReturn(null);
            else when(fixture.secrets.describeSecret(reference)).thenReturn(new PlatformSecretSummary(
                "name".equals(changed) ? "DIFFERENT_REFERENCE" : reference, "private-metadata", "private-metadata", false,
                !"absent".equals(changed), "DATABASE", null,
                "scope".equals(changed) ? "GLOBAL_PLATFORM" : "DEPLOYMENT_MANAGED", "PLATFORM_MANAGED", null,
                "deployment".equals(changed) ? "dep-other" : fixture.deployment.getId(), !"managed".equals(changed), "DELETE_ON_HARD_DELETE"));
        }
        fixture.assertDependencySecrets(fixture.scopedSummary(), false);
        fixture.verifyLocalOnly();
    }

    @Test
    void targetSecretUsesOnlyTheExactPersistedTargetReference() {
        var fixture = new DependencyFixture();
        fixture.useBindings(List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
            fixture.names.getFirst(), new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.TARGET_SECRET, "target-credential"))));
        var missing = fixture.scopedSummary();
        assertThat(missing.secrets()).filteredOn(item -> fixture.names.getFirst().equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("MISSING"));

        fixture.state.targetSecretReferences.put("target-credential", fixture.references.getFirst());
        var present = fixture.scopedSummary();
        assertThat(present.secrets()).filteredOn(item -> fixture.names.getFirst().equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("READY"));
        assertThat(present.toString()).doesNotContain(fixture.references.getFirst());
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"different-name", "unrelated-name", "runtime-target", "non-secret"})
    void dependencyProjectionRequiresExactConnectorEnvironmentSecretDeclaration(String changed) {
        var fixture = new DependencyFixture();
        var target = "runtime-target".equals(changed) ? ManagedDependencySpec.RuntimeTarget.RUNTIME : ManagedDependencySpec.RuntimeTarget.CONNECTOR;
        String name = switch (changed) {
            case "different-name" -> fixture.names.getFirst() + "_EXTRA";
            case "unrelated-name" -> "OTHER_ALPHA_AUTH";
            default -> fixture.names.getFirst();
        };
        var value = "non-secret".equals(changed)
            ? new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_URL, "source")
            : new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_SECRET, "api", "source");
        fixture.useBindings(List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(target, name, value)));

        var summary = fixture.scopedSummary();

        assertThat(summary.secrets()).filteredOn(item -> fixture.names.contains(item.secretName()))
            .allSatisfy(item -> assertThat(item.present()).isFalse());
        for (String reference : fixture.references) verify(fixture.secrets, never()).describeSecret(reference);
        fixture.verifyLocalOnly();
    }

    @Test
    void missingGraphAndUnscopedRequestsCannotSelectAnotherTargetOrVersion() {
        var fixture = new DependencyFixture();
        var missing = fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-other");
        assertThat(missing.secrets()).filteredOn(item -> fixture.names.contains(item.secretName()))
            .allSatisfy(item -> assertThat(item.present()).isFalse());
        verify(fixture.graphs).find("dep-synthetic", "target-other");
        verify(fixture.graphs, never()).find("dep-synthetic", "target-synthetic");
        fixture.service.build(fixture.deployment.getId(), fixture.draft);
        verify(fixture.graphs, never()).spec(any());
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @Test
    void exactPublishedTemplateDeclarationsKeepMissingGraphPendingDespiteSameNamedGlobalCredentials() {
        var fixture = new DependencyFixture();
        fixture.publishSpec();
        fixture.shadowWithGlobalCredentials();

        var summary = fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-other");

        fixture.assertDependencySecrets(summary, false);
        assertThat(summary.missingRequiredCount()).isEqualTo(3);
        assertThat(summary.toString()).contains("Dependency provisioning is pending or not ready");
        verify(fixture.graphs).find("dep-synthetic", "target-other");
        verify(fixture.graphs, never()).find("dep-synthetic", "target-synthetic");
        verify(fixture.specs).findByTemplateVersionIdAndActiveTrue("template-version-synthetic");
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @Test
    void olderTargetGraphCannotHideRequestedPublishedTemplateDeclarations() {
        var fixture = new DependencyFixture();
        fixture.publishSpec();
        fixture.shadowWithGlobalCredentials();
        var olderSpec = new ManagedDependencySpec("managed-dependencies/v1", List.of(ManagedDependencyFixtures.http("source", false)));
        when(fixture.graphs.spec(fixture.graph)).thenReturn(olderSpec);
        fixture.graph.setVersionId("version-older");
        fixture.graph.setSpecFingerprint(fixture.validator.fingerprint(olderSpec));

        var summary = fixture.scopedSummary();

        fixture.assertDependencySecrets(summary, false);
        assertThat(summary.toString()).contains("Persisted evidence does not match this draft and scope");
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @ParameterizedTest
    @ValueSource(strings = {"template-scope", "spec-fingerprint", "review", "inactive", "duplicate", "multiple-templates"})
    void absentGraphDoesNotUseAmbiguousOrInvalidReviewedDeclarations(String changed) {
        var fixture = new DependencyFixture();
        var publication = fixture.publishSpec();
        switch (changed) {
            case "template-scope" -> publication.setTemplateVersionId("template-other");
            case "spec-fingerprint" -> publication.setFingerprint("b".repeat(64));
            case "review" -> publication.setReviewedAt(null);
            case "inactive" -> publication.setActive(false);
            case "duplicate" -> when(fixture.specs.findByTemplateVersionIdAndActiveTrue("template-version-synthetic"))
                .thenReturn(List.of(publication, publication));
            case "multiple-templates" -> fixture.version.setCompositionProvenanceJson("""
                {"marketplaceInstalls":[{"pluginType":"TEMPLATE","pluginVersionId":"template-version-synthetic"},
                {"pluginType":"TEMPLATE","pluginVersionId":"template-other"}]}
                """);
            default -> throw new AssertionError(changed);
        }
        assertThatThrownBy(() -> fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-other"))
            .isInstanceOf(IllegalStateException.class);
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    @Test
    void dependencyDeclarationDoesNotReplaceIndependentProviderCredentialRequirements() {
        var fixture = new DependencyFixture();
        fixture.draft.setRoutingConfigJson("{\"connector\":{\"upstream\":{\"auth\":{\"value\":\"${OPENAI_API_KEY}\"}}}}");
        fixture.version.setRoutingConfigJson(fixture.draft.getRoutingConfigJson());
        fixture.useBindings(List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
            "OPENAI_API_KEY", new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_SECRET, "api", "source"))));
        fixture.graph.setStatus("PLANNED");
        assertThat(fixture.scopedSummary().secrets()).filteredOn(item -> "OPENAI_API_KEY".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("MISSING"));

        fixture.graph.setStatus("PROVISIONED");
        assertThat(fixture.scopedSummary().secrets()).filteredOn(item -> "OPENAI_API_KEY".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("READY"));

        when(fixture.secrets.listSecrets()).thenReturn(List.of());
        assertThat(fixture.scopedSummary().secrets()).filteredOn(item -> "OPENAI_API_KEY".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("MISSING"));
        fixture.verifyLocalOnly();
    }

    @Test
    void dependencyCredentialsAndNativeVectorCredentialsKeepIndependentLocalOwners() {
        var fixture = new DependencyFixture();
        fixture.draft.setProviderConfigJson("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED"}
            """);
        fixture.version.setProviderConfigJson(fixture.draft.getProviderConfigJson());
        var effective = fixture.mapper.createObjectNode().put("vectorStrategy", "milvus")
            .put("milvusAuthenticationType", "TOKEN").put("milvusExternalBindingRequired", true)
            .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
            .put("milvusRuntimeTokenSecretName", "MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_SCOPE");
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-synthetic"))).thenReturn(effective);

        var ready = fixture.scopedSummary();
        fixture.assertDependencySecrets(ready, true);
        assertThat(ready.secrets()).filteredOn(item -> "MILVUS_TOKEN".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("READY"));

        fixture.graph.setStatus("PLANNED");
        var pending = fixture.scopedSummary();
        fixture.assertDependencySecrets(pending, false);
        assertThat(pending.secrets()).filteredOn(item -> "MILVUS_TOKEN".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("READY"));
        verify(fixture.bindings, never()).resolve(any(), any(), any(), any());
        verify(fixture.secrets, never()).resolveSecret(any());
        verify(fixture.secrets, never()).isSecretPresent(any());
        verifyNoInteractions(fixture.resolutions);
    }

    @Test
    void normalizedNativeCredentialPurposeIsNotAnExactEnvironmentIdentifierMatch() {
        var fixture = new DependencyFixture();
        fixture.draft.setRoutingConfigJson("{\"connector\":{\"upstream\":{\"auth\":{\"value\":\"${MILVUS_USERNAME}\"}}}}");
        fixture.version.setRoutingConfigJson(fixture.draft.getRoutingConfigJson());
        fixture.useBindings(List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
            "MILVUS_RUNTIME_CREDENTIALS", new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_SECRET, "api", "source"))));
        assertThat(fixture.scopedSummary().secrets()).filteredOn(item -> "MILVUS_RUNTIME_CREDENTIALS".equals(item.secretName()))
            .singleElement().satisfies(item -> assertThat(item.present()).isFalse());
        for (String reference : fixture.references) verify(fixture.secrets, never()).describeSecret(reference);
        fixture.verifyLocalOnly();
    }

    @Test
    void unexpectedDependencyProjectionFailuresAreNotHiddenAsMissingCredentials() {
        var fixture = new DependencyFixture();
        when(fixture.graphs.state(fixture.graph)).thenThrow(new IllegalStateException("synthetic-state-failure"));
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(IllegalStateException.class).hasMessage("synthetic-state-failure");
        doReturn(fixture.state).when(fixture.graphs).state(fixture.graph);
        doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "synthetic-metadata-failure"))
            .when(fixture.secrets).describeSecret(fixture.references.getFirst());
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("synthetic-metadata-failure");
        fixture.verifyLocalOnly();
    }

    @Test
    void invalidPersistedDependencySpecCannotFallBackToGlobalPlaceholderCredentials() {
        var fixture = new DependencyFixture();
        fixture.shadowWithGlobalCredentials();
        when(fixture.graphs.spec(fixture.graph)).thenReturn(null);
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(ResponseStatusException.class).hasMessageContaining("SPEC_INVALID");
        verify(fixture.graphs, never()).state(any());
        fixture.verifyLocalOnly();
    }

    private void assertBinding(DeploymentSecretUsageSummary summary, String authType, boolean present) {
        String purpose = "TOKEN".equals(authType) ? "MILVUS_TOKEN" : "MILVUS_RUNTIME_CREDENTIALS";
        assertThat(summary.secrets()).filteredOn(item -> purpose.equals(item.secretPurpose())).singleElement().satisfies(item -> {
            assertThat(item.required()).isTrue();
            assertThat(item.present()).isEqualTo(present);
            assertThat(item.status()).isEqualTo(present ? "READY" : "MISSING");
            assertThat(item.source()).isEqualTo("DEPLOYMENT_MANAGED");
        });
    }

    private static final class DependencyFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final PlatformSecretService secrets = mock(PlatformSecretService.class);
        private final DeploymentProviderSecretResolutionService resolutions = mock(DeploymentProviderSecretResolutionService.class);
        private final DeploymentExistingVectorBindingService bindings = mock(DeploymentExistingVectorBindingService.class);
        private final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
        private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
        private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
        private final DeploymentEntity deployment = new DeploymentEntity();
        private final DeploymentDraftEntity draft = new DeploymentDraftEntity();
        private final DeploymentVersionEntity version = new DeploymentVersionEntity();
        private final ManagedDependencyGraphEntity graph = new ManagedDependencyGraphEntity();
        private final ManagedDependencyState state = new ManagedDependencyState();
        private final ManagedDependencyState.Node node = new ManagedDependencyState.Node();
        private final List<String> names = List.of("ALPHA_AUTH", "BETA_AUTH", "GAMMA_AUTH");
        private final List<String> purposes = List.of("api", "db", "management");
        private final List<String> references = List.of("MANAGED_TEST_SOURCE_A", "MANAGED_TEST_SOURCE_B", "MANAGED_TEST_SOURCE_C");
        private final PlatformSecretSummary openai = new PlatformSecretSummary("OPENAI_API_KEY", "OpenAI", "Synthetic", true,
            true, "DATABASE", null, "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "KEEP");
        private final DeploymentSecretUsageService service;
        private ManagedDependencySpec spec;

        private DependencyFixture() {
            deployment.setId("dep-synthetic");
            deployment.setCustomerId("customer-synthetic");
            deployment.setTenantId("tenant-synthetic");
            draft.setId("draft-current");
            draft.setDeploymentId(deployment.getId());
            draft.setProviderConfigJson("{}");
            draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false}");
            draft.setMarketplaceDatasetConfigJson("""
                {"datasets":[{"ingestionMode":"EXTERNAL_SYNC_HTTP","syncConnector":{
                  "connectionProfile":{"auth":{"apiKeyValue":"${ALPHA_AUTH}","credentialFields":{"client":"${secret:BETA_AUTH}"}}},
                  "webhook":{"verification":{"secret":"${GAMMA_AUTH}"}}
                }}]}
                """);
            version.setId("version-synthetic");
            version.setDeploymentId(deployment.getId());
            version.setSourceDraftId("draft-published");
            version.setStatus("PUBLISHED");
            version.setProviderConfigJson(draft.getProviderConfigJson());
            version.setSecurityConfigJson(draft.getSecurityConfigJson());
            version.setMarketplaceDatasetConfigJson(draft.getMarketplaceDatasetConfigJson());
            graph.setId("graph-synthetic");
            graph.setDeploymentId(deployment.getId());
            graph.setCustomerId(deployment.getCustomerId());
            graph.setTenantId(deployment.getTenantId());
            graph.setTargetProfileId("target-synthetic");
            graph.setVersionId(version.getId());
            graph.setBindingFingerprint("a".repeat(64));
            graph.setReservationRef("reservation-synthetic");
            graph.setStatus("READY");
            state.configurationRevision = 3;
            state.effectiveConfigurationRevision = 3;
            node.slot = "source";
            node.applicationUuid = "source-application";
            node.baseUrl = "https://dependency.example.test";
            node.status = "READY";
            node.initialized = true;
            node.provisionedConfigurationRevision = 3;
            state.nodes.add(node);
            for (int index = 0; index < references.size(); index++) {
                String reference = references.get(index);
                node.secrets.put(purposes.get(index), reference);
                when(secrets.isManagedSecretName(reference)).thenReturn(true);
                when(secrets.describeSecret(reference)).thenReturn(new PlatformSecretSummary(reference, "private-metadata", "private-metadata",
                    false, true, "DATABASE", null, "DEPLOYMENT_MANAGED", "PLATFORM_MANAGED", null,
                    deployment.getId(), true, "DELETE_ON_HARD_DELETE"));
            }
            when(secrets.listSecrets()).thenReturn(List.of(openai));
            when(graphs.find(deployment.getId(), "target-synthetic")).thenReturn(Optional.of(graph));
            when(graphs.state(graph)).thenReturn(state);
            useBindings(java.util.stream.IntStream.range(0, names.size()).mapToObj(index ->
                new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR, names.get(index),
                    new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_SECRET, purposes.get(index), "source"))).toList());
            service = new DeploymentSecretUsageService(secrets, resolutions, mock(DeploymentSecretPurposeCatalog.class),
                new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)), mapper, bindings, graphs, validator, specs);
        }

        private void useBindings(List<ManagedDependencySpec.RuntimeEnvironmentBinding> environment) {
            spec = new ManagedDependencySpec("managed-dependencies/v1", List.of(ManagedDependencyFixtures.http("source", false)), environment);
            validator.validate(spec);
            graph.setSpecFingerprint(validator.fingerprint(spec));
            when(graphs.spec(graph)).thenReturn(spec);
        }

        private ManagedDependencySpecEntity publishSpec() {
            version.setCompositionProvenanceJson("""
                {"marketplaceInstalls":[{"pluginType":"TEMPLATE","pluginVersionId":"template-version-synthetic"}]}
                """);
            var publication = new ManagedDependencySpecEntity();
            publication.setId("spec-synthetic");
            publication.setTemplateVersionId("template-version-synthetic");
            publication.setFingerprint(validator.fingerprint(spec));
            publication.setSpecJson("synthetic-spec-json");
            publication.setReviewedAt(Instant.parse("2026-01-01T00:00:00Z"));
            publication.setReviewedBy("reviewer-synthetic");
            publication.setActive(true);
            when(specs.findByTemplateVersionIdAndActiveTrue("template-version-synthetic")).thenReturn(List.of(publication));
            when(graphs.parse(publication.getSpecJson(), ManagedDependencySpec.class)).thenReturn(spec);
            return publication;
        }

        private void shadowWithGlobalCredentials() {
            var catalog = new java.util.ArrayList<PlatformSecretSummary>();
            catalog.add(openai);
            names.forEach(name -> catalog.add(new PlatformSecretSummary(name, "Synthetic shadow", "Synthetic", false, true,
                "ENV", null, "GLOBAL_PLATFORM", "PLATFORM", name, null, false, "KEEP")));
            when(secrets.listSecrets()).thenReturn(catalog);
        }

        private DeploymentSecretUsageSummary scopedSummary() {
            return service.build(deployment, draft, version, "target-synthetic");
        }

        private void assertDependencySecrets(DeploymentSecretUsageSummary summary, boolean present) {
            assertThat(summary.secrets()).filteredOn(item -> names.contains(item.secretName())).hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.required()).isTrue();
                    assertThat(item.present()).isEqualTo(present);
                    assertThat(item.status()).isEqualTo(present ? "READY" : "MISSING");
                    assertThat(item.source()).isEqualTo("DEPLOYMENT_MANAGED");
                    assertThat(item.effectiveResolution()).isNull();
                    assertThat(item.secretPurpose()).isNull();
                });
        }

        private void verifyLocalOnly() {
            verifyNoInteractions(bindings, resolutions);
            verify(secrets, never()).resolveSecret(any());
            verify(secrets, never()).isSecretPresent(any());
        }
    }

    private static final class BindingFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final PlatformSecretService secrets = mock(PlatformSecretService.class);
        private final DeploymentProviderSecretResolutionService resolutions = mock(DeploymentProviderSecretResolutionService.class);
        private final DeploymentExistingVectorBindingService bindings = mock(DeploymentExistingVectorBindingService.class);
        private final DeploymentEntity deployment = new DeploymentEntity();
        private final DeploymentDraftEntity draft = new DeploymentDraftEntity();
        private final DeploymentVersionEntity version = new DeploymentVersionEntity();
        private final ObjectNode effectiveConfig;
        private final String purpose;
        private final DeploymentSecretUsageService service;

        private BindingFixture(String authType) {
            purpose = "TOKEN".equals(authType) ? "MILVUS_TOKEN" : "MILVUS_RUNTIME_CREDENTIALS";
            deployment.setId("dep-fake"); deployment.setCustomerId("customer-fake"); deployment.setTenantId("tenant-fake");
            draft.setId("draft-current"); draft.setDeploymentId(deployment.getId());
            draft.setProviderConfigJson(mapper.createObjectNode().put("vectorStrategy", "milvus")
                .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
                .put("milvusExternalBindingRequired", true).put("milvusAuthenticationType", authType).toString());
            draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false}");
            version.setId("ver-fake"); version.setDeploymentId(deployment.getId()); version.setSourceDraftId("draft-published");
            version.setStatus("PUBLISHED"); version.setConfigHash("hash-fake");
            version.setProviderConfigJson(draft.getProviderConfigJson()); version.setSecurityConfigJson(draft.getSecurityConfigJson());
            effectiveConfig = mapper.createObjectNode().put("vectorStrategy", "milvus")
                .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
                .put("milvusExternalBindingRequired", true).put("milvusAuthenticationType", authType);
            if ("TOKEN".equals(authType)) effectiveConfig.put("milvusRuntimeTokenSecretName", "MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_SCOPE");
            else effectiveConfig.put("milvusRuntimeUsernameSecretName", "MANAGED_EXTERNAL_VECTOR_USERNAME_FAKE_SCOPE")
                .put("milvusRuntimePasswordSecretName", "MANAGED_EXTERNAL_VECTOR_PASSWORD_FAKE_SCOPE");
            when(bindings.resolveLocal(eq(deployment), eq(version), any(), eq("target-staging"))).thenReturn(effectiveConfig);
            when(secrets.listSecrets()).thenReturn(List.of(
                new PlatformSecretSummary("OPENAI_API_KEY", "OpenAI", "Synthetic metadata", true, true, "DATABASE", null,
                    "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "KEEP"),
                new PlatformSecretSummary(purpose, "Milvus", "Synthetic global metadata", false, true, "DATABASE", null,
                    "GLOBAL_PLATFORM", "PLATFORM", purpose, null, false, "KEEP")));
            service = new DeploymentSecretUsageService(secrets, resolutions, new DeploymentSecretPurposeCatalog(),
                new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)), mapper, bindings,
                mock(ManagedDependencyGraphStore.class), new ManagedDependencySpecValidator(mapper), mock(ManagedDependencySpecRepository.class));
        }

        private DeploymentSecretUsageSummary scopedSummary() {
            return service.build(deployment, draft, version, "target-staging");
        }

        private void verifyNoLiveOrFallbackReads() {
            verify(bindings, never()).resolve(any(), any(), any(), any());
            verify(resolutions, never()).summarize(deployment.getId(), purpose);
            verify(secrets, never()).resolveSecret(any());
            verify(secrets, never()).isSecretPresent(any());
        }
    }
}
