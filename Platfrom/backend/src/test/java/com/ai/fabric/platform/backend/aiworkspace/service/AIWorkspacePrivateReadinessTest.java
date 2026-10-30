package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AIWorkspacePrivateReadinessTest {
    private static final String ORIGIN = "https://customer.example";
    private static final String PUBLIC_KEY = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    private static final String PRIVATE_KEY = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY";
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final AIWorkspaceAssignmentService assignments = mock(AIWorkspaceAssignmentService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final AIWorkspaceDomainService domains = mock(AIWorkspaceDomainService.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final PlatformConsumerEntity consumer = new PlatformConsumerEntity();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final ObjectNode security = mapper.createObjectNode();
    private final AIWorkspaceReadinessService service;

    AIWorkspacePrivateReadinessTest() {
        var properties = new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60),
            null, "https://shopify-bridge.example", "apps.example", "https://workspace.example");
        var environment = new MockEnvironment().withProperty("platform.support.enabled", "true")
            .withProperty("platform.support.customer-id", "customer-1");
        service = new AIWorkspaceReadinessService(consumers, assignments, assets, packs,
            new AIWorkspaceConnectionProfileRegistry(properties, environment),
            new AIWorkspaceAuthenticationProfileRegistry(mapper), domains, secrets, authority, properties, mapper);
        consumer.setId("consumer-1"); consumer.setCustomerId("customer-1"); consumer.setConsumerId("customer-web");
        consumer.setBoundDeploymentId("dep-1"); consumer.setBoundReleaseId("rel-1"); consumer.setBoundTargetProfileId("target-stage");
        deployment.setId("dep-1"); deployment.setCustomerId("customer-1"); deployment.setTenantId("tenant-1");
        version.setId("ver-1"); version.setDeploymentId(deployment.getId());
        release.setId("rel-1"); release.setDeploymentId(deployment.getId()); release.setDeploymentVersionId(version.getId());
        release.setTargetProfileId("target-stage"); release.setRuntimeAuthoritySnapshotId("snapshot-1");
        release.setRuntimeAuthorityFingerprint("fingerprint-1");
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(assignments.resolveTarget(consumer, deployment.getId(), release.getId()))
            .thenReturn(new AIWorkspaceAssignmentService.Assignment(consumer, deployment, release, version, security,
                "https://runtime.customer.example", "revision-1"));
        when(assets.health()).thenReturn(new AIWorkspaceAssetCatalogService.CatalogHealth(true, "READY", "1.1.0"));
        var asset = new AIWorkspaceAssetDescriptor("experience-pack", "generic-workspace", "1.0.0", "/pack.js", "pack.js",
            "a".repeat(64), "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 10);
        when(packs.resolve("generic-workspace", "1.0.0"))
            .thenReturn(new AIWorkspaceExperiencePackRegistry.ExperiencePack("generic-workspace", "1.0.0", "Generic",
                "loomai-generic-workspace-experience-config-v1", true, asset));
    }

    @ParameterizedTest @ValueSource(strings = {AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST, AIWorkspaceConnectionProfileRegistry.SHOPIFY})
    void privateProfilesUseAssignedFrozenTargetWithoutLegacyGlobalConfiguration(String profile) {
        var binding = new RuntimeAuthorityBinding("customer-1", "tenant-1", deployment.getId(), release.getTargetProfileId(),
            version.getId(), "config-hash", 1, "platform-target:target-stage", "runtime-private:target-stage",
            "public-target:target-stage", "runtime-public:target-stage", false, false, List.of(),
            Map.of(RuntimeAuthorityPurpose.PRIVATE_ASSERTION_SIGNING_KEY,
                new RuntimeAuthorityBinding.SecretRevision("MANAGED_RTA_PRIVATE_TARGET_STAGE", "target-key-revision")));
        when(authority.requireReleaseUnchangedForRead(deployment, version, release))
            .thenReturn(new RuntimeAuthorityBinding.Snapshot("snapshot-1", release.getId(), "fingerprint-1", binding));
        var installation = installation(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME, profile);

        var readiness = service.evaluate(installation);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).filteredOn(check -> check.code().startsWith("PRIVATE_ASSERTION_"))
            .hasSize(2).allSatisfy(check -> assertThat(check.status()).isEqualTo("PASSED"));
        assertThat(service.evaluateLocal(installation).ready()).isTrue();
        verify(authority, times(2)).requireReleaseUnchangedForRead(same(deployment), same(version), same(release));
        verify(authority, never()).requireReleaseUnchangedForProvisioning(any(), any(), any());
        verifyNoInteractions(secrets, domains);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing snapshot", "missing target key", "stale release", "foreign target", "foreign owner", "invalid integrity"})
    void invalidFrozenAuthorityBlocksEvenWhenLegacyGlobalSettingsAreComplete(String failure) {
        security.put("privateRuntimeAcceptedIssuers", "legacy-global-issuer")
            .put("privateRuntimeAcceptedAudiences", "legacy-global-audience");
        when(secrets.isSecretPresent(PRIVATE_KEY)).thenReturn(true);
        when(authority.requireReleaseUnchangedForRead(deployment, version, release))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, failure));

        var readiness = service.evaluate(installation(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME,
            AIWorkspaceConnectionProfileRegistry.SHOPIFY));

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.checks()).filteredOn(check -> check.code().startsWith("PRIVATE_ASSERTION_"))
            .hasSize(2).allSatisfy(check -> assertThat(check.status()).isEqualTo("BLOCKED"));
        assertThat(readiness.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("PRIVATE_ASSERTION_POLICY");
            assertThat(check.message()).contains(failure);
        });
        verify(authority).requireReleaseUnchangedForRead(same(deployment), same(version), same(release));
        verify(authority, never()).requireReleaseUnchangedForProvisioning(any(), any(), any());
        verifyNoInteractions(secrets);
    }

    @ParameterizedTest @EnumSource(value = AIWorkspaceConnectionMode.class,
        names = {"PUBLIC_RUNTIME_ANONYMOUS", "PUBLIC_RUNTIME_AUTHENTICATED"})
    void publicModesKeepTheirExistingTokenPolicyAndCorsChecks(AIWorkspaceConnectionMode mode) {
        var installation = installation(mode, mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS
            ? AIWorkspaceConnectionProfileRegistry.ANONYMOUS : AIWorkspaceConnectionProfileRegistry.AUTHENTICATED);
        security.put("publicRuntimeBootstrapEnabled", true).put("publicRuntimeAcceptedIssuers", "public-issuer")
            .put("publicRuntimeAcceptedAudiences", "public-audience").put("publicRuntimeDefaultAudience", "public-audience")
            .put("corsAllowedOrigins", ORIGIN);
        when(secrets.isSecretPresent(PUBLIC_KEY)).thenReturn(true);
        assertThat(service.evaluate(installation).ready()).isTrue();

        when(secrets.isSecretPresent(PUBLIC_KEY)).thenReturn(false);
        assertThat(service.evaluate(installation).checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("PUBLIC_TOKEN_KEY");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
        when(secrets.isSecretPresent(PUBLIC_KEY)).thenReturn(true);
        security.put("publicRuntimeDefaultAudience", "");
        assertThat(service.evaluate(installation).checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("PUBLIC_TOKEN_POLICY");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
        security.put("publicRuntimeDefaultAudience", "public-audience").put("corsAllowedOrigins", "https://other.example");
        assertThat(service.evaluate(installation).checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("RUNTIME_CORS");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
        verifyNoInteractions(authority);
        verify(secrets, never()).isSecretPresent(PRIVATE_KEY);
    }

    private AIWorkspaceInstallationEntity installation(AIWorkspaceConnectionMode mode, String profile) {
        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("workspace-1"); installation.setInstallationId("awi_pub_test");
        installation.setCustomerId(consumer.getCustomerId()); installation.setConsumerEntityId(consumer.getId());
        installation.setExperiencePackCode("generic-workspace"); installation.setExperiencePackVersion("1.0.0");
        installation.setConnectionMode(mode); installation.setConnectionProfileCode(profile); installation.setConnectionProfileVersion("1.0.0");
        installation.setConnectionConfigurationJson(AIWorkspaceConnectionProfileRegistry.SHOPIFY.equals(profile)
            ? "{\"shopDomain\":\"customer.myshopify.com\"}" : "{}");
        installation.setAllowedOriginsJson("[\"" + ORIGIN + "\"]"); installation.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED);
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        installation.setAuthProfileCode(AIWorkspaceAuthenticationProfileRegistry.NONE);
        installation.setAuthProfileVersion("1.0.0"); installation.setAuthConfigurationJson("{}");
        return installation;
    }
}
