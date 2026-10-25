package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingMode;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.model.UpsertDeploymentProviderSecretBindingRequest;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentProviderSecretOverrideServiceTest {

    private final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    private final DeploymentProviderSecretBindingRepository bindings = mock(DeploymentProviderSecretBindingRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final CustomerOfferDeploymentRepository offerDeployments = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentProviderSecretResolutionService resolution = mock(DeploymentProviderSecretResolutionService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final DeploymentProviderSecretOverrideService service = new DeploymentProviderSecretOverrideService(
        secrets, bindings, deployments, access, offerDeployments, new DeploymentSecretPurposeCatalog(), resolution, audit
    );
    private final DeploymentEntity deployment = new DeploymentEntity();

    @BeforeEach
    void setUp() {
        deployment.setId("dep-owned");
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(access.requireDeploymentAdminAccess(deployment)).thenReturn(deployment);
    }

    @Test
    void templateOriginDoesNotPreventBindingAnOwnedCredential() {
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);
        PlatformSecretEntity own = secret("OWN_OPENAI", deployment.getId(), "OPENAI_API_KEY",
            PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        when(secrets.findById(own.getName())).thenReturn(Optional.of(own));
        assertThat(service.upsertBinding(deployment.getId(), request(own.getName())).secretName())
            .isEqualTo(own.getName());
        verify(bindings).save(any(DeploymentProviderSecretBindingEntity.class));
    }

    @Test
    void samePurposeSecretOwnedByAnotherDeploymentCannotBeBound() {
        PlatformSecretEntity foreign = secret("FOREIGN_OPENAI", "dep-other", "OPENAI_API_KEY", PlatformSecretCleanupPolicy.KEEP);
        when(secrets.findById(foreign.getName())).thenReturn(Optional.of(foreign));

        assertForbidden(() -> service.upsertBinding(deployment.getId(), request(foreign.getName())));

        verifyNoInteractions(bindings, resolution, audit);
        verify(secrets, never()).save(any());
    }

    @Test
    void foreignSecondarySecretCannotBeUsedInPairedBinding() {
        PlatformSecretEntity primary = secret("OWN_USERNAME", deployment.getId(), "MILVUS_RUNTIME_CREDENTIALS",
            PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        PlatformSecretEntity secondary = secret("FOREIGN_PASSWORD", "dep-other", "MILVUS_RUNTIME_CREDENTIALS",
            PlatformSecretCleanupPolicy.KEEP);
        when(secrets.findById(primary.getName())).thenReturn(Optional.of(primary));
        when(secrets.findById(secondary.getName())).thenReturn(Optional.of(secondary));

        assertForbidden(() -> service.upsertBinding(deployment.getId(), new UpsertDeploymentProviderSecretBindingRequest(
            "MILVUS_RUNTIME_CREDENTIALS", "REQUIRE_OVERRIDE", primary.getName(), secondary.getName()
        )));

        verifyNoInteractions(bindings, resolution, audit);
    }

    @Test
    void ownDeploymentSecretCanBeBound() {
        PlatformSecretEntity own = secret("OWN_OPENAI", deployment.getId(), "OPENAI_API_KEY",
            PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        when(secrets.findById(own.getName())).thenReturn(Optional.of(own));

        var summary = service.upsertBinding(deployment.getId(), request(own.getName()));

        assertThat(summary.secretName()).isEqualTo(own.getName());
        assertThat(summary.deploymentId()).isEqualTo(deployment.getId());
        verify(bindings).save(any(DeploymentProviderSecretBindingEntity.class));
    }

    @Test
    void explicitlyReusableUnownedSecretsRemainAvailable() {
        for (PlatformSecretCleanupPolicy policy : List.of(PlatformSecretCleanupPolicy.KEEP,
            PlatformSecretCleanupPolicy.DELETE_WHEN_UNREFERENCED)) {
            PlatformSecretEntity shared = secret("SHARED_" + policy.name(), null, "OPENAI_API_KEY", policy);
            when(secrets.findById(shared.getName())).thenReturn(Optional.of(shared));

            assertThat(service.upsertBinding(deployment.getId(), request(shared.getName())).secretName())
                .isEqualTo(shared.getName());
        }
    }

    @Test
    void unownedSecretWithoutExplicitReusablePolicyIsRejected() {
        PlatformSecretEntity unowned = secret("UNOWNED_OPENAI", null, "OPENAI_API_KEY", null);
        when(secrets.findById(unowned.getName())).thenReturn(Optional.of(unowned));

        assertForbidden(() -> service.upsertBinding(deployment.getId(), request(unowned.getName())));

        verifyNoInteractions(bindings, resolution, audit);
    }

    @Test
    void candidatesExcludeForeignAndUnclassifiedSecrets() {
        PlatformSecretEntity own = secret("OWN_OPENAI", deployment.getId(), "OPENAI_API_KEY",
            PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        PlatformSecretEntity shared = secret("SHARED_OPENAI", null, "OPENAI_API_KEY", PlatformSecretCleanupPolicy.KEEP);
        PlatformSecretEntity foreign = secret("FOREIGN_OPENAI", "dep-other", "OPENAI_API_KEY", PlatformSecretCleanupPolicy.KEEP);
        PlatformSecretEntity unclassified = secret("UNCLASSIFIED_OPENAI", null, "OPENAI_API_KEY", null);
        when(secrets.findByScopeTypeOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE))
            .thenReturn(List.of(own, shared, foreign, unclassified));

        assertThat(service.listBindings(deployment.getId()).availableOverrideSecrets())
            .extracting(item -> item.name()).containsExactly(own.getName(), shared.getName());

        verify(bindings, never()).countBySecretName(foreign.getName());
        verify(bindings, never()).countBySecretName(unclassified.getName());
    }

    @Test
    void templateOriginDoesNotPreventRemovingAnOwnedCredentialBinding() {
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);
        DeploymentProviderSecretBindingEntity binding = new DeploymentProviderSecretBindingEntity();
        binding.setId("binding-reviewed");
        binding.setDeploymentId(deployment.getId());
        binding.setSecretPurpose("OPENAI_API_KEY");
        binding.setSecretName("OWN_OPENAI");
        binding.setBindingMode(DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE);
        when(bindings.findByDeploymentIdAndSecretPurpose(deployment.getId(), "OPENAI_API_KEY"))
            .thenReturn(Optional.of(binding));

        service.clearBinding(deployment.getId(), "OPENAI_API_KEY");
        verify(bindings).delete(binding);
        verifyNoInteractions(secrets, resolution);
        assertThat(binding.getSecretName()).isEqualTo("OWN_OPENAI");
        assertThat(binding.getBindingMode()).isEqualTo(DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE);
    }

    @Test
    void ordinaryDeploymentCanRemoveAnExistingBindingWithoutAccessingOrReplacingSecrets() {
        DeploymentProviderSecretBindingEntity binding = new DeploymentProviderSecretBindingEntity();
        binding.setId("binding-owned");
        binding.setDeploymentId(deployment.getId());
        binding.setSecretPurpose("OPENAI_API_KEY");
        binding.setSecretName("OWN_OPENAI");
        binding.setBindingMode(DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE);
        when(bindings.findByDeploymentIdAndSecretPurpose(deployment.getId(), "OPENAI_API_KEY"))
            .thenReturn(Optional.of(binding));

        service.clearBinding(deployment.getId(), "OPENAI_API_KEY");

        verify(bindings).delete(binding);
        verify(bindings, never()).save(any());
        verifyNoInteractions(secrets, resolution);
        assertThat(binding.getSecretName()).isEqualTo("OWN_OPENAI");
        assertThat(binding.getBindingMode()).isEqualTo(DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE);
    }

    @Test
    void removalStillRequiresCurrentDeploymentAdministrationAccess() {
        when(access.requireDeploymentAdminAccess(deployment)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertForbidden(() -> service.clearBinding(deployment.getId(), "OPENAI_API_KEY"));

        verifyNoInteractions(secrets, bindings, resolution, audit);
    }

    private UpsertDeploymentProviderSecretBindingRequest request(String name) {
        return new UpsertDeploymentProviderSecretBindingRequest("OPENAI_API_KEY", "REQUIRE_OVERRIDE", name, null);
    }

    private PlatformSecretEntity secret(String name, String deploymentId, String purpose, PlatformSecretCleanupPolicy policy) {
        PlatformSecretEntity secret = new PlatformSecretEntity();
        secret.setName(name);
        secret.setDeploymentId(deploymentId);
        secret.setScopeType(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        secret.setOwnerType(PlatformSecretOwnerType.DEPLOYMENT);
        secret.setSecretPurpose(purpose);
        secret.setCleanupPolicy(policy);
        secret.setSecretValue("test-secret");
        return secret;
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable command) {
        assertThatThrownBy(command).isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
