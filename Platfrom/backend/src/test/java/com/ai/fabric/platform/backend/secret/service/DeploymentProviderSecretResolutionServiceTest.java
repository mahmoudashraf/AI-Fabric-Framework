package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingMode;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentProviderSecretResolutionServiceTest {

    @Test
    void resolveUsesDeploymentOverrideBeforeGlobalDefault() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        DeploymentProviderSecretBindingEntity binding = new DeploymentProviderSecretBindingEntity();
        binding.setDeploymentId("dep-123");
        binding.setSecretPurpose("OPENAI_API_KEY");
        binding.setBindingMode(DeploymentProviderSecretBindingMode.ALLOW_STANDARD_PRECEDENCE);
        binding.setSecretName("DEPLOYMENT_OPENAI_OVERRIDE");
        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "OPENAI_API_KEY")).thenReturn(Optional.of(binding));
        when(platformSecretService.isSecretPresent("DEPLOYMENT_OPENAI_OVERRIDE")).thenReturn(true);
        when(platformSecretService.resolveSecret("DEPLOYMENT_OPENAI_OVERRIDE")).thenReturn("override-value");

        PlatformSecretEntity entity = new PlatformSecretEntity();
        entity.setName("DEPLOYMENT_OPENAI_OVERRIDE");
        entity.setScopeType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        entity.setOwnerType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType.DEPLOYMENT);
        entity.setUpdatedAt(Instant.now());
        when(platformSecretRepository.findById("DEPLOYMENT_OPENAI_OVERRIDE")).thenReturn(Optional.of(entity));

        DeploymentSecretResolutionSummary summary = service.summarize("dep-123", "OPENAI_API_KEY");

        assertThat(summary.resolved()).isTrue();
        assertThat(summary.secretName()).isEqualTo("DEPLOYMENT_OPENAI_OVERRIDE");
        assertThat(summary.scopeType()).isEqualTo("DEPLOYMENT_OVERRIDE");
        assertThat(summary.reasonCode()).isEqualTo("DEPLOYMENT_OVERRIDE_PRESENT");
    }

    @Test
    void resolveFallsBackToManagedSecretBeforeGlobalDefault() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "PINECONE_API_KEY")).thenReturn(Optional.empty());
        when(platformSecretService.isSecretPresent("MANAGED_PINECONE_API_KEY_DEP_DEP_123")).thenReturn(true);
        when(platformSecretService.resolveSecret("MANAGED_PINECONE_API_KEY_DEP_DEP_123")).thenReturn("managed-value");

        DeploymentSecretResolutionSummary summary =
            service.summarize("dep-123", "PINECONE_API_KEY", "MANAGED_PINECONE_API_KEY_DEP_DEP_123");

        assertThat(summary.resolved()).isTrue();
        assertThat(summary.secretName()).isEqualTo("MANAGED_PINECONE_API_KEY_DEP_DEP_123");
        assertThat(summary.scopeType()).isEqualTo("DEPLOYMENT_MANAGED");
        assertThat(summary.reasonCode()).isEqualTo("DEPLOYMENT_MANAGED_PRESENT");
    }

    @Test
    void resolveAllowsGlobalFallbackWhenOverrideBindingIsMissing() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        DeploymentProviderSecretBindingEntity binding = new DeploymentProviderSecretBindingEntity();
        binding.setDeploymentId("dep-123");
        binding.setSecretPurpose("WEAVIATE_API_KEY");
        binding.setBindingMode(DeploymentProviderSecretBindingMode.ALLOW_STANDARD_PRECEDENCE);
        binding.setSecretName("DEPLOYMENT_WEAVIATE_OVERRIDE");
        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "WEAVIATE_API_KEY")).thenReturn(Optional.of(binding));
        when(platformSecretService.isSecretPresent("DEPLOYMENT_WEAVIATE_OVERRIDE")).thenReturn(false);
        when(platformSecretService.isSecretPresent("WEAVIATE_API_KEY")).thenReturn(true);
        when(platformSecretService.resolveSecret("WEAVIATE_API_KEY")).thenReturn("global-value");
        PlatformSecretEntity entity = new PlatformSecretEntity();
        entity.setName("WEAVIATE_API_KEY");
        entity.setScopeType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType.GLOBAL_PLATFORM);
        entity.setOwnerType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType.PLATFORM);
        when(platformSecretRepository.findById("WEAVIATE_API_KEY")).thenReturn(Optional.of(entity));

        DeploymentSecretResolutionSummary summary = service.summarize("dep-123", "WEAVIATE_API_KEY");

        assertThat(summary.resolved()).isTrue();
        assertThat(summary.secretName()).isEqualTo("WEAVIATE_API_KEY");
        assertThat(summary.fallbackUsed()).isTrue();
        assertThat(summary.reasonCode()).isEqualTo("GLOBAL_DEFAULT_PRESENT");
    }

    @Test
    void resolveFailsWhenOverrideIsRequiredButMissing() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        DeploymentProviderSecretBindingEntity binding = new DeploymentProviderSecretBindingEntity();
        binding.setDeploymentId("dep-123");
        binding.setSecretPurpose("OPENAI_API_KEY");
        binding.setBindingMode(DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE);
        binding.setSecretName("DEPLOYMENT_OPENAI_OVERRIDE");
        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "OPENAI_API_KEY")).thenReturn(Optional.of(binding));
        when(platformSecretService.isSecretPresent("DEPLOYMENT_OPENAI_OVERRIDE")).thenReturn(false);

        DeploymentSecretResolutionSummary summary = service.summarize("dep-123", "OPENAI_API_KEY");

        assertThat(summary.resolved()).isFalse();
        assertThat(summary.reasonCode()).isEqualTo("DEPLOYMENT_OVERRIDE_REQUIRED_MISSING");
    }

    @Test
    void resolveMilvusCredentialsAtomically() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "MILVUS_RUNTIME_CREDENTIALS")).thenReturn(Optional.empty());
        when(platformSecretService.isSecretPresent("MILVUS_USERNAME")).thenReturn(true);
        when(platformSecretService.isSecretPresent("MILVUS_PASSWORD")).thenReturn(false);

        DeploymentSecretResolutionSummary summary = service.summarize("dep-123", "MILVUS_RUNTIME_CREDENTIALS");

        assertThat(summary.resolved()).isFalse();
        assertThat(summary.reasonCode()).isEqualTo("NO_EFFECTIVE_SECRET");
    }

    @Test
    void resolveInfersManagedSecretNamesFromDeploymentId() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformSecretRepository platformSecretRepository = mock(PlatformSecretRepository.class);
        DeploymentProviderSecretBindingRepository bindingRepository = mock(DeploymentProviderSecretBindingRepository.class);
        DeploymentProviderSecretResolutionService service = new DeploymentProviderSecretResolutionService(
            platformSecretService,
            platformSecretRepository,
            bindingRepository,
            new DeploymentSecretPurposeCatalog()
        );

        when(bindingRepository.findByDeploymentIdAndSecretPurpose("dep-123", "QDRANT_API_KEY")).thenReturn(Optional.empty());
        when(platformSecretService.isSecretPresent("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn(true);
        when(platformSecretService.resolveSecret("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn("managed-qdrant-key");

        PlatformSecretEntity entity = new PlatformSecretEntity();
        entity.setName("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123");
        entity.setScopeType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType.DEPLOYMENT_MANAGED);
        entity.setOwnerType(com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType.PLATFORM_MANAGED);
        entity.setUpdatedAt(Instant.now());
        when(platformSecretRepository.findById("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn(Optional.of(entity));

        DeploymentSecretResolutionSummary summary = service.summarize("dep-123", "QDRANT_API_KEY");

        assertThat(summary.resolved()).isTrue();
        assertThat(summary.secretName()).isEqualTo("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123");
        assertThat(summary.scopeType()).isEqualTo("DEPLOYMENT_MANAGED");
        assertThat(summary.reasonCode()).isEqualTo("DEPLOYMENT_MANAGED_PRESENT");
    }
}
