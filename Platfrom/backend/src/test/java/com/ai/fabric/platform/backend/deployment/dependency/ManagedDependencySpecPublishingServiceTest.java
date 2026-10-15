package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManagedDependencySpecPublishingServiceTest {
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final MarketplacePluginVersionRepository versions = mock(MarketplacePluginVersionRepository.class);
    private final MarketplacePluginRepository plugins = mock(MarketplacePluginRepository.class);
    private final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
    private final CustomerOfferEntitlementService staff = mock(CustomerOfferEntitlementService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(
        new ObjectMapper().findAndRegisterModules());
    private final ManagedDependencySpecPublishingService service = new ManagedDependencySpecPublishingService(
        specs, validator, versions, plugins, artifacts, staff, audit,
        new ObjectMapper().findAndRegisterModules());
    private MarketplacePluginVersionEntity templateVersion;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("admin@example.test", PlatformRole.PLATFORM_ADMIN, "Admin", "SESSION"),
            null, List.of()));
        templateVersion = new MarketplacePluginVersionEntity();
        templateVersion.setId("template-version-1"); templateVersion.setPluginId("template-1");
        templateVersion.setStatus("PUBLISHED"); templateVersion.setPublishedAt(Instant.now());
        templateVersion.setManifestJson("{}");
        var plugin = new MarketplacePluginEntity();
        plugin.setId("template-1"); plugin.setPluginType("TEMPLATE"); plugin.setStatus("ACTIVE");
        when(versions.findById("template-version-1")).thenReturn(Optional.of(templateVersion));
        when(plugins.findById("template-1")).thenReturn(Optional.of(plugin));
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of());
        for (var dependency : ManagedDependencyFixtures.topology().services()) {
            when(artifacts.findById(dependency.sourceArtifactId())).thenReturn(Optional.of(artifact(dependency)));
        }
        when(specs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesImmutableArtifactPinnedTopologyForExactTemplateVersion() {
        var result = service.publish("template-version-1",
            new ManagedDependencySpecPublishingService.PublicationRequest(
                "dependency-spec-1", ManagedDependencyFixtures.topology(), "review-123"));

        assertThat(result.templateVersionId()).isEqualTo("template-version-1");
        assertThat(result.fingerprint()).hasSize(64);
        verify(staff).requireStaffAdmin();
        verify(specs).saveAndFlush(argThat(entity -> entity.isActive()
            && "review-123".equals(entity.getReviewReference())
            && "admin@example.test".equals(entity.getReviewedBy())));
        verify(audit).record(eq("MANAGED_DEPENDENCY_SPEC_REVIEWED"),
            eq("MARKETPLACE_TEMPLATE_VERSION"), eq("template-version-1"), anyMap());
    }

    @Test
    void readsBackAndRevalidatesTheExactPublishedTopology() throws Exception {
        var topology = ManagedDependencyFixtures.topology();
        var entity = new ManagedDependencySpecEntity();
        entity.setId("dependency-spec-1");
        entity.setTemplateVersionId("template-version-1");
        entity.setSpecJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(topology));
        entity.setFingerprint(validator.fingerprint(topology));
        entity.setReviewReference("review-123");
        entity.setReviewedBy("admin@example.test");
        entity.setReviewedAt(Instant.now());
        entity.setActive(true);
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1")).thenReturn(List.of(entity));

        var current = service.current("template-version-1");

        assertThat(current.publication().specId()).isEqualTo("dependency-spec-1");
        assertThat(current.spec()).isEqualTo(topology);
        verify(staff).requireStaffAdmin();
    }

    @Test
    void rejectsArtifactThatDoesNotMatchReviewedImage() {
        var first = ManagedDependencyFixtures.topology().services().getFirst();
        var artifact = artifact(first);
        artifact.setImageDigest("sha256:" + "f".repeat(64));
        when(artifacts.findById(first.sourceArtifactId())).thenReturn(Optional.of(artifact));

        assertThatThrownBy(() -> service.publish("template-version-1",
            new ManagedDependencySpecPublishingService.PublicationRequest(
                "dependency-spec-1", ManagedDependencyFixtures.topology(), "review-123")))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_NOT_IMMUTABLE");
        verify(specs, never()).saveAndFlush(any());
    }

    @Test
    void refusesSecondTopologyForSameTemplateVersion() {
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version-1"))
            .thenReturn(List.of(new ManagedDependencySpecEntity()));

        assertThatThrownBy(() -> service.publish("template-version-1",
            new ManagedDependencySpecPublishingService.PublicationRequest(
                "dependency-spec-2", ManagedDependencyFixtures.topology(), "review-124")))
            .hasMessageContaining("DEPENDENCY_SPEC_IMMUTABLE");
    }

    @Test
    void requiresManagedTemplateDefaultsToHaveConnectorRuntimeBindings() {
        templateVersion.setManifestJson("""
            {
              "contributions": {"template": {
                "requiredPluginInstallDefaults": {
                  "mkp-data-managed@1.0.0": {
                    "config": {
                      "providerBaseUrl": "${MANAGED_SIMULATOR_BASE_URL}",
                      "providerHost": "${MANAGED_SIMULATOR_HOST}",
                      "accountId": "${MANAGED_SIMULATOR_ACCOUNT_ID}"
                    },
                    "secretRefs": {"providerKey": "MANAGED_SIMULATOR_API_KEY"}
                  }
                }
              }}
            }
            """);

        assertThat(service.publish("template-version-1",
            new ManagedDependencySpecPublishingService.PublicationRequest(
                "dependency-spec-1", ManagedDependencyFixtures.topology(), "review-123")))
            .extracting(ManagedDependencySpecPublishingService.PublicationSummary::specId)
            .isEqualTo("dependency-spec-1");
    }

    @Test
    void rejectsManagedTemplateDefaultWithoutConnectorRuntimeBinding() {
        templateVersion.setManifestJson("""
            {
              "contributions": {"template": {
                "requiredPluginInstallDefaults": {
                  "mkp-data-managed@1.0.0": {
                    "config": {"providerBaseUrl": "${MANAGED_UNKNOWN_BASE_URL}"}
                  }
                }
              }}
            }
            """);

        assertThatThrownBy(() -> service.publish("template-version-1",
            new ManagedDependencySpecPublishingService.PublicationRequest(
                "dependency-spec-1", ManagedDependencyFixtures.topology(), "review-123")))
            .hasMessageContaining("DEPENDENCY_RUNTIME_BINDING_MISSING")
            .hasMessageContaining("MANAGED_UNKNOWN_BASE_URL");
        verify(specs, never()).saveAndFlush(any());
    }

    private DeploymentSourceArtifactEntity artifact(ManagedDependencySpec.Service dependency) {
        var artifact = new DeploymentSourceArtifactEntity();
        artifact.setId(dependency.sourceArtifactId());
        artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setArtifactRole(DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name());
        int separator = dependency.image().indexOf("@sha256:");
        artifact.setImageRepository(dependency.image().substring(0, separator));
        artifact.setImageDigest(dependency.image().substring(separator + 1));
        artifact.setGitCommitSha(dependency.sourceCommit());
        artifact.setCapabilityManifestHash("manifest-hash");
        artifact.setPromotionChannel("staging");
        artifact.setPromotedAt(Instant.now());
        return artifact;
    }
}
