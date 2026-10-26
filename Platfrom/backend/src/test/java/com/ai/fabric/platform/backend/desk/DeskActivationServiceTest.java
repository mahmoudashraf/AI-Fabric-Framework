package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.marketplace.model.*;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.entity.PlatformCustomerIdentityLinkEntity;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskActivationServiceTest {
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    final DeploymentDraftRepository drafts = mock(DeploymentDraftRepository.class);
    final DeploymentService deploymentService = mock(DeploymentService.class);
    final DeploymentAccessService access = mock(DeploymentAccessService.class);
    final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    final DeploymentMarketplaceInstallService installs = mock(DeploymentMarketplaceInstallService.class);
    final DeploymentMarketplaceDraftCompilerService compiler = mock(DeploymentMarketplaceDraftCompilerService.class);
    final MarketplacePluginVersionRepository pluginVersions = mock(MarketplacePluginVersionRepository.class);
    final PlatformCustomerIdentityLinkRepository identities = mock(PlatformCustomerIdentityLinkRepository.class);
    final PlatformUserRepository users = mock(PlatformUserRepository.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final PlatformAuditService audit = mock(PlatformAuditService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final DeskBindingProvisioner bindingProvisioner = new DeskBindingProvisioner(bindings, secrets, desk, users, identities,
        new MockEnvironment().withProperty("LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL", "https://console.example"), mapper);
    final DeskActivationService service = new DeskActivationService(bindings, targets, deployments, releases, versions, drafts,
        deploymentService, access, profiles, installs, compiler, pluginVersions, identities, secrets, desk, audit, mapper, bindingProvisioner);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void enableRequiresASupabaseCustomerNotStaffOrAnApiKey() {
        for (var principal : List.of(new PlatformPrincipal("staff", PlatformRole.PLATFORM_ADMIN, "Staff", "SESSION", "staff-id"),
            new PlatformPrincipal("owner", PlatformRole.CUSTOMER_ADMIN, "Owner", "API_KEY", "owner-id"))) {
            authenticate(principal);
            assertThatThrownBy(() -> service.enable("dep-a", new DeskActivationService.EnableRequest("target-a", "rel-a", "enable-a", false)))
                .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(targets, desk, installs, deploymentService);
    }

    @Test void repeatingActivationReturnsTheExistingOperationWithoutPublishingAgain() {
        authenticate(new PlatformPrincipal("owner@example.com", PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE", "owner-id"));
        var deployment = deployment();
        var profile = new DeploymentTargetProfileEntity(); profile.setId("target-a"); profile.setEnvironmentName("production");
        when(targets.resolve("dep-a", "target-a", "rel-a", true)).thenReturn(
            new DeploymentTargetContextService.Context(deployment, profile, null, null, "https://runtime.example", "https://connector.example"));
        var binding = binding(); binding.setStatus("APPLYING"); binding.setReleaseId("rel-desk");
        when(bindings.findByDeploymentIdAndTargetProfileId("dep-a", "target-a")).thenReturn(Optional.of(binding));
        when(desk.connection("production")).thenReturn(new DeskServiceClient.Connection("https://desk.example", "not-exposed"));
        var result = service.enable("dep-a", new DeskActivationService.EnableRequest("target-a", "rel-a", "repeated", false));
        assertThat(result.releaseId()).isEqualTo("rel-desk");
        assertThat(result.status()).isEqualTo("APPLYING");
        verifyNoInteractions(installs, deploymentService, identities, secrets);
    }

    @Test void normalVerifiedUpgradeIsUsedInsteadOfPinningReviewsToTheEnablementRelease() {
        var original = release("rel-original", "ver-original");
        var upgraded = release("rel-upgrade", "ver-upgrade");
        when(releases.findByDeploymentIdOrderByCreatedAtDesc("dep-a")).thenReturn(List.of(upgraded, original));
        var version = new DeploymentVersionEntity(); version.setBehaviorConfigJson(DeskTargetConfigurationResolverTest.managedBehavior(mapper).toString());
        when(versions.findById("ver-upgrade")).thenReturn(Optional.of(version));
        assertThat(service.currentReviewRelease(binding()).getId()).isEqualTo("rel-upgrade");
        verify(versions, never()).findById("ver-original");
    }

    @Test void removingDeskInANewVersionDoesNotFallBackToAnOlderReviewRelease() {
        when(releases.findByDeploymentIdOrderByCreatedAtDesc("dep-a")).thenReturn(
            List.of(release("rel-upgrade", "ver-upgrade"), release("rel-original", "ver-original")));
        var version = new DeploymentVersionEntity(); version.setBehaviorConfigJson("{}");
        when(versions.findById("ver-upgrade")).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.currentReviewRelease(binding())).isInstanceOf(ResponseStatusException.class);
        verify(versions, never()).findById("ver-original");
    }

    @Test void supportBindingsNeverAuthorizeAnotherCustomerAfterAnOwnershipChange() {
        when(bindings.findById("desk-a")).thenReturn(Optional.of(binding()));
        when(secrets.resolveSecret("bridge-key-ref")).thenReturn("private-bridge-key");
        var deployment = deployment(); deployment.setCustomerId("different-customer");
        when(deployments.findById("dep-a")).thenReturn(Optional.of(deployment));
        assertThatThrownBy(() -> service.requireBridgeBinding("desk-a", "private-bridge-key"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void reviewsNeverFallBackBehindANewerActiveUnverifiedReplacement() {
        for (String status : List.of("VERIFYING", "APPLIED_VERIFICATION_FAILED")) {
            var replacement = release("rel-new", "ver-new"); replacement.setStatus(status);
            when(releases.findByDeploymentIdOrderByCreatedAtDesc("dep-a")).thenReturn(List.of(replacement, release("rel-old", "ver-old")));
            assertThatThrownBy(() -> service.currentReviewRelease(binding()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("current deployment target");
        }
        verifyNoInteractions(versions);
    }

    @Test void enablingProductionUsesLatestPublishedInputsNotItsOlderAppliedVersionAndPreservesOwners() {
        var draft = prepareActivation();
        var globalDesk = mock(DeploymentMarketplaceInstallSummary.class);
        when(globalDesk.id()).thenReturn("install-desk"); when(globalDesk.pluginId()).thenReturn(DeskActivationService.PLUGIN);
        var action = mock(DeploymentMarketplaceInstallSummary.class); when(action.pluginId()).thenReturn("mkp-action-conversation-handover");
        when(installs.listInstallsForTrustedCaller(any())).thenReturn(List.of(globalDesk, action));
        var ownerA = user("owner-id"); var ownerB = user("owner-b"); var unlinked = user("unlinked"); var revoked = user("revoked");
        when(users.findByCustomerIdAndRoleIgnoreCaseAndStatusIgnoreCase("customer-a", "CUSTOMER_ADMIN", "ACTIVE"))
            .thenReturn(List.of(ownerA, ownerB, unlinked, revoked));
        when(identities.findByPlatformUserId("owner-b")).thenReturn(Optional.of(identity("owner-b", "subject-b")));
        var revokedLink = identity("revoked", "revoked-subject"); revokedLink.setRevokedAt(java.time.Instant.now());
        when(identities.findByPlatformUserId("revoked")).thenReturn(Optional.of(revokedLink));
        var result = service.enable("dep-a", new DeskActivationService.EnableRequest("production", "rel-prod-old", "enable-prod", false));
        assertThat(result.releaseId()).isEqualTo("rel-desk");
        var update = org.mockito.ArgumentCaptor.forClass(UpdateDeploymentMarketplaceInstallRequest.class);
        verify(installs).updateInstallForTrustedCallerWithoutDraftSync(any(), eq("install-desk"), update.capture());
        assertThat(update.getValue().config().path("bindingId").asText()).isEqualTo(DeskTargetConfigurationResolver.BINDING);
        assertThat(update.getValue().config().toString()).doesNotContain("desk-production.example");
        assertThat(update.getValue().secretRefs().path("providerApiKeySecretRef").asText()).isEqualTo(DeskTargetConfigurationResolver.PROVIDER_KEY);
        verify(installs, never()).createInstallForTrustedCallerWithoutDraftSync(any(), any());
        var body = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(desk).put(any(), eq("/api/desk/admin/v1/workspaces/customer-a"), body.capture());
        var owners = mapper.valueToTree(body.getValue()).path("owners");
        assertThat(owners).hasSize(2);
        assertThat(owners.findValuesAsText("subject")).containsExactly("subject-a", "subject-b");
        verify(deploymentService).publishDraft(draft.id());
        verify(deploymentService).applyVersion("dep-a", "ver-desk", null, "production", "artifact-prod", "enable-prod");
    }

    @Test void genuinelyUnpublishedEditsAreRejectedBeforeDeskRegistrationOrGlobalInstallChanges() {
        prepareActivation();
        var original = drafts.findById("draft-published").orElseThrow(); original.setShellConfigJson("{\"title\":\"different\"}");
        assertThatThrownBy(() -> service.enable("dep-a", new DeskActivationService.EnableRequest("production", "rel-prod-old", "enable-prod", false)))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unrelated draft edits");
        verify(desk, never()).put(any(), any(), any());
        verifyNoInteractions(installs, secrets, compiler);
    }

    @Test void firstEnablementPublishesOnlyManagedDeskSlotsAndUsesTheCompatibleHandoverAction() {
        prepareActivation();
        when(installs.listInstallsForTrustedCaller(any())).thenReturn(List.of());
        service.enable("dep-a", new DeskActivationService.EnableRequest("production", "rel-prod-old", "enable-prod", false));
        var inputs = org.mockito.ArgumentCaptor.forClass(CreateDeploymentMarketplaceInstallRequest.class);
        verify(installs, times(2)).createInstallForTrustedCallerWithoutDraftSync(any(), inputs.capture());
        var action = inputs.getAllValues().stream().filter(value -> "mkp-action-conversation-handover".equals(value.pluginId())).findFirst().orElseThrow();
        assertThat(action.pluginVersion()).isEqualTo("1.1.0");
        var channel = inputs.getAllValues().stream().filter(value -> DeskActivationService.PLUGIN.equals(value.pluginId())).findFirst().orElseThrow();
        assertThat(channel.config().path("bindingId").asText()).isEqualTo(DeskTargetConfigurationResolver.BINDING);
        assertThat(channel.config().path("providerBaseUrl").asText()).isEqualTo(DeskTargetConfigurationResolver.PROVIDER);
        assertThat(channel.secretRefs().path("providerApiKeySecretRef").asText()).isEqualTo(DeskTargetConfigurationResolver.PROVIDER_KEY);
        var registration = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(desk).put(any(), startsWith("/api/desk/admin/v1/bindings/"), registration.capture());
        var registered = mapper.valueToTree(registration.getValue());
        assertThat(registered.path("callbackUrl").asText()).isEqualTo("https://connector-prod.example/integrations/conversation-channel/events");
        assertThat(registered.path("targetProfileId").asText()).isEqualTo("production");
        assertThat(registered.path("reviewBridgeUrl").asText()).startsWith("https://console.example/api/desk/bridge/v1/bindings/").endsWith("/reviews");
    }

    private DeploymentDraftResponse prepareActivation() {
        authenticate(new PlatformPrincipal("owner@example.com", PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE", "owner-id"));
        var deployment = deployment();
        var target = new DeploymentTargetProfileEntity(); target.setId("production"); target.setEnvironmentName("production"); target.setActive(true);
        var oldVersion = new DeploymentVersionEntity(); oldVersion.setId("ver-prod-old"); oldVersion.setBehaviorConfigJson("{}");
        var release = release("rel-prod-old", "ver-prod-old"); release.setSourceArtifactId("artifact-prod");
        when(targets.resolve("dep-a", "production", "rel-prod-old", true)).thenReturn(
            new DeploymentTargetContextService.Context(deployment, target, release, oldVersion, "https://runtime-prod.example", "https://connector-prod.example"));
        when(desk.connection("production")).thenReturn(new DeskServiceClient.Connection("https://desk-production.example", "service-key"));
        var savedBinding = new java.util.concurrent.atomic.AtomicReference<DeskBindingEntity>();
        when(bindings.findByDeploymentIdAndTargetProfileId("dep-a", "production")).thenAnswer(invocation -> Optional.ofNullable(savedBinding.get()));
        when(bindings.saveAndFlush(any())).thenAnswer(invocation -> { savedBinding.set(invocation.getArgument(0)); return savedBinding.get(); });
        when(identities.findByPlatformUserId("owner-id")).thenReturn(Optional.of(identity("owner-id", "subject-a")));
        when(users.findByCustomerIdAndRoleIgnoreCaseAndStatusIgnoreCase("customer-a", "CUSTOMER_ADMIN", "ACTIVE"))
            .thenReturn(List.of(user("owner-id")));
        var empty = mapper.createObjectNode();
        var behavior = DeskTargetConfigurationResolverTest.managedBehavior(mapper);
        var draft = new DeploymentDraftResponse("draft-current", "dep-a", 3, "DRAFT", empty, empty, empty, empty, empty,
            empty, empty, empty, empty, null, null, "AI_ENTITY_CONFIG_V0_4", behavior);
        when(deploymentService.getActiveDraftForDeployment("dep-a")).thenReturn(draft);
        var latest = new DeploymentVersionEntity(); latest.setId("ver-stage-latest"); latest.setStatus("PUBLISHED"); latest.setSourceDraftId("draft-published");
        // The generated prompt artifact intentionally differs from editable source inputs.
        latest.setPromptConfigJson("{\"compiled\":true}");
        when(versions.findByDeploymentIdOrderByPublishedAtDesc("dep-a")).thenReturn(List.of(latest, oldVersion));
        var source = new DeploymentDraftEntity(); source.setId("draft-published"); source.setDeploymentId("dep-a"); source.setStatus("PUBLISHED");
        source.setActionsConfigJson("{}"); source.setEntityConfigJson("{}"); source.setRoutingConfigJson("{}"); source.setProviderConfigJson("{}");
        source.setSecurityConfigJson("{}"); source.setPromptConfigJson("{}"); source.setKnowledgeSourceConfigJson("{}");
        source.setShellConfigJson("{}"); source.setMarketplaceDatasetConfigJson("{}"); source.setBehaviorConfigJson(behavior.toString());
        when(drafts.findById("draft-published")).thenReturn(Optional.of(source));
        when(secrets.resolveSecret(anyString())).thenReturn("private-binding-key");
        when(compiler.syncDeploymentDraftForTrustedCaller("dep-a")).thenReturn(draft);
        var published = mock(DeploymentVersionSummary.class); when(published.id()).thenReturn("ver-desk");
        when(deploymentService.publishDraft(draft.id())).thenReturn(published);
        var applied = mock(DeploymentReleaseSummary.class); when(applied.id()).thenReturn("rel-desk");
        when(deploymentService.applyVersion("dep-a", "ver-desk", null, "production", "artifact-prod", "enable-prod")).thenReturn(applied);
        return draft;
    }

    private PlatformUserEntity user(String id) {
        var user = new PlatformUserEntity(); user.setId(id); user.setEmail(id + "@example.com"); user.setDisplayName(id);
        user.setCustomerId("customer-a"); user.setRole("CUSTOMER_ADMIN"); user.setStatus("ACTIVE"); return user;
    }

    private PlatformCustomerIdentityLinkEntity identity(String id, String subject) {
        var identity = new PlatformCustomerIdentityLinkEntity(); identity.setPlatformUserId(id);
        identity.setIssuer("https://supabase.example/auth/v1"); identity.setSubject(subject); return identity;
    }

    private void authenticate(PlatformPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    private DeploymentEntity deployment() {
        var value = new DeploymentEntity(); value.setId("dep-a"); value.setName("Northfield"); value.setCustomerId("customer-a"); value.setTenantId("tenant-a"); return value;
    }
    private DeskBindingEntity binding() {
        var value = new DeskBindingEntity(); value.setId("desk-a"); value.setDeploymentId("dep-a"); value.setCustomerId("customer-a");
        value.setTenantId("tenant-a"); value.setTargetProfileId("target-a"); value.setStatus("ENABLED"); value.setBridgeKeyRef("bridge-key-ref");
        value.setReleaseId("rel-original"); return value;
    }
    private DeploymentReleaseEntity release(String id, String version) {
        var value = new DeploymentReleaseEntity(); value.setId(id); value.setDeploymentId("dep-a"); value.setTargetProfileId("target-a");
        value.setDeploymentVersionId(version); value.setStatus("APPLIED_VERIFIED"); value.setProvisioningStatus("ACTIVE"); value.setVerificationStatus("PASSED"); return value;
    }
}
