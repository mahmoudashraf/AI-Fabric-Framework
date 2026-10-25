package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationResolverTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void localResolutionUsesExactScopeAndOwnedRefsWithoutDnsProviderCallsOrDraftMutation() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind();
        String before = f.version.getCompositionProvenanceJson();
        var result = f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId());
        assertThat(result).hasSize(1); assertThat(f.dnsCalls).hasValue(0);
        assertThat(f.version.getCompositionProvenanceJson()).isEqualTo(before);
        assertThat(result.getFirst().configEnvironment()).containsValue("https://provider.example.com/actions");
        assertThat(result.getFirst().secretEnvironment()).hasSize(1);
        assertThat(f.codec.write(result)).doesNotContain("synthetic-customer-key", "ZILLIZ_CLOUD_API_KEY");
        verify(f.authority, never()).requireReleaseUnchanged(anyString(), anyString(), anyString(), anyString());
        verify(f.vault, never()).resolveSecret("MANAGED_GLOBAL_KEY");
    }

    @Test void noProfilesAreNoOpForOrdinaryDeploymentsBeforeOfferVaultDnsOrAuthorityLookup() throws Exception {
        var f = new ExternalIntegrationFixture();
        f.deployment.setCustomerId(null); f.deployment.setTenantId(null);
        f.version.setCompositionProvenanceJson("{}");
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.empty());
        assertThat(f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), "ordinary-target")).isEmpty();
        assertThat(f.resolver.requireForEffects(f.deployment.getId(), f.version.getId(), "ordinary-target")).isEmpty();
        assertThat(f.resolver.freezeRelease(f.deployment.getId(), f.version.getId(), "ordinary-target", "ordinary-release", null).projections()).isEmpty();
        assertThat(f.resolver.releaseEnvironment(f.deployment.getId(), f.version.getId(), "ordinary-target", "ordinary-release", null))
            .isEqualTo(new ReleaseEnvironment(Map.of(), Map.of()));
        verify(f.entitlements, never()).requireOffer(anyString());
        verify(f.store, never()).fenceDeployment(anyString()); verify(f.store, never()).secretEvidence(anyString());
        verifyNoInteractions(f.authority); verify(f.vault, never()).resolveSecret(anyString()); assertThat(f.dnsCalls).hasValue(0);
    }

    @Test void declaredProfilesWithoutAssociationAndMissingBindingNeverBecomeGlobalFallback() throws Exception {
        var f = new ExternalIntegrationFixture();
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId())).hasMessageContaining("BINDING_REQUIRED");
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId())).hasMessageContaining("OFFER_SCOPE_REQUIRED");
        verify(f.vault, never()).resolveSecret("ZILLIZ_CLOUD_API_KEY");
    }

    @Test void credentialsChangedMissingForeignOrReferenceInjectedFailClosed() throws Exception {
        for (String fault : new String[]{"missing", "revision", "owner", "reference"}) {
            var f = new ExternalIntegrationFixture(); f.bind();
            var row = f.bindings.values().iterator().next(); var binding = row.value();
            String name = binding.secretReferences().values().iterator().next();
            switch (fault) {
                case "missing" -> f.secretRows.remove(name);
                case "revision" -> f.secretRows.put(name, new ExternalIntegrationStore.SecretEvidence(f.deployment.getId(), "DEPLOYMENT_MANAGED", "PLATFORM_MANAGED", true, true, "new-revision"));
                case "owner" -> f.secretRows.put(name, new ExternalIntegrationStore.SecretEvidence("foreign-deployment", "DEPLOYMENT_MANAGED", "PLATFORM_MANAGED", true, true, "2026-10-19T01:00:00.123456Z"));
                case "reference" -> {
                    var changed = new Binding(binding.id(), binding.scope(), binding.approvalId(), Map.of("apiKey", "ZILLIZ_CLOUD_API_KEY"), binding.secretRevisions());
                    f.bindings.put(ExternalIntegrationFixture.key(binding.scope()), new ExternalIntegrationStore.Row<>(changed,
                        f.integrity.seal(ExternalIntegrationService.bindingEvidence(changed, row.status(), row.revision())), row.status(), row.revision()));
                }
            }
            assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId()));
            assertThat(f.dnsCalls).hasValue(0);
        }
    }

    @Test void missingOrChangedCompilerEvidenceTargetVersionOrBindingSignatureBlocksResolution() throws Exception {
        for (String fault : new String[]{"compiler", "target", "version", "signature"}) {
            var f = new ExternalIntegrationFixture(); f.bind();
            switch (fault) {
                case "compiler" -> f.version.setCompositionProvenanceJson(f.version.getCompositionProvenanceJson()
                    .replace("profileFingerprint", "invalidFingerprint"));
                case "target" -> f.target.setEnvironmentName("changed-environment");
                case "version" -> f.version.setConfigHash("changed-version-hash");
                case "signature" -> f.bindings.replaceAll((key, row) -> new ExternalIntegrationStore.Row<>(row.value(), "0".repeat(64), row.status(), row.revision()));
            }
            assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId()));
        }
    }

    @Test void productionRequiresItsOwnApprovalBindingAndCredentialsNeverStagingFallback() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind();
        var production = new DeploymentTargetProfileEntity(); BeanUtils.copyProperties(f.target, production);
        production.setId("target-production"); production.setEnvironmentName("production");
        when(f.targets.findById(production.getId())).thenReturn(Optional.of(production));
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), production.getId())).hasMessageContaining("BINDING_REQUIRED");
        var review = f.approvalRequest(); f.staff();
        var approval = f.service.approve("offer-generic", f.deployment.getId(), new ApprovalRequest(review.versionId(), production.getId(), review.bindingSlotId(),
            review.selectedProfileId(), review.config(), review.accountScope(), review.reviewReference(), "production-review"));
        f.customer();
        f.service.bind("offer-generic", f.deployment.getId(), new BindRequest(f.version.getId(), production.getId(), f.profile.bindingSlotId(), f.profile.id(),
            approval.id(), 3L, "production-bind", f.mapper.createObjectNode().put("apiKey", "synthetic-production-key")));
        var stage = f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), f.target.getId()).getFirst();
        var prod = f.resolver.resolveLocal(f.deployment.getId(), f.version.getId(), production.getId()).getFirst();
        assertThat(prod.secretEnvironment()).isNotEqualTo(stage.secretEnvironment());
        assertThat(f.bindings).hasSize(2); assertThat(f.secretRows).hasSize(2);
    }

    @Test void releaseIsFrozenAgainstRuntimeAuthorityAndGenerationReturnsTypedSecretReferencesOnly() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); f.release();
        var snapshot = f.resolver.freezeRelease(f.deployment.getId(), f.version.getId(), f.target.getId(), "release-generic", "authority-fingerprint");
        assertThat(f.resolver.freezeRelease(f.deployment.getId(), f.version.getId(), f.target.getId(), "release-generic", "authority-fingerprint")).isEqualTo(snapshot);
        var environment = f.resolver.releaseEnvironment(f.deployment.getId(), f.version.getId(), f.target.getId(), "release-generic", "authority-fingerprint");
        assertThat(environment.configEnvironment()).hasSize(1); assertThat(environment.secretEnvironment()).hasSize(1);
        assertThat(environment.secretEnvironment().values()).allMatch(value -> value.startsWith("MANAGED_EXTERNAL_INTEGRATION_CREDENTIAL_"));
        assertThat(f.codec.write(environment)).doesNotContain("synthetic-customer-key");
        assertThat(f.dnsCalls.get()).isGreaterThanOrEqualTo(3);
        verify(f.store, times(1)).insertSnapshot(any(), any(), anyString());
        var binding = f.bindings.values().iterator().next().value();
        f.service.revoke("offer-generic", f.deployment.getId(), f.revokeRequest(binding.id()));
        assertThatThrownBy(() -> f.resolver.releaseEnvironment(f.deployment.getId(), f.version.getId(), f.target.getId(), "release-generic", "authority-fingerprint"))
            .hasMessageContaining("BINDING_STALE_OR_FOREIGN");
    }

    @Test void workerEnvironmentUsesExactTypedAuthorityAndNeverTheOperatorMethod() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); f.release();
        f.resolver.freezeRelease(f.deployment.getId(), f.version.getId(), f.target.getId(), "release-generic", "authority-fingerprint");
        var release = f.releases.findById("release-generic").orElseThrow();
        release.setRuntimeAuthorityFingerprint("authority-fingerprint");
        var checked = f.authority.requireReleaseUnchanged(f.deployment.getId(), f.target.getId(), release.getId(), "authority-fingerprint");
        when(f.authority.requireReleaseUnchangedForProvisioning(f.deployment, f.version, release)).thenReturn(checked);
        clearInvocations(f.authority); SecurityContextHolder.clearContext();
        assertThat(f.resolver.releaseEnvironmentForProvisioning(f.deployment, f.version, release).secretEnvironment()).hasSize(1);
        verify(f.authority).requireReleaseUnchangedForProvisioning(f.deployment, f.version, release);
        verify(f.authority, never()).requireReleaseUnchanged(any(), any(), any(), any());
        int before = f.dnsCalls.get();
        doThrow(new IllegalStateException("FROZEN_AUTHORITY_CHANGED")).when(f.authority)
            .requireReleaseUnchangedForProvisioning(f.deployment, f.version, release);
        assertThatThrownBy(() -> f.resolver.releaseEnvironmentForProvisioning(f.deployment, f.version, release))
            .hasMessage("FROZEN_AUTHORITY_CHANGED");
        assertThat(f.dnsCalls.get()).isEqualTo(before);
    }
}
