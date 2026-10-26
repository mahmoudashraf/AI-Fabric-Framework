package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskTargetConfigurationResolverTest {
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final DeskTargetConfigurationResolver resolver = new DeskTargetConfigurationResolver(bindings, targets, desk);
    final ObjectMapper mapper = new ObjectMapper();

    public static ObjectNode managedBehavior(ObjectMapper mapper) {
        var behavior = mapper.createObjectNode().put("schemaVersion", "loomai-deployment-behavior-v1")
            .put("type", "CONVERSATIONAL").put("contractVersion", 1);
        behavior.putArray("executionExtensions").add("CONVERSATION_HANDOVER");
        var handover = behavior.putObject("conversationHandover").put("enabled", true)
            .put("marketplacePluginId", DeskActivationService.PLUGIN)
            .put("channelBindingId", DeskTargetConfigurationResolver.BINDING);
        handover.putObject("intake").put("anonymousAllowed", true).putArray("fields");
        handover.putObject("channel").put("bindingId", DeskTargetConfigurationResolver.BINDING)
            .put("providerBaseUrl", DeskTargetConfigurationResolver.PROVIDER)
            .put("operatorConsoleUrl", DeskTargetConfigurationResolver.CONSOLE)
            .put("providerApiKeySecretRef", DeskTargetConfigurationResolver.PROVIDER_KEY)
            .put("runtimeCallbackSigningSecretRef", DeskTargetConfigurationResolver.RUNTIME_CALLBACK_KEY)
            .put("providerWebhookSigningSecretRef", DeskTargetConfigurationResolver.PROVIDER_CALLBACK_KEY);
        return behavior;
    }

    @Test void sameImmutableVersionResolvesIndependentStagingAndProductionBindings() {
        var stage = register("staging"); var prod = register("production");
        JsonNode published = managedBehavior(mapper);
        String original = published.toString();
        var stageResolved = resolver.resolve(deployment(), version(), "staging", published);
        var prodResolved = resolver.resolve(deployment(), version(), "production", published);
        assertTarget(stageResolved, stage, "https://desk-staging.example");
        assertTarget(prodResolved, prod, "https://desk-production.example");
        assertThat(published.toString()).isEqualTo(original);
        assertThat(prodResolved.toString()).doesNotContain(stage.getId(), stage.getProviderKeyRef(), "https://desk-staging.example");
    }

    @Test void firstProductionPreviewProjectsOnlyItsOwnRefsWithoutCreatingARegistrationOrUsingStaging() {
        var staging = register("staging");
        var profile = new DeploymentTargetProfileEntity(); profile.setId("production"); profile.setActive(true); profile.setEnvironmentName("production");
        when(targets.findById("production")).thenReturn(Optional.of(profile));
        when(desk.connection("production")).thenReturn(new DeskServiceClient.Connection("https://desk-production.example", "service-key"));
        var expected = DeskBindingProvisioner.projection(deployment(), "production");
        var resolved = resolver.resolve(deployment(), version(), "production", managedBehavior(mapper));
        assertTarget(resolved, expected, "https://desk-production.example");
        assertThat(resolved.toString()).doesNotContain(staging.getId(), staging.getProviderKeyRef());
        verify(bindings, never()).findByDeploymentIdAndTargetProfileId("dep-a", "staging");
        verify(bindings, never()).save(any()); verify(bindings, never()).saveAndFlush(any());
        verify(desk, never()).put(any(), anyString(), any());
    }

    @Test void absentDeskLeavesExistingTemplatesAndTheirConfigurationUnchanged() {
        JsonNode ordinary = mapper.createObjectNode().put("type", "CONVERSATIONAL");
        assertThat(resolver.resolve(deployment(), version(), null, ordinary)).isSameAs(ordinary);
        verifyNoInteractions(bindings, targets, desk);
    }

    @Test void publishedTargetSpecificDeskValuesCannotBePromoted() {
        var behavior = managedBehavior(mapper);
        ((ObjectNode) behavior.path("conversationHandover")).put("channelBindingId", "desk-staging");
        assertThatThrownBy(() -> resolver.resolve(deployment(), version(), "production", behavior))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("target-independent");
        verifyNoInteractions(bindings, targets, desk);
    }

    @Test void ownershipMismatchDoesNotExposeTargetCredentials() {
        var binding = register("production");
        binding.setTenantId("other-tenant");
        assertThatThrownBy(() -> resolver.resolve(deployment(), version(), "production", managedBehavior(mapper)))
            .isInstanceOf(ResponseStatusException.class);
        verify(desk, never()).connection(anyString());
    }

    @Test void missingTargetAndMismatchedVersionFailBeforeResolvingAnyBinding() {
        assertThatThrownBy(() -> resolver.resolve(deployment(), version(), null, managedBehavior(mapper)))
            .isInstanceOf(ResponseStatusException.class);
        var otherVersion = version(); otherVersion.setDeploymentId("dep-other");
        assertThatThrownBy(() -> resolver.resolve(deployment(), otherVersion, "production", managedBehavior(mapper)))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(bindings);
    }

    @Test void preparingBindingIsUsableOnlyForItsExactTarget() {
        var binding = register("production"); binding.setStatus("PREPARING");
        assertTarget(resolver.resolve(deployment(), version(), "production", managedBehavior(mapper)), binding, "https://desk-production.example");
    }

    private DeskBindingEntity register(String target) {
        var b = new DeskBindingEntity(); b.setId("desk-" + target); b.setCustomerId("customer-a"); b.setTenantId("tenant-a");
        b.setDeploymentId("dep-a"); b.setTargetProfileId(target); b.setStatus("ENABLED");
        b.setProviderKeyRef("PROVIDER_" + target.toUpperCase()); b.setCallbackKeyRef("CALLBACK_" + target.toUpperCase());
        b.setRuntimeCallbackKeyRef("RUNTIME_CALLBACK_" + target.toUpperCase());
        when(bindings.findByDeploymentIdAndTargetProfileId("dep-a", target)).thenReturn(Optional.of(b));
        var profile = new DeploymentTargetProfileEntity(); profile.setId(target); profile.setActive(true); profile.setEnvironmentName(target);
        when(targets.findById(target)).thenReturn(Optional.of(profile));
        when(desk.connection(target)).thenReturn(new DeskServiceClient.Connection("https://desk-" + target + ".example", "service-key"));
        return b;
    }

    private void assertTarget(JsonNode resolved, DeskBindingEntity b, String url) {
        assertThat(resolved.at("/conversationHandover/channelBindingId").asText()).isEqualTo(b.getId());
        assertThat(resolved.at("/conversationHandover/channel/providerBaseUrl").asText()).isEqualTo(url);
        assertThat(resolved.at("/conversationHandover/channel/providerApiKeySecretRef").asText()).isEqualTo(b.getProviderKeyRef());
        assertThat(resolved.at("/conversationHandover/channel/runtimeCallbackSigningSecretRef").asText()).isEqualTo(b.getRuntimeCallbackKeyRef());
        assertThat(resolved.at("/conversationHandover/channel/providerWebhookSigningSecretRef").asText()).isEqualTo(b.getCallbackKeyRef());
    }

    private DeploymentEntity deployment() {
        var d = new DeploymentEntity(); d.setId("dep-a"); d.setCustomerId("customer-a"); d.setTenantId("tenant-a"); return d;
    }
    private DeploymentVersionEntity version() {
        var v = new DeploymentVersionEntity(); v.setId("ver-a"); v.setDeploymentId("dep-a"); return v;
    }
}
