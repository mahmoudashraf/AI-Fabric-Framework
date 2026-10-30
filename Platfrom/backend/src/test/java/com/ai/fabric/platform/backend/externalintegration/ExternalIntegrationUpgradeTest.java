package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationUpgradeTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ordinaryUpgradeReusesOwnedCredentialsWithoutNewApprovalOrBootstrapPluginPins() throws Exception {
        var f = new ExternalIntegrationFixture(); var binding = f.bind();
        var version = nextVersion(f);
        var provenance = (ObjectNode) f.codec.tree(version.getCompositionProvenanceJson());
        provenance.put("templateId", "different-template");
        provenance.withArray("marketplaceInstalls").addObject().put("pluginId", "additional-plugin")
            .put("pluginVersionId", "additional-version").put("pluginType", "ACTION");
        version.setCompositionProvenanceJson(provenance.toString());
        f.grant.setRevision(100); f.grant.setExpiresAt(Instant.EPOCH);
        clearInvocations(f.store, f.vault, f.composition);

        var result = f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().bindingId()).isEqualTo(binding.id());
        assertThat(result.getFirst().scope().versionId()).isEqualTo(version.getId());
        assertThat(f.bindings).hasSize(1); assertThat(f.approvals).hasSize(1);
        verify(f.store, never()).insertApproval(any(), anyString());
        verify(f.store, never()).insertBinding(any(), anyString());
        verify(f.vault, never()).upsertManagedSecret(anyString(), anyString(), anyMap());
        verify(f.vault, never()).upsertTargetManagedSecret(anyString(), anyString(), anyString(), anyString(), anyMap());
        verifyNoInteractions(f.composition);
        verify(f.entitlements, never()).requireEntitlement(anyString(), anyString(), any(), anyBoolean());
    }

    @Test void compatibleIntegrationPluginUpgradeUsesActualCompilerEvidenceAndCurrentAliases() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); var version = nextVersion(f);
        var upgraded = new MarketplacePluginVersionEntity();
        BeanUtils.copyProperties(f.pluginVersions.findById(f.profile.pluginVersionId()).orElseThrow(), upgraded);
        upgraded.setId("integration-version-two"); upgraded.setVersion("2.0.0");
        upgraded.setManifestJson(upgraded.getManifestJson().replace("1.0.0", "2.0.0"));
        when(f.pluginVersions.findById(upgraded.getId())).thenReturn(Optional.of(upgraded));
        var profile = f.policy.forPluginVersion(f.profile, upgraded.getId());
        var before = f.compiler.installInputs(f.profile, f.mapper.createObjectNode(), f.mapper.createObjectNode());
        var recompiled = f.compiler.installInputs(profile, before.config(), before.secretRefs());
        assertThat(recompiled.config().path("endpointUrl").asText())
            .isEqualTo("${" + ExternalIntegrationCompilerResolver.alias(profile, "endpointUrl") + "}");
        assertThat(recompiled.secretRefs().path("apiKey").asText())
            .isEqualTo(ExternalIntegrationCompilerResolver.alias(profile, "apiKey"));
        var provenance = f.mapper.createObjectNode();
        provenance.putArray("externalIntegrationProfiles").add(f.compiler.compilerEvidence(profile));
        provenance.putArray("marketplaceInstalls").addObject().put("pluginId", profile.pluginId())
            .put("pluginVersionId", profile.pluginVersionId()).put("pluginType", "ACTION")
            .put("manifestSha256", "sha256:" + profile.manifestSha256());
        version.setCompositionProvenanceJson(provenance.toString());

        var result = f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId()).getFirst();

        assertThat(result.configEnvironment()).containsKey(ExternalIntegrationCompilerResolver.alias(profile, "endpointUrl"));
        assertThat(result.secretEnvironment()).containsKey(ExternalIntegrationCompilerResolver.alias(profile, "apiKey"));
        assertThat(f.approvals).hasSize(1); assertThat(f.bindings).hasSize(1);
    }

    @Test void removedIntegrationDoesNotRemainMandatoryBecauseItWasInTheOriginalTemplate() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); var version = nextVersion(f);
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[],\"externalIntegrationProfiles\":[]}");
        clearInvocations(f.entitlements, f.store);
        assertThat(f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId())).isEmpty();
        verifyNoInteractions(f.entitlements, f.store);
    }

    @Test void revokedBindingIsNotResurrectedForANewerVersion() throws Exception {
        var f = new ExternalIntegrationFixture(); var binding = f.bind();
        f.service.revoke("offer-generic", f.deployment.getId(), f.revokeRequest(binding.id()));
        var version = nextVersion(f);
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId()))
            .hasMessageContaining("BINDING_STALE_OR_FOREIGN");
    }

    @Test void missingOriginalInvitationAndFormerOwnerNeverVetoAQualifiedVersionUpgrade() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); var version = nextVersion(f);
        when(f.ownerRepository.findByCustomerIdAndOfferId(anyString(), anyString())).thenReturn(Optional.empty());
        when(f.ownerRepository.findForUpdate(anyString(), anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("former owner left")).when(f.entitlements)
            .requireCurrentOwnerEligibility(anyString(), anyString());
        assertThat(f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId())).hasSize(1);
        assertThat(f.resolver.requireForEffects(f.deployment.getId(), version.getId(), f.target.getId())).hasSize(1);
        verify(f.entitlements, never()).requireCurrentOwnerEligibility(anyString(), anyString());
    }

    @Test void revokedLatestBindingNeverSelectsAnOlderStillConfiguredBinding() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); var version = nextVersion(f);
        var old = f.bindings.values().iterator().next().value();
        var latestScope = new Scope(old.scope().offerId(), old.scope().customerId(), old.scope().tenantId(), old.scope().deploymentId(),
            "newer-bound-version", old.scope().targetProfileId(), old.scope().bindingSlotId(), old.scope().profileId());
        var revoked = new Binding("newer-binding", latestScope, "newer-approval", old.secretReferences(), old.secretRevisions());
        var latest = new ExternalIntegrationStore.Row<>(revoked,
            f.integrity.seal(ExternalIntegrationService.bindingEvidence(revoked, "REVOKED", 1)), "REVOKED", 1);
        when(f.store.latestBinding(f.deployment.getId(), f.target.getId(), f.profile.bindingSlotId()))
            .thenReturn(Optional.of(latest));
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId()))
            .hasMessageContaining("BINDING_STALE_OR_FOREIGN");
        assertThat(f.bindings.values()).singleElement().extracting(ExternalIntegrationStore.Row::status).isEqualTo("CONFIGURED");
    }

    @Test void accountSuspensionAndCurrentCompilerTamperingStillBlockUpgrades() throws Exception {
        var f = new ExternalIntegrationFixture(); f.bind(); var version = nextVersion(f);
        doThrow(new IllegalStateException("owner suspended")).when(f.entitlements)
            .requireActiveCustomer(f.deployment.getCustomerId());
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId()))
            .hasMessageContaining("owner suspended");
        reset(f.entitlements);
        var offer = new com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity(
            "offer-generic", f.codec.write(f.definition), "fingerprint", "review", "staff", Instant.now());
        when(f.entitlements.requireOffer("offer-generic")).thenReturn(offer);
        when(f.entitlements.definition(offer)).thenReturn(f.definition);
        version.setCompositionProvenanceJson(version.getCompositionProvenanceJson().replace("sha256:", "tampered:"));
        assertThatThrownBy(() -> f.resolver.resolveLocal(f.deployment.getId(), version.getId(), f.target.getId()))
            .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
    }

    private DeploymentVersionEntity nextVersion(ExternalIntegrationFixture f) {
        var version = new DeploymentVersionEntity(); BeanUtils.copyProperties(f.version, version);
        version.setId("version-upgraded"); version.setConfigHash("upgraded-config-hash");
        when(f.versions.findById(version.getId())).thenReturn(Optional.of(version));
        return version;
    }
}
