package com.ai.fabric.platform.backend.deployment.authority;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentExecutionSecretService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentRuntimeAuthorityServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final RuntimeAuthorityRepository authorities = mock(RuntimeAuthorityRepository.class);
    private final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
    private final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final Map<String, RuntimeAuthorityEntity> authorityRows = new HashMap<>();
    private final Map<String, RuntimeAuthoritySnapshotEntity> snapshotRows = new HashMap<>();
    private final Map<String, PlatformSecretEntity> secretRows = new HashMap<>();
    private DeploymentRuntimeAuthorityService service;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;

    @BeforeEach void setUp() {
        when(secrets.findById(anyString())).thenAnswer(i -> Optional.ofNullable(secretRows.get(i.getArgument(0))));
        when(secrets.existsById(anyString())).thenAnswer(i -> secretRows.containsKey(i.getArgument(0)));
        when(secrets.save(any())).thenAnswer(i -> { PlatformSecretEntity row = i.getArgument(0); secretRows.put(row.getName(), row); return row; });
        when(secrets.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        doAnswer(i -> { secretRows.remove(i.getArgument(0)); return null; }).when(secrets).deleteById(anyString());
        when(authorities.findByDeploymentIdAndTargetProfileId(anyString(), anyString())).thenAnswer(i -> authorityRows.values().stream()
            .filter(r -> r.getDeploymentId().equals(i.getArgument(0)) && r.getTargetProfileId().equals(i.getArgument(1))).findFirst());
        when(authorities.save(any())).thenAnswer(i -> { RuntimeAuthorityEntity row = i.getArgument(0); authorityRows.put(row.getId(), row); return row; });
        when(snapshots.findById(anyString())).thenAnswer(i -> Optional.ofNullable(snapshotRows.get(i.getArgument(0))));
        when(snapshots.findByReleaseId(anyString())).thenAnswer(i -> snapshotRows.values().stream().filter(r -> Objects.equals(r.getReleaseId(), i.getArgument(0))).findFirst());
        when(snapshots.save(any())).thenAnswer(i -> { RuntimeAuthoritySnapshotEntity row = i.getArgument(0); snapshotRows.put(row.getId(), row); return row; });
        var secretService = new PlatformSecretService(secrets, audit, new MockEnvironment()
            .withProperty(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME, "GLOBAL-TRUSTED-KEY")
            .withProperty(RuntimePrivateAssertionSigningService.SECRET_NAME, "GLOBAL-PRIVATE-KEY"));
        service = new DeploymentRuntimeAuthorityService(deployments, versions, releases, targets, access, authorities, snapshots,
            secrets, secretService, new DeploymentExecutionSecretService(secretService, mapper),
            new RuntimePrivateAssertionSigningService(secretService, mapper), audit, mapper);
        deployment = deployment("dep-1", "customer-1", "tenant-1");
        version = version("version-1", deployment.getId());
        for (String target : List.of("staging", "production")) {
            var profile = new DeploymentTargetProfileEntity(); profile.setId(target); profile.setActive(true);
            when(targets.findById(target)).thenReturn(Optional.of(profile));
        }
    }

    @Test void reusesRandomManagedSecretsAndFreezesIdempotentlyUnderDeploymentLock() {
        var first = freeze("staging");
        var values = secretValues();
        var second = freeze("staging");
        assertThat(second).isEqualTo(first);
        assertThat(secretValues()).isEqualTo(values);
        assertThat(new HashSet<>(values)).hasSize(values.size());
        assertThat(values).allSatisfy(value -> assertThat(Base64.getUrlDecoder().decode(value)).hasSize(48));
        verify(secrets, times(values.size())).save(any());
        var order = inOrder(deployments, secrets);
        order.verify(deployments).findByIdForUpdate("dep-1");
        order.verify(secrets, atLeastOnce()).save(any());
    }

    @org.junit.jupiter.api.AfterEach void clearPrincipal() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void principal(com.ai.fabric.platform.backend.security.PlatformRole role) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.ai.fabric.platform.backend.security.PlatformPrincipal("staff", role, "fixture", "SESSION"), null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @Test void authorizedApplyFreezeDoesNotDependOnAmbientBrowserAuthority() {
        var candidate = service.freezeCandidateForAuthorizedApply(
            "dep-1", "staging", "version-1", List.of("https://workspace.example.com")
        );
        var release = new DeploymentReleaseEntity();
        release.setId("release-authorized-apply");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version-1");
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));

        var frozen = service.freezeReleaseForAuthorizedApply(
            "dep-1", "staging", release.getId(), candidate.snapshotId(), candidate.fingerprint()
        );

        assertThat(frozen.releaseId()).isEqualTo(release.getId());
        assertThat(frozen.binding().targetProfileId()).isEqualTo("staging");
        assertThat(frozen.binding().versionId()).isEqualTo("version-1");
        verifyNoInteractions(access);
    }

    @Test void targetKeysAndAudienceIssuerBoundariesRejectEachOthersCredentials() throws Exception {
        var stage = release("staging", "release-stage");
        var prod = release("production", "release-prod");
        var stageHeaders = headers(stage);
        var prodHeaders = headers(prod);
        assertThat(stageHeaders.get(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER))
            .isNotEqualTo(prodHeaders.get(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER));
        assertThat(stage.binding().secrets().keySet()).isEqualTo(prod.binding().secrets().keySet());
        for (var purpose : stage.binding().secrets().keySet()) {
            assertThat(key(stage, purpose)).isNotEqualTo(key(prod, purpose));
            assertThat(stage.binding().secrets().get(purpose).reference()).isNotEqualTo(prod.binding().secrets().get(purpose).reference());
        }
        String token = stageHeaders.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7);
        assertThat(validHmac(token, key(stage, PRIVATE_ASSERTION_SIGNING_KEY))).isTrue();
        assertThat(validHmac(token, key(prod, PRIVATE_ASSERTION_SIGNING_KEY))).isFalse();
        assertThat(validHmac(token, "GLOBAL-PRIVATE-KEY")).isFalse();
        var payload = payload(token);
        assertThat(payload.path("aud").asText()).isEqualTo(stage.binding().privateAudience()).isNotEqualTo(prod.binding().privateAudience());
        assertThat(payload.path("iss").asText()).isEqualTo(stage.binding().privateIssuer()).isNotEqualTo(prod.binding().privateIssuer());
        assertThat(payload.path("customerId").asText()).isEqualTo("customer-1");
        assertThat(payload.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(payload.path("scopes").get(0).asText()).isEqualTo("documents:read");
        assertThatThrownBy(() -> service.issueSystemHeaders("dep-1", "production", "release-stage", stage.fingerprint(),
            "system", "session", List.of("documents:read"), Duration.ofMinutes(1))).hasMessageContaining("explicit deployment and target");
    }

    @Test void absenceAndRevisionOrValueDriftFailClosedDespiteGlobalKeys() {
        assertThatThrownBy(() -> service.requireReleaseUnchanged("dep-1", "staging", "missing-release", "fingerprint"))
            .isInstanceOf(ResponseStatusException.class);
        var frozen = release("staging", "release-stage");
        String reference = frozen.binding().secrets().get(PRIVATE_ASSERTION_SIGNING_KEY).reference();
        var secret = secretRows.remove(reference);
        assertThatThrownBy(() -> headers(frozen)).hasMessageContaining("missing or revoked");
        secretRows.put(reference, secret);
        Instant originalRevision = secret.getUpdatedAt();
        secret.setUpdatedAt(originalRevision.plusSeconds(1));
        assertThatThrownBy(() -> headers(frozen)).hasMessageContaining("changed");
        secret.setUpdatedAt(originalRevision);
        secret.setSecretValue("tampered-with-unchanged-timestamp");
        assertThatThrownBy(() -> headers(frozen)).hasMessageContaining("changed");
        assertThatThrownBy(() -> freeze("staging")).hasMessageContaining("changed");
        assertThat(service.rotate("dep-1", "staging", 1)).isEqualTo(2);
        assertThatThrownBy(() -> headers(frozen)).hasMessageContaining("new apply and verification");
        assertThat(freeze("staging").binding().generation()).isEqualTo(2);
    }

    @Test void rotationRequiresNewReleaseAndDoesNotRotateOtherTargets() {
        var stage = release("staging", "release-stage");
        var prod = release("production", "release-prod");
        String prodKey = key(prod, PRIVATE_ASSERTION_SIGNING_KEY);
        assertThat(service.rotate("dep-1", "staging", 1)).isEqualTo(2);
        assertThatThrownBy(() -> service.rotate("dep-1", "staging", 1)).hasMessageContaining("generation changed");
        assertThatThrownBy(() -> headers(stage)).hasMessageContaining("new apply and verification");
        var next = freeze("staging");
        assertThat(next.fingerprint()).isNotEqualTo(stage.fingerprint());
        assertThatThrownBy(() -> service.freezeRelease("dep-1", "staging", "release-stage", next.snapshotId(), next.fingerprint()))
            .hasMessageContaining("fingerprint");
        assertThat(key(prod, PRIVATE_ASSERTION_SIGNING_KEY)).isEqualTo(prodKey);
        assertThat(headers(prod)).isNotEmpty();
    }

    @Test void revocationIsIdempotentAndNeverClearsAnotherTargetGlobalOrSourceSecret() {
        var stage = release("staging", "release-stage");
        var prod = release("production", "release-prod");
        var source = new PlatformSecretEntity(); source.setName("MANAGED_CUSTOMER_SOURCE"); source.setSecretValue("source-value");
        secretRows.put(source.getName(), source);
        service.revoke("dep-1", "staging", 1);
        service.revoke("dep-1", "staging", 1);
        assertThat(secretRows).containsKey(source.getName());
        assertThatThrownBy(() -> headers(stage)).hasMessageContaining("revoked");
        assertThatThrownBy(() -> freeze("staging")).hasMessageContaining("cannot be silently recreated");
        assertThat(headers(prod)).isNotEmpty();
        verify(secrets, times(stage.binding().secrets().size())).deleteById(anyString());
        verify(secrets, never()).deleteById(RuntimePrivateAssertionSigningService.SECRET_NAME);
        verify(secrets, never()).deleteById(source.getName());
        for (var ref : prod.binding().secrets().values()) verify(secrets, never()).deleteById(ref.reference());
    }

    @Test void foreignOwnerCannotBeRevokedAndSnapshotCannotBeSubstitutedAcrossCustomers() {
        var stage = freeze("staging");
        var ref = stage.binding().secrets().get(CONNECTOR_ADMIN_API_KEY).reference();
        secretRows.get(ref).setDeploymentId("foreign-deployment");
        assertThatThrownBy(() -> service.revoke("dep-1", "staging", 1)).hasMessageContaining("managed ownership");
        verify(secrets, never()).deleteById(anyString());
        deployment.setCustomerId("other-customer");
        assertThatThrownBy(() -> freeze("staging")).hasMessageContaining("owner changed");
    }

    @Test void environmentAndAuditProjectionsNeverExposeValuesOrSecretHashes() throws Exception {
        var frozen = freeze("staging");
        var plan = service.candidateEnvironmentPlan("dep-1", "staging", "version-1", frozen.snapshotId(), frozen.fingerprint());
        String metadata = mapper.writeValueAsString(frozen) + mapper.writeValueAsString(plan)
            + mapper.writeValueAsString(authorityRows.values().iterator().next());
        for (String secret : secretValues()) {
            assertThat(metadata).doesNotContain(secret).doesNotContain(hash(secret));
        }
        assertThat(metadata).doesNotContain("secretIntegrity").doesNotContain("GLOBAL-PRIVATE-KEY").doesNotContain("GLOBAL-TRUSTED-KEY");
        assertThat(plan.runtime()).containsEntry("AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES", frozen.binding().privateAudience())
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE", frozen.binding().publicAudience())
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED", "true")
            .containsEntry("AI_FABRIC_RUNTIME_AUTHZ_ALLOW_ANONYMOUS_SESSIONS", "true")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOW_MISSING_ORIGIN", "false");
        assertThat(plan.connector()).containsEntry("APP_ADMIN_API_KEY", "${secret:" + frozen.binding().secrets().get(CONNECTOR_ADMIN_API_KEY).reference() + "}");
        var details = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(audit, atLeastOnce()).record(anyString(), anyString(), anyString(), details.capture());
        for (var entry : details.getAllValues()) {
            String serialized = mapper.writeValueAsString(entry);
            assertThat(entry).containsKeys("deploymentId", "targetProfileId", "customerId", "tenantId");
            for (String secret : secretValues()) assertThat(serialized).doesNotContain(secret).doesNotContain(hash(secret));
        }
    }

    @Test void purposeCatalogUsesExistingCompositionRequirementsWithoutImportingLegacyValues() {
        version.setBehaviorConfigJson("{\"type\":\"SMART_BRAIN\",\"executionExtensions\":[\"HUMAN_REVIEW\",\"CONVERSATION_HANDOVER\"]}");
        var frozen = freeze("staging");
        assertThat(frozen.binding().secrets()).containsKeys(SMART_BRAIN_JOB_ENCRYPTION, SMART_BRAIN_JOB_FINGERPRINT,
            SMART_BRAIN_ENCRYPTION, SMART_BRAIN_FINGERPRINT, SMART_BRAIN_DELIVERY_SIGNING, ACTION_RECEIPT_ENCRYPTION,
            ACTION_RECEIPT_FINGERPRINT, REVIEW_ENCRYPTION, REVIEW_FINGERPRINT, CONVERSATION_HANDOVER_ENCRYPTION,
            CONVERSATION_HANDOVER_CALLBACK_SIGNING, INTEGRATION_SERVICE_API_KEY, INTEGRATION_CONNECTOR_DATABASE_PASSWORD);
        var plan = service.candidateEnvironmentPlan("dep-1", "staging", version.getId(), frozen.snapshotId(), frozen.fingerprint());
        assertThat(plan.runtime()).containsKeys("AI_EXECUTION_ASYNC_ENCRYPTION_SECRET", "LOOMAI_CONVERSATION_HANDOVER_CALLBACK_SIGNING_SECRET");
        assertThat(plan.connector()).containsKeys("REST_CONNECTOR_JDBC_PASSWORD", "REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_CALLBACK_SIGNING_SECRET");
        assertThat(plan.runtime()).doesNotContainKey("OPENAI_API_KEY");
    }

    @Test void provisioningMaterializesOnlyTheExactFrozenReleaseWithoutAmbientOperatorContext() {
        var candidate = freeze("staging");
        var release = new DeploymentReleaseEntity();
        release.setId("release-stage");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version-1");
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));
        var frozen = service.freezeRelease(
            "dep-1", "staging", release.getId(), candidate.snapshotId(), candidate.fingerprint()
        );
        release.setRuntimeAuthoritySnapshotId(frozen.snapshotId());
        release.setRuntimeAuthorityFingerprint(frozen.fingerprint());

        var plan = service.releaseEnvironmentPlanForProvisioning(deployment, version, release);

        assertThat(plan.runtime()).containsEntry("AI_FABRIC_RUNTIME_DEPLOYMENT_ID", "dep-1");
        assertThat(plan.runtime().values()).anySatisfy(value -> assertThat(value).startsWith("${secret:"));
        assertThat(plan.runtime().values()).doesNotContainAnyElementsOf(secretValues());

        release.setRuntimeAuthorityFingerprint("0".repeat(64));
        assertThatThrownBy(() -> service.releaseEnvironmentPlanForProvisioning(deployment, version, release))
            .hasMessageContaining("does not match");
    }

    @Test void anonymousAndSignedInPrivateSessionWireClaimsArePreserved() throws Exception {
        var frozen = release("staging", "release-stage");
        var anonymous = service.issueBackendSessionHeaders("dep-1", "staging", "release-stage", frozen.fingerprint(),
            "session-anon", "session-anon", true, List.of("chat:query"), Duration.ofMinutes(1));
        assertThat(payload(anonymous.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7)).path("subjectType").asText())
            .isEqualTo("ANONYMOUS_SESSION");
        var signedIn = service.issueBackendSessionHeaders("dep-1", "staging", "release-stage", frozen.fingerprint(),
            "verified-user", "session-user", false, List.of("chat:query", "chat:conversations"), Duration.ofMinutes(1));
        assertThat(payload(signedIn.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7)).path("subjectType").asText())
            .isEqualTo("END_USER");
        assertThatThrownBy(() -> service.issueBackendSessionHeaders("dep-1", "staging", "release-stage", frozen.fingerprint(),
            "different", "session-anon", true, List.of("chat:query"), Duration.ofMinutes(1))).hasMessageContaining("inconsistent");
    }

    @Test void workerVerificationUsesOnlyFixedReadScopesAndBoundedAssertionsWithoutOperatorAuthority() throws Exception {
        var frozen = release("staging", "release-stage");
        var release = releases.findById(frozen.releaseId()).orElseThrow();
        clearInvocations(access);
        for (boolean documents : List.of(false, true)) {
            Instant before = Instant.now();
            var result = service.issueReleaseVerificationHeaders(deployment, release, documents);
            var claims = payload(result.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7));
            assertThat(claims.path("subjectType").asText()).isEqualTo("SYSTEM_PROCESS");
            assertThat(claims.path("sub").asText()).isEqualTo("platform-release-verification");
            var scopes = new ArrayList<String>(); claims.path("scopes").forEach(scope -> scopes.add(scope.asText()));
            assertThat(scopes).containsExactlyInAnyOrderElementsOf(documents
                ? RuntimePrivateAccessSupport.documentReadScopes() : RuntimePrivateAccessSupport.adminReadScopes());
            assertThat(Instant.parse(claims.path("exp").asText())).isAfter(before).isBefore(before.plusSeconds(301));
        }
        verifyNoInteractions(access);
        release.setProvisioningStatus("QUEUED");
        assertThatThrownBy(() -> service.issueReleaseVerificationHeaders(deployment, release, false)).hasMessageContaining("not provisioned");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"finalize_runtime", "resume_managed_dependencies"})
    void provisionedSetupPhasePreservesExistingWorkerAuthorityWithoutStartingVerification(String step) throws Exception {
        var frozen = release("staging", "release-setup");
        var current = releases.findById(frozen.releaseId()).orElseThrow();
        current.setStatus("VERIFYING"); current.setVerificationStatus("PENDING"); current.setCurrentStepKey(step);
        clearInvocations(access);
        var read = service.issueReleaseVerificationHeaders(deployment, current, false);
        var readClaims = payload(read.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7));
        assertThat(readClaims.path("subjectType").asText()).isEqualTo("SYSTEM_PROCESS");
        assertThat(readClaims.path("sub").asText()).isEqualTo("platform-release-verification");
        var readScopes = new ArrayList<String>(); readClaims.path("scopes").forEach(scope -> readScopes.add(scope.asText()));
        assertThat(readScopes).containsExactlyInAnyOrderElementsOf(RuntimePrivateAccessSupport.adminReadScopes());
        assertThat(readScopes).noneMatch(scope -> scope.endsWith(":write"));
        var provision = service.issueProviderBudgetProvisioningHeaders(deployment, version, current);
        var provisionClaims = payload(provision.get(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER).substring(7));
        assertThat(provisionClaims.path("scopes")).hasSize(2);
        assertThat(provisionClaims.path("scopes").toString()).contains("runtime:admin:provider-budget:write");
        assertThat(current.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(current.getCurrentStepKey()).isEqualTo(step);
        verifyNoInteractions(access);
        current.setStatus("PROVISIONING"); current.setCurrentStepKey(step);
        assertThatThrownBy(() -> service.issueReleaseVerificationHeaders(deployment, current, false)).hasMessageContaining("not provisioned");
        assertThatThrownBy(() -> service.issueProviderBudgetProvisioningHeaders(deployment, version, current)).hasMessageContaining("not provisioned");
        current.setStatus("VERIFYING");
        current.setProvisioningStatus("RUNNING");
        assertThatThrownBy(() -> service.issueReleaseVerificationHeaders(deployment, current, false)).hasMessageContaining("not provisioned");
        current.setProvisioningStatus("ACTIVE"); current.setRuntimeAuthorityFingerprint("0".repeat(64));
        assertThatThrownBy(() -> service.issueReleaseVerificationHeaders(deployment, current, false)).hasMessageContaining("does not match");
    }

    @Test void coreAuthorityExposesNoLegacyRepairDrainOrGenerationEntryPoints() {
        assertThat(Arrays.stream(DeploymentRuntimeAuthorityService.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
            .noneMatch(name -> name.contains("ArtifactRepair") || name.contains("ColdMaintenance")
                || name.contains("GenerationTransition") || name.contains("TargetRetirement"));
    }

    @Test void missingContextInvalidOriginsStaleFingerprintAndUnprovisionedReleaseAreBlocked() {
        assertThatThrownBy(() -> service.freezeCandidate("dep-1", null, "version-1", List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.freezeCandidate("dep-1", "staging", "version-1", List.of("https://*.example.com"))).hasMessageContaining("exact HTTPS");
        assertThatThrownBy(() -> service.freezeCandidate("dep-1", "staging", "version-1", List.of("http://staging.example.com"))).hasMessageContaining("exact HTTPS");
        assertThatThrownBy(() -> service.freezeCandidate("dep-1", "staging", "version-1", List.of("https://staging.example.com/path"))).hasMessageContaining("exact HTTPS");
        assertThatThrownBy(() -> service.freezeCandidate("dep-1", "staging", "version-1", List.of())).hasMessageContaining("reviewed exact target origins");
        var frozen = release("staging", "release-stage");
        assertThatThrownBy(() -> service.requireReleaseUnchanged("dep-1", "staging", "release-stage", "wrong-fingerprint")).hasMessageContaining("fingerprint");
        releases.findById("release-stage").orElseThrow().setProvisioningStatus("PROVISIONING");
        assertThatThrownBy(() -> headers(frozen)).hasMessageContaining("not provisioned");
    }

    @Test void releaseFreezeReplayReturnsTheSameExactTargetReleaseSnapshot() {
        var candidate = service.freezeCandidate("dep-1", "staging", "version-1", List.of(
            "https://z.example.com", "https://a.example.com", "https://a.example.com"
        ));
        assertThat(candidate.binding().allowedOrigins()).containsExactly("https://a.example.com", "https://z.example.com");
        var release = new DeploymentReleaseEntity();
        release.setId("release-replay");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version-1");
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));

        var first = service.freezeRelease("dep-1", "staging", release.getId(), candidate.snapshotId(), candidate.fingerprint());
        var replay = service.freezeRelease("dep-1", "staging", release.getId(), candidate.snapshotId(), candidate.fingerprint());

        assertThat(replay).isEqualTo(first);
        assertThat(replay.releaseId()).isEqualTo(release.getId());
        assertThat(replay.binding().targetProfileId()).isEqualTo("staging");
        verify(snapshots, times(2)).save(any(RuntimeAuthoritySnapshotEntity.class));
    }

    @Test void distinctCustomerAndPunctuationSimilarDeploymentIdentitiesHaveDistinctAuthority() {
        var first = freeze("staging");
        deployment = deployment("dep_1", "customer-2", "tenant-2");
        version = version("version-2", "dep_1");
        var second = service.freezeCandidate("dep_1", "staging", "version-2", List.of("https://other.example.com"));
        assertThat(second.binding().privateAudience()).isNotEqualTo(first.binding().privateAudience());
        assertThat(second.binding().secrets().get(PRIVATE_ASSERTION_SIGNING_KEY).reference())
            .isNotEqualTo(first.binding().secrets().get(PRIVATE_ASSERTION_SIGNING_KEY).reference());
        assertThatThrownBy(() -> service.requireCandidateUnchanged("dep_1", "staging", "version-1", first.snapshotId(), first.fingerprint()))
            .hasMessageContaining("ownership");
    }

    @Test void explicitSignerNeverFallsBackAndLegacyStaffSigningRemainsAvailable() throws Exception {
        var store = mock(PlatformSecretService.class);
        when(store.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME)).thenReturn("legacy-global-signing");
        when(store.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME)).thenReturn("legacy-global-backend");
        var signer = new RuntimePrivateAssertionSigningService(store, mapper);
        var claims = new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims("staff", "INTERNAL_PLATFORM_USER",
            "PLATFORM_PROXY_SESSION", "PLATFORM_PROXY", "staff-session", "dep-1", "customer-1", "tenant-1",
            "legacy-issuer", Instant.now().plusSeconds(60), List.of("dep-1"), List.of("documents:read"));
        assertThat(validHmac(signer.issueAssertion(claims), "legacy-global-signing")).isTrue();
        assertThat(RuntimePrivateAccessSupport.issueHeaders(store, mapper, claims))
            .containsEntry(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "legacy-global-backend");
        clearInvocations(store);
        assertThat(validHmac(signer.issueAssertionWithSigningKey(claims, "exact-target-key"), "exact-target-key")).isTrue();
        assertThatThrownBy(() -> signer.issueAssertionWithSigningKey(claims, null)).hasMessageContaining("not configured");
        verifyNoInteractions(store);
    }

    private DeploymentEntity deployment(String id, String customer, String tenant) {
        var entity = new DeploymentEntity(); entity.setId(id); entity.setCustomerId(customer); entity.setTenantId(tenant); entity.setBehaviorType("CONVERSATIONAL");
        when(deployments.findByIdForUpdate(id)).thenReturn(Optional.of(entity)); return entity;
    }
    private DeploymentVersionEntity version(String id, String deploymentId) {
        var entity = new DeploymentVersionEntity(); entity.setId(id); entity.setDeploymentId(deploymentId); entity.setStatus("PUBLISHED");
        entity.setConfigHash("immutable-config-hash"); entity.setSecurityConfigJson("{\"publicRuntimeBootstrapEnabled\":true,\"authzMode\":\"ALLOW_VERIFIED\"}");
        entity.setBehaviorConfigJson("{\"type\":\"CONVERSATIONAL\",\"executionExtensions\":[]}");
        when(versions.findById(id)).thenReturn(Optional.of(entity)); return entity;
    }
    private RuntimeAuthorityBinding.Snapshot freeze(String target) {
        return service.freezeCandidate("dep-1", target, "version-1", List.of("https://" + target + ".example.com"));
    }
    private RuntimeAuthorityBinding.Snapshot release(String target, String releaseId) {
        var candidate = freeze(target);
        var release = new DeploymentReleaseEntity(); release.setId(releaseId); release.setDeploymentId("dep-1");
        release.setTargetProfileId(target); release.setDeploymentVersionId("version-1"); release.setProvisioningStatus("ACTIVE"); release.setStatus("VERIFYING");
        when(releases.findById(releaseId)).thenReturn(Optional.of(release));
        var frozen = service.freezeRelease("dep-1", target, releaseId, candidate.snapshotId(), candidate.fingerprint());
        release.setRuntimeAuthoritySnapshotId(frozen.snapshotId()); release.setRuntimeAuthorityFingerprint(frozen.fingerprint());
        return frozen;
    }
    private Map<String, String> headers(RuntimeAuthorityBinding.Snapshot snapshot) {
        return service.issueSystemHeaders(snapshot.binding().deploymentId(), snapshot.binding().targetProfileId(), snapshot.releaseId(),
            snapshot.fingerprint(), "verified-system", "explicit-session", List.of("documents:read"), Duration.ofMinutes(2));
    }
    private String key(RuntimeAuthorityBinding.Snapshot snapshot, RuntimeAuthorityPurpose purpose) {
        return secretRows.get(snapshot.binding().secrets().get(purpose).reference()).getSecretValue();
    }
    private List<String> secretValues() { return secretRows.values().stream().map(PlatformSecretEntity::getSecretValue).sorted().toList(); }
    private JsonNode payload(String token) throws Exception { return mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])); }
    private boolean validHmac(String token, String key) throws Exception {
        var parts = token.split("\\."); var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return MessageDigest.isEqual(mac.doFinal(parts[1].getBytes(StandardCharsets.UTF_8)), Base64.getUrlDecoder().decode(parts[2]));
    }
    private String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
