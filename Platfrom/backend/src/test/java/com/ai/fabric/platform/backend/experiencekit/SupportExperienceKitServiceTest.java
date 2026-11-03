package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class SupportExperienceKitServiceTest {
    private static final String SUPPORT = "ai-support-experience";
    private ExperienceKitTestFixture f;

    @BeforeEach void setupSupportCatalogue() {
        f = new ExperienceKitTestFixture();
        var definition = f.definition(SUPPORT, "support", List.of("mkp-template-ai-support-workspace"));
        when(f.definitions.findById(SUPPORT)).thenReturn(Optional.of(definition));
        when(f.definitions.findAllByOrderBySortOrderAsc()).thenReturn(List.of(definition));
        f.version("support-version", "mkp-template-ai-support-workspace");
        var profile = f.profile("support");
        var config = (ObjectNode) profile.interaction().path("configuration");
        config.put("schemaVersion", "loomai-support-experience-config-v1");
        config.putObject("support").put("requestLabel", "My requests").put("teamVisible", false).putArray("topics");
        config.with("capabilities").put("tickets", false).put("attachments", true);
        when(f.catalogue.offer(OFFER)).thenReturn(f.offer(OFFER, "support-version", profile, TARGET, TargetIntent.CUSTOMER_STAGING));
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void setupRecordsIndependentPreferencesAndCreatesOnlyDrafts(boolean tickets, boolean human) {
        var result = f.service.create(request(tickets, human, tickets ? "desk" : null));
        assertThat(result.ticketingEnabled()).isEqualTo(tickets);
        assertThat(result.humanSupportEnabled()).isEqualTo(human);
        assertThat(result.ticketProvider()).isEqualTo(tickets ? "desk" : null);
        assertThat(result.workspace().configuration().at("/capabilities/tickets").asBoolean()).isEqualTo(tickets);
        assertThat(result.workspace().configuration().at("/capabilities/handover").asBoolean()).isEqualTo(human);
        assertThat(result.workspace().configuration().at("/support/spaceId").isMissingNode()).isTrue();
        assertThat(result.workspace().status()).isEqualTo("DRAFT");
        var validator = new AIWorkspaceConfigurationValidator(new PlatformAIWorkspaceProperties(Path.of("."),
            false, 120, Duration.ofSeconds(60), null, null, "apps.example.test", "https://workspace.example.test"));
        validator.validateSupportConfiguration(result.workspace().configuration());
        verify(f.bootstrap).bootstrap(eq(OFFER), any());
        verify(f.workspaces, never()).activate(anyString(), anyString());
    }

    @Test void catalogueProvidesDeclaredOptionsNotEffectiveBindingAuthority() {
        var offer = f.catalogue.offer(OFFER);
        when(f.catalogue.catalogue()).thenReturn(List.of(offer));
        var entry = f.service.customerCatalogue().getFirst();
        assertThat(entry.offers()).hasSize(1);
        assertThat(entry.ticketProviders()).extracting(ExperienceKitContracts.TicketProviderOption::code).containsExactly("desk");
        assertThat(entry.ticketProviders().getFirst().declaredOperations()).contains("createRequest", "reopenRequest");
        verifyNoInteractions(f.bootstrap, f.consumers, f.workspaces);
    }

    @ParameterizedTest @CsvSource({"true,", "true,unregistered", "false,unregistered"})
    void invalidProviderSelectionFailsBeforeAnyBootstrap(boolean enabled, String provider) {
        assertThatThrownBy(() -> f.service.create(request(enabled, false, provider))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(f.bootstrap, f.consumers, f.workspaces);
    }

    @Test void changingTicketingPreferenceChangesReplayFingerprint() {
        f.service.create(request(false, false, null));
        clearInvocations(f.bootstrap, f.consumers, f.workspaces);
        assertThatThrownBy(() -> f.service.create(request(true, false, "desk"))).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("operation key");
        verifyNoInteractions(f.bootstrap, f.consumers, f.workspaces);
    }

    @Test void draftUpdatePreservesExplicitProviderReferencesAndTicketOnlyMode() {
        var result = f.service.create(request(true, false, "desk"));
        clearInvocations(f.bootstrap);
        when(f.kits.findOwnedForUpdate(CUSTOMER, result.id())).thenReturn(Optional.of(f.saved));
        var config = (ObjectNode) result.workspace().configuration();
        config.with("support").put("spaceId", "owned-space").put("ticketProviderBindingId", "owned-binding");
        var update = new ExperienceKitContracts.UpdateRequest("Support", "Support AI", "#18a999", "standalone",
            result.workspace().appSlug(), null, false, 0, 4L, null, true, "desk");
        f.service.update(result.id(), update);
        var captured = ArgumentCaptor.forClass(com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest.class);
        verify(f.workspaces).update(eq(CUSTOMER), eq(result.installationId()), captured.capture());
        assertThat(captured.getValue().configuration().at("/support/spaceId").asText()).isEqualTo("owned-space");
        assertThat(captured.getValue().configuration().at("/capabilities/tickets").asBoolean()).isTrue();
        assertThat(captured.getValue().configuration().at("/capabilities/handover").asBoolean()).isFalse();
        verifyNoMoreInteractions(f.bootstrap);
    }

    private ExperienceKitContracts.CreateRequest request(boolean tickets, boolean human, String provider) {
        var base = f.request("standalone", human);
        return new ExperienceKitContracts.CreateRequest(SUPPORT, "Acme Support", base.offerId(), base.targetProfileId(),
            base.workspaceProfileId(), base.aiStateStorageMode(), "Acme Support AI", base.brandColor(), base.deliveryMode(),
            "acme-support", null, human, base.operationKey(), tickets, provider);
    }
}
