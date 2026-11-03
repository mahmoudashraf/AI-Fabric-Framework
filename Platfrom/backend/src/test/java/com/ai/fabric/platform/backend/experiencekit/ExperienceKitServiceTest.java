package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAuthenticationProfileRegistry;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceExperiencePackRegistry;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitTestFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class ExperienceKitServiceTest {
    private ExperienceKitTestFixture f;

    @BeforeEach void setUp() { f = new ExperienceKitTestFixture(); }

    @Test void publicCatalogueReturnsProductDefinitionsWithoutCustomerAccessOrResources() {
        var result = f.service.publicCatalogue();
        assertThat(result).extracting(ExperienceKitContracts.Definition::code).containsExactly(GENERIC, DEALERSHIP);
        assertThat(result).extracting(ExperienceKitContracts.Definition::experiencePackCode)
            .containsExactly("generic-workspace", "dealership");
        verifyNoInteractions(f.access, f.catalogue, f.bootstrap, f.staging, f.consumers, f.workspaces, f.kits, f.audit);
    }

    @Test void customerCatalogueMatchesExactTemplatePluginIdsNotOfferLabelsOrPrefixes() {
        f.version("generic-version", "mkp-template-document-knowledge-assistant");
        f.version("external-version", "mkp-template-dealership-external-systems-v1");
        f.version("desk-version", "mkp-template-dealership-external-systems-managed-inbox-v1");
        f.version("near-version", TEMPLATE + "-unapproved");
        var generic = f.offer("generic", "generic-version", f.profile("generic-workspace"), TARGET, TargetIntent.CUSTOMER_STAGING);
        var simulator = f.offer("simulator", "template-version", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING);
        var external = f.offer("external", "external-version", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING);
        var desk = f.offer("desk", "desk-version", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING);
        var near = f.offer("similar-name", "near-version", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING);
        var missing = f.offer("missing-version", "not-in-catalogue", f.profile, TARGET, TargetIntent.CUSTOMER_STAGING);
        when(f.catalogue.catalogue()).thenReturn(List.of(generic, simulator, external, desk, near, missing));

        var result = f.service.customerCatalogue();

        assertThat(result.get(0).offers()).containsExactly(generic);
        assertThat(result.get(1).offers()).containsExactly(simulator, external, desk);
        verify(f.access).requireCustomerActor();
        verifyNoInteractions(f.bootstrap, f.staging, f.consumers, f.workspaces, f.kits, f.audit);
    }

    @Test void foreignOrMissingKitIsNotFoundWithoutUnscopedLookupOrAppRead() {
        when(f.kits.findByCustomerIdAndId(CUSTOMER, "another-customers-kit")).thenReturn(Optional.empty());

        assertStatus(NOT_FOUND, () -> f.service.get("another-customers-kit"));

        verify(f.kits).findByCustomerIdAndId(CUSTOMER, "another-customers-kit");
        verify(f.kits, never()).findById(anyString());
        verifyNoInteractions(f.installations, f.workspaces);
    }

    @Test void identicalOperationReplayReturnsTheSameKitWithoutCreatingMoreResources() {
        var request = f.request("standalone", false);
        var first = f.service.create(request);
        var second = f.service.create(request);

        assertThat(second).isEqualTo(first);
        assertThat(f.saved.getRequestFingerprint()).isEqualTo(f.codec.hash(java.util.Map.of("request", request, "ownerUserId", OWNER)));
        verify(f.entityManager, times(2)).find(PlatformCustomerEntity.class, CUSTOMER, LockModeType.PESSIMISTIC_WRITE);
        verify(f.bootstrap).bootstrap(eq(OFFER), any());
        verify(f.consumers).createConsumer(eq(CUSTOMER), any());
        verify(f.staging).configure(eq(OFFER), eq("kit-deployment"), any());
        verify(f.workspaces).create(eq(CUSTOMER), any(), anyString());
        verify(f.kits).saveAndFlush(any());
        verify(f.audit, times(1)).record(anyString(), anyString(), anyString(), anyMap());
    }

    @ParameterizedTest @ValueSource(strings = {"payload", "owner"})
    void reusedOperationKeyWithChangedPayloadOrOwnerConflictsBeforeResourceCreation(String change) {
        var request = f.request("standalone", false);
        f.service.create(request);
        clearInvocations(f.bootstrap, f.consumers, f.staging, f.workspaces, f.kits, f.audit);
        if (change.equals("owner")) {
            when(f.access.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor(CUSTOMER, "other-owner"));
        } else {
            request = f.request("standalone", true);
        }
        var replay = request;

        assertStatus(CONFLICT, () -> f.service.create(replay));

        verifyNoInteractions(f.bootstrap, f.consumers, f.staging, f.workspaces, f.audit);
        verify(f.kits, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-template", "unknown-profile", "wrong-pack", "production", "other-target"})
    void incompatibleSelectionsAreDeniedBeforeConfigurationBootstrap(String invalid) {
        var profile = f.profile;
        String target = TARGET;
        var intent = TargetIntent.CUSTOMER_STAGING;
        switch (invalid) {
            case "wrong-template" -> f.version("template-version", "mkp-template-document-knowledge-assistant");
            case "unknown-profile" -> profile = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.WorkspaceProfile(
                "different-profile", profile.label(), profile.interaction(), profile.theme(), profile.authentication());
            case "wrong-pack" -> profile = f.profile("generic-workspace");
            case "production" -> intent = TargetIntent.CUSTOMER_PRODUCTION;
            case "other-target" -> target = "another-staging-target";
            default -> throw new AssertionError(invalid);
        }
        when(f.catalogue.offer(OFFER)).thenReturn(f.offer(OFFER, "template-version", profile, target, intent));

        assertStatus(BAD_REQUEST, () -> f.service.create(f.request("standalone", false)));

        verifyNoInteractions(f.bootstrap, f.staging, f.consumers, f.workspaces, f.audit);
        verify(f.kits, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void creationUsesCanonicalConfigurationOnlyBootstrapConsumerStagingAndDraftWorkspace(boolean human) {
        var result = f.service.create(f.request("standalone", human));
        var order = inOrder(f.bootstrap, f.consumers, f.staging, f.workspaces, f.kits, f.audit);
        order.verify(f.bootstrap).bootstrap(OFFER, new CustomerDeploymentBootstrapContracts.Request(
            TARGET, "Northfield experience", 7, result.id(), "CUSTOMER_POSTGRES"));
        order.verify(f.consumers).createConsumer(CUSTOMER, new CreatePlatformConsumerRequest(result.id(),
            "Northfield AI", "AI experience kit configuration", null, null));
        order.verify(f.staging).configure(OFFER, "kit-deployment", new CustomerStagingConsumerConfigurationContracts.Request(
            "kit-consumer", 3, TARGET, result.id() + "-bind", List.of()));
        var app = ArgumentCaptor.forClass(CreateAIWorkspaceInstallationRequest.class);
        order.verify(f.workspaces).create(eq(CUSTOMER), app.capture(), eq(result.id() + "-app"));
        order.verify(f.kits).saveAndFlush(f.saved);
        order.verify(f.audit).record(eq("CUSTOMER_EXPERIENCE_KIT_CREATED"), eq("EXPERIENCE_KIT"), eq(result.id()),
            argThat(value -> "CONFIGURATION_ONLY".equals(value.get("creationStatus"))));

        assertThat(result.deploymentId()).isEqualTo("kit-deployment");
        assertThat(result.consumerId()).isEqualTo("kit-consumer");
        assertThat(result.workspace().status()).isEqualTo("DRAFT");
        assertThat(result.workspace().releaseId()).isNull();
        assertThat(result.workspace().ready()).isFalse();
        assertThat(result.humanSupportEnabled()).isEqualTo(human);
        assertThat(app.getValue().configuration().path("dealer").path("assistantLabel").asText()).isEqualTo("Northfield AI");
        assertThat(app.getValue().configuration().has("assistantLabel")).isFalse();
        assertThat(app.getValue().configuration().path("capabilities"))
            .isEqualTo(f.profile.interaction().path("configuration").path("capabilities"));
        assertThat(app.getValue().configuration().path("capabilities").has("handover")).isFalse();
        assertThat(app.getValue().configuration().path("toolGroups")).isEqualTo(f.profile.interaction().path("configuration").path("toolGroups"));
        assertThat(app.getValue().configuration().path("theme").path("darkMode").asBoolean()).isTrue();
        assertThat(app.getValue().configuration().path("theme").path("primaryColor").asText()).isEqualTo("#2563eb");
        assertThat(app.getValue().allowedOrigins()).isEmpty();
        assertThat(app.getValue().deliveryMode()).isEqualTo("standalone");
        assertThat(app.getValue().authConfiguration()).isEqualTo(f.profile.authentication().path("configuration"));
        assertThat(f.profile.interaction().path("configuration").path("capabilities").has("handover")).isFalse();
        // Desk is a desired kit preference, not a dealership capability or provisioned service.
        verify(f.workspaces).get(CUSTOMER, result.installationId());
        verifyNoMoreInteractions(f.bootstrap, f.consumers, f.staging, f.workspaces, f.audit);
    }

    @ParameterizedTest @CsvSource({"dealership,false", "dealership,true", "generic-workspace,false", "generic-workspace,true"})
    void capturedCreateConfigurationPassesRealPackConnectionAndAuthenticationValidators(String pack, boolean human) {
        var request = f.request("standalone", human);
        if (pack.equals("generic-workspace")) {
            f.version("template-version", "mkp-template-document-knowledge-assistant");
            when(f.catalogue.offer(OFFER)).thenReturn(f.offer(OFFER, "template-version", f.profile(pack), TARGET,
                TargetIntent.CUSTOMER_STAGING));
            request = new ExperienceKitContracts.CreateRequest(GENERIC, request.name(), request.offerId(),
                request.targetProfileId(), request.workspaceProfileId(), request.aiStateStorageMode(), request.workspaceName(),
                request.brandColor(), request.deliveryMode(), request.appSlug(), request.websiteOrigin(), human, request.operationKey(), false, null);
        }
        var result = f.service.create(request);
        var captured = ArgumentCaptor.forClass(CreateAIWorkspaceInstallationRequest.class);
        verify(f.workspaces).create(eq(CUSTOMER), captured.capture(), anyString());
        var input = captured.getValue();
        var properties = new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60),
            null, null, "apps.loomai.test", "https://workspace.loomai.test");
        var validator = new AIWorkspaceConfigurationValidator(properties);
        var packs = new AIWorkspaceExperiencePackRegistry(mock(AIWorkspaceAssetCatalogService.class), validator);

        assertThatCode(() -> packs.validateConfiguration(input.experiencePackCode(), input.experiencePackVersion(), input.configuration()))
            .doesNotThrowAnyException();
        assertThatCode(() -> new AIWorkspaceConnectionProfileRegistry(properties).resolve(input.connectionProfileCode(),
            input.connectionProfileVersion(), AIWorkspaceConnectionMode.parse(input.connectionMode()), input.connectionConfiguration()))
            .doesNotThrowAnyException();
        assertThatCode(() -> new AIWorkspaceAuthenticationProfileRegistry(f.mapper).resolve(input.authProfileCode(),
            input.authProfileVersion(), AIWorkspaceInteractionPolicy.parse(input.interactionPolicy()), input.authConfiguration()))
            .doesNotThrowAnyException();
        assertThat(input.configuration().path(pack.equals("dealership") ? "dealer" : "app").path("assistantLabel").asText())
            .isEqualTo("Northfield AI");
        assertThat(input.configuration().has("assistantLabel")).isFalse();
        if (pack.equals("dealership")) {
            assertThat(input.configuration().path("capabilities").has("handover")).isFalse();
        } else {
            assertThat(input.configuration().path("capabilities").path("handover").asBoolean()).isEqualTo(human);
            assertThat(input.configuration().path("capabilities").path("currentPage").asBoolean()).isTrue();
        }
        assertThat(result.humanSupportEnabled()).isEqualTo(human);
        verify(f.workspaces, never()).activate(anyString(), anyString());
    }

    @Test void bothCreatesTwoSeparateCanonicalAppsWithDistinctDeliveryAndSharedOwnedConsumer() {
        var result = f.service.create(f.request("both", true));
        var inputs = ArgumentCaptor.forClass(CreateAIWorkspaceInstallationRequest.class);
        verify(f.workspaces, times(2)).create(eq(CUSTOMER), inputs.capture(), anyString());
        var standalone = inputs.getAllValues().get(0);
        var embedded = inputs.getAllValues().get(1);

        assertThat(standalone.deliveryMode()).isEqualTo("standalone");
        assertThat(standalone.appSlug()).isEqualTo("northfield-ai");
        assertThat(standalone.allowedOrigins()).isEmpty();
        assertThat(embedded.deliveryMode()).isEqualTo("embedded");
        assertThat(embedded.appSlug()).isNull();
        assertThat(embedded.allowedOrigins()).containsExactly("https://dealer.example.test");
        assertThat(embedded.consumerId()).isEqualTo(standalone.consumerId());
        assertThat(embedded.configuration()).isEqualTo(standalone.configuration()).isNotSameAs(standalone.configuration());
        assertThat(result.installationId()).isNotEqualTo(result.embeddedInstallationId());
        assertThat(f.saved.getInstallationEntityId()).isEqualTo(result.workspace().id());
        assertThat(f.saved.getEmbeddedInstallationEntityId()).isEqualTo(result.embeddedWorkspace().id());
        verify(f.workspaces).create(CUSTOMER, standalone, result.id() + "-app");
        verify(f.workspaces).create(CUSTOMER, embedded, result.id() + "-embed");
        verify(f.bootstrap, times(1)).bootstrap(eq(OFFER), any());
    }

    @ParameterizedTest @ValueSource(strings = {"kit", "primary", "embedded"})
    void staleKitOrCanonicalWorkspaceRevisionCannotMutateAnyResource(String stale) {
        var result = f.service.create(f.request("both", false));
        when(f.kits.findOwnedForUpdate(CUSTOMER, result.id())).thenReturn(Optional.of(f.saved));
        clearInvocations(f.workspaces, f.installations, f.kits, f.audit);
        var request = f.update("both", true, stale.equals("kit") ? 1 : 0);
        var changed = new ExperienceKitContracts.UpdateRequest(request.name(), request.workspaceName(), request.brandColor(),
            request.deliveryMode(), request.appSlug(), request.websiteOrigin(), request.humanSupportEnabled(),
            request.expectedRevision(), stale.equals("primary") ? 5L : 4L, stale.equals("embedded") ? 5L : 4L, false, null);

        assertStatus(CONFLICT, () -> f.service.update(result.id(), changed));

        if (stale.equals("kit")) verifyNoInteractions(f.workspaces, f.installations);
        verify(f.workspaces, never()).update(anyString(), anyString(), any());
        verify(f.workspaces, never()).create(anyString(), any(), anyString());
        verify(f.workspaces, never()).disable(anyString(), anyString());
        verifyNoInteractions(f.audit);
        verify(f.kits, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"primary-appearance", "embedded-appearance", "primary-metadata", "embedded-metadata"})
    void liveAppsRejectAppearanceChangesButPermitDesiredMetadataWithoutMutatingEitherApp(String scenario) {
        var result = f.service.create(f.request("both", false));
        when(f.kits.findOwnedForUpdate(CUSTOMER, result.id())).thenReturn(Optional.of(f.saved));
        boolean primary = scenario.startsWith("primary");
        boolean metadataOnly = scenario.endsWith("metadata");
        var app = primary ? result.workspace() : result.embeddedWorkspace();
        var request = ArgumentCaptor.forClass(CreateAIWorkspaceInstallationRequest.class);
        verify(f.workspaces, times(2)).create(eq(CUSTOMER), request.capture(), anyString());
        f.remember(f.workspace(app.id(), app.installationId(), "ACTIVE",
            request.getAllValues().get(primary ? 0 : 1)));
        clearInvocations(f.workspaces, f.kits, f.audit);

        if (metadataOnly) {
            var metadata = new ExperienceKitContracts.UpdateRequest("Renamed experience", result.workspace().displayName(),
                "#2563eb", "both", result.workspace().appSlug(), "https://dealer.example.test", true, 0, 4L, 4L, false, null);
            var updated = f.service.update(result.id(), metadata);
            assertThat(updated.name()).isEqualTo("Renamed experience");
            assertThat(updated.humanSupportEnabled()).isTrue();
            assertThat(updated.workspace().configuration().path("capabilities").has("handover")).isFalse();
            assertThat(updated.embeddedWorkspace().configuration().path("capabilities").has("handover")).isFalse();
            verify(f.kits).saveAndFlush(f.saved);
            verify(f.audit).record(eq("CUSTOMER_EXPERIENCE_KIT_UPDATED"), eq("EXPERIENCE_KIT"), eq(result.id()), anyMap());
        } else {
            assertStatus(CONFLICT, () -> f.service.update(result.id(), f.update("both", true, 0)));
            verify(f.kits, never()).saveAndFlush(any());
            verifyNoInteractions(f.audit);
        }

        verify(f.workspaces, never()).update(anyString(), anyString(), any());
        verify(f.workspaces, never()).create(anyString(), any(), anyString());
        verify(f.workspaces, never()).disable(anyString(), anyString());
        verify(f.workspaces, never()).activate(anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"single-origin", "preserve-multiple", "replace-multiple"})
    void draftUpdatePreservesInjectedConfigurationAndCannotReplaceAMultipleOriginSet(String scenario) {
        var result = f.service.create(f.request("embedded", false));
        when(f.kits.findOwnedForUpdate(CUSTOMER, result.id())).thenReturn(Optional.of(f.saved));
        boolean multiple = !scenario.equals("single-origin");
        var originalOrigins = List.of("https://dealer.example.test", "https://second.example.test");
        if (multiple) {
            var input = ArgumentCaptor.forClass(CreateAIWorkspaceInstallationRequest.class);
            verify(f.workspaces).create(eq(CUSTOMER), input.capture(), anyString());
            var original = input.getValue();
            var multiOrigin = new CreateAIWorkspaceInstallationRequest(original.consumerId(), original.displayName(),
                original.experiencePackCode(), original.experiencePackVersion(), original.connectionMode(),
                original.connectionProfileCode(), original.connectionProfileVersion(), original.connectionConfiguration(),
                originalOrigins, original.configuration(), original.deliveryMode(), original.appSlug(), original.customDomains(),
                original.interactionPolicy(), original.authProfileCode(), original.authProfileVersion(), original.authConfiguration());
            f.remember(f.workspace(result.workspace().id(), result.installationId(), "DRAFT", multiOrigin));
        }
        clearInvocations(f.workspaces, f.bootstrap, f.consumers, f.staging, f.audit);
        var request = f.update("embedded", true, 0);
        if (scenario.equals("preserve-multiple")) request = new ExperienceKitContracts.UpdateRequest(request.name(),
            request.workspaceName(), request.brandColor(), request.deliveryMode(), request.appSlug(), originalOrigins.getFirst(),
            true, 0, 4L, null, false, null);
        var update = request;

        if (scenario.equals("replace-multiple")) {
            assertStatus(CONFLICT, () -> f.service.update(result.id(), update));
            verify(f.workspaces, never()).update(anyString(), anyString(), any());
            verifyNoInteractions(f.bootstrap, f.consumers, f.staging, f.audit);
            return;
        }

        var updated = f.service.update(result.id(), update);
        var input = ArgumentCaptor.forClass(UpdateAIWorkspaceInstallationRequest.class);
        verify(f.workspaces).update(eq(CUSTOMER), eq(result.installationId()), input.capture());

        assertThat(updated.name()).isEqualTo("Updated experience");
        assertThat(updated.humanSupportEnabled()).isTrue();
        assertThat(input.getValue().rowVersion()).isEqualTo(result.workspace().rowVersion());
        assertThat(input.getValue().configuration().path("dealer").path("assistantLabel").asText()).isEqualTo("Updated AI");
        assertThat(input.getValue().configuration().has("assistantLabel")).isFalse();
        assertThat(input.getValue().configuration().path("capabilities"))
            .isEqualTo(result.workspace().configuration().path("capabilities"));
        assertThat(input.getValue().configuration().path("capabilities").has("handover")).isFalse();
        assertThat(input.getValue().configuration().path("toolGroups")).isEqualTo(result.workspace().configuration().path("toolGroups"));
        assertThat(input.getValue().configuration().path("presentation")).isEqualTo(result.workspace().configuration().path("presentation"));
        assertThat(input.getValue().configuration().path("presentation").path("detailBasePath").asText()).isEqualTo("/vehicles");
        assertThat(input.getValue().authConfiguration()).isEqualTo(result.workspace().authConfiguration());
        assertThat(input.getValue().connectionConfiguration()).isEqualTo(result.workspace().connectionConfiguration());
        assertThat(input.getValue().allowedOrigins()).containsExactlyElementsOf(multiple ? originalOrigins : List.of(update.websiteOrigin()));
        assertThat(result.workspace().configuration().path("capabilities").has("handover")).isFalse();
        verifyNoInteractions(f.bootstrap, f.consumers, f.staging);
        verify(f.workspaces, never()).create(anyString(), any(), anyString());
        verify(f.workspaces, never()).activate(anyString(), anyString());
    }

    private void assertStatus(org.springframework.http.HttpStatus status, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
