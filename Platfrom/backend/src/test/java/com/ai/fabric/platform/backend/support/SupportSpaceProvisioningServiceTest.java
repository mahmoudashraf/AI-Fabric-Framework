package com.ai.fabric.platform.backend.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssignmentService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

class SupportSpaceProvisioningServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final SupportSettings settings = mock(SupportSettings.class);
  private final SupportRequesterAssertionService assertions =
      mock(SupportRequesterAssertionService.class);
  private final SupportTicketClient tickets = mock(SupportTicketClient.class);
  private final DeskServiceClient desk = mock(DeskServiceClient.class);
  private final AIWorkspaceInstallationRepository installations =
      mock(AIWorkspaceInstallationRepository.class);
  private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
  private final AIWorkspaceAssignmentService assignments = mock(AIWorkspaceAssignmentService.class);
  private final MockEnvironment env =
      new MockEnvironment().withProperty("platform.support.installation-id", "console-support");
  private final DeskServiceClient.Connection connection =
      new DeskServiceClient.Connection("https://desk.example.test", "admin-key");
  private final String path = "/api/desk/admin/v1/bindings/support-binding/support-space";
  private final SupportSpaceProvisioningService service =
      new SupportSpaceProvisioningService(
          settings, assertions, tickets, desk, installations, consumers, assignments, env, json);
  private AIWorkspaceInstallationEntity installation;
  private DeploymentEntity deployment;
  private DeploymentReleaseEntity release;
  private DeploymentVersionEntity version;
  private AIWorkspaceAssignmentService.Assignment assignment;
  private ObjectNode space;

  @BeforeEach
  void setup() {
    when(settings.binding())
        .thenReturn(
            new SupportContracts.RuntimeBinding(
                "support-owner",
                "support-tenant",
                "support-runtime",
                "staging",
                "private-issuer",
                "private-audience"));
    when(settings.deskBindingId()).thenReturn("support-binding");
    when(tickets.connection()).thenReturn(connection);
    installation = new AIWorkspaceInstallationEntity();
    installation.setInstallationId("console-support");
    installation.setCustomerId("support-owner");
    installation.setConsumerEntityId("consumer-row");
    installation.setExperiencePackCode("support");
    installation.setExperiencePackVersion("1.0.0");
    installation.setConnectionMode(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME);
    installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST);
    installation.setConnectionProfileVersion("1.0.0");
    installation.setConfigurationJson(
        """
        {"schemaVersion":"loomai-support-experience-config-v1","app":{"assistantLabel":"Support"},
         "support":{"topics":[{"code":"setup","label":"Setup label"},{"code":"knowledge","label":"Documents label"}]},
         "capabilities":{"tickets":true,"handover":true}}
        """);
    var consumer = new PlatformConsumerEntity();
    consumer.setId("consumer-row");
    consumer.setCustomerId("support-owner");
    deployment = new DeploymentEntity();
    deployment.setId("support-runtime");
    deployment.setCustomerId("support-owner");
    deployment.setTenantId("support-tenant");
    release = new DeploymentReleaseEntity();
    release.setTargetProfileId("staging");
    version = new DeploymentVersionEntity();
    version.setBehaviorConfigJson("{\"conversationHandover\":{\"enabled\":true}}");
    assignment =
        new AIWorkspaceAssignmentService.Assignment(
            consumer,
            deployment,
            release,
            version,
            json.createObjectNode(),
            "https://verified-runtime.example.test",
            "sha256:current");
    when(installations.findByInstallationId("console-support"))
        .thenReturn(Optional.of(installation));
    when(consumers.findById("consumer-row")).thenReturn(Optional.of(consumer));
    when(assignments.resolveCurrent(consumer)).thenReturn(assignment);
    when(assertions.issuer()).thenReturn("https://console.example.test/api/support/requester");
    when(assertions.jwksUri())
        .thenReturn("https://console.example.test/api/public/support/requester-jwks");
    when(assertions.audience("support-binding")).thenReturn("loomai-support:support-binding");
    when(assertions.serverKey()).thenReturn("dedicated-requester-key");
    when(assertions.connectorKey()).thenReturn("distinct-connector-key");
    when(desk.getOptional(connection, path)).thenReturn(Optional.empty());
    space =
        json.createObjectNode()
            .put("spaceId", "support-binding")
            .put("bindingId", "support-binding")
            .put("providerBindingId", "support-binding")
            .put("providerId", "desk")
            .put("enabled", true)
            .put("humanHandoverEnabled", true)
            .put("version", "opaque-configuration-version");
    space
        .putObject("identity")
        .put("mode", "TRUSTED_PRIVATE_HOST")
        .put("issuer", "https://console.example.test/api/support/requester")
        .put("audience", "loomai-support:support-binding");
    when(desk.get(connection, path)).thenAnswer(ignored -> space.deepCopy());
    when(desk.put(eq(connection), eq(path), any(JsonNode.class))).thenReturn(space);
  }

  @Test
  void preparesRegisteredSpaceWithActualAssignmentTopicCodesAndDistinctManagedCredentials() {
    assertThat(service.prepare()).isSameAs(space);
    var body = ArgumentCaptor.forClass(JsonNode.class);
    verify(desk).put(eq(connection), eq(path), body.capture());
    JsonNode input = body.getValue();
    assertThat(input.path("spaceId").asText()).isEqualTo("support-binding");
    assertThat(input.path("providerBindingId").asText()).isEqualTo("support-binding");
    assertThat(input.path("providerId").asText()).isEqualTo("desk");
    assertThat(input.path("runtimeBaseUrl").asText()).isEqualTo(assignment.runtimeBaseUrl());
    assertThat(input.at("/conversation/mode").asText()).isEqualTo("executor");
    assertThat(input.path("conversation").has("position")).isFalse();
    assertThat(input.path("runtimeTrustedKeyHeader").asText())
        .isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
    assertThat(input.path("expectedVersion").isMissingNode()).isTrue();
    assertThat(json.convertValue(input.path("topics"), List.class))
        .containsExactly("setup", "knowledge");
    assertThat(input.path("allowedOrigins")).isEmpty();
    assertThat(input.path("connectorKey").asText()).isEqualTo("distinct-connector-key");
    assertThat(input.path("identity").path("privateServerKey").asText())
        .isEqualTo("dedicated-requester-key");
    assertThat(input.path("identity").path("mode").asText()).isEqualTo("TRUSTED_PRIVATE_HOST");
    assertThat(input.path("identity").path("jwksUri").asText()).isEqualTo(assertions.jwksUri());
    assertThat(input.path("identity").path("requesterNamespace").asText())
        .isEqualTo("loomai-console:support-binding");
    verify(assertions).prepare();
    verify(assignments).resolveCurrent(assignment.consumer());
  }

  @Test
  void registersOnlyTheInstallationsApprovedReturnOriginsWithoutChangingPrivateIdentity() {
    installation.setAllowedOriginsJson("[\"https://console.example.test\"]");
    service.prepare();
    var body = ArgumentCaptor.forClass(JsonNode.class);
    verify(desk).put(eq(connection), eq(path), body.capture());
    assertThat(json.convertValue(body.getValue().path("allowedOrigins"), List.class))
        .containsExactly("https://console.example.test");
    assertThat(body.getValue().at("/identity/mode").asText()).isEqualTo("TRUSTED_PRIVATE_HOST");
  }

  @Test
  void updatingSpaceUsesRemoteOpaqueConfigurationVersionAndNotLocalRevision() {
    when(desk.getOptional(connection, path)).thenReturn(Optional.of(space));
    service.prepare();
    var body = ArgumentCaptor.forClass(JsonNode.class);
    verify(desk).put(eq(connection), eq(path), body.capture());
    assertThat(body.getValue().path("expectedVersion").asText())
        .isEqualTo("opaque-configuration-version");
  }

  @Test
  void prepareRejectsWrongOwnerModeAndTargetBeforeRegisteringAnyKeysOrSpace() {
    installation.setCustomerId("foreign-owner");
    unavailable(service::prepare);
    installation.setCustomerId("support-owner");
    installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED);
    unavailable(service::prepare);
    installation.setConnectionMode(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME);
    release.setTargetProfileId("production");
    unavailable(service::prepare);
    release.setTargetProfileId("staging");
    deployment.setId("other-runtime");
    unavailable(service::prepare);
    verifyNoInteractions(assertions, desk, tickets);
  }

  @Test
  void readinessIsReadOnlyAndOnlyClaimsTheExplicitPrivateSupportProfile() {
    assertThat(service.supports(installation)).isTrue();
    service.validate(installation, assignment);
    verify(desk).get(connection, path);
    verify(desk, never()).put(any(), anyString(), any());
    verify(assertions, never()).prepare();
    verifyNoInteractions(assignments, installations, consumers);
    installation.setExperiencePackCode("generic-workspace");
    assertThat(service.supports(installation)).isFalse();
    installation.setExperiencePackCode("support");
    installation.setConnectionProfileCode("other");
    assertThat(service.supports(installation)).isFalse();
    installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST);
    installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED);
    assertThat(service.supports(installation)).isFalse();
  }

  @Test
  void readinessRequiresConfiguredInstallationOwnerAndFrozenDeploymentTarget() {
    installation.setInstallationId("other-installation");
    unavailable(() -> service.validate(installation, assignment));
    installation.setInstallationId("console-support");
    installation.setCustomerId("foreign-owner");
    unavailable(() -> service.validate(installation, assignment));
    installation.setCustomerId("support-owner");
    release.setTargetProfileId("production");
    unavailable(() -> service.validate(installation, assignment));
    release.setTargetProfileId("staging");
    deployment.setId("other-runtime");
    unavailable(() -> service.validate(installation, assignment));
    verifyNoInteractions(desk, tickets);
  }

  @Test
  void readinessRequiresExactDeskProviderBindingAndPrivateIdentity() {
    ObjectNode valid = space.deepCopy();
    for (String field : List.of("spaceId", "bindingId", "providerBindingId", "providerId")) {
      space = valid.deepCopy();
      space.put(field, "foreign");
      unavailable(() -> service.validate(installation, assignment));
    }
    for (String field : List.of("mode", "issuer", "audience")) {
      space = valid.deepCopy();
      ((ObjectNode) space.path("identity")).put(field, "foreign");
      unavailable(() -> service.validate(installation, assignment));
    }
    verify(desk, never()).put(any(), anyString(), any());
    verify(assertions, never()).prepare();
  }

  @Test
  void requestedTicketAndHandoverCapabilitiesMustBeEnabledOnBothSides() {
    space.put("enabled", false);
    unavailable(() -> service.validate(installation, assignment));
    space.put("enabled", true).put("humanHandoverEnabled", false);
    unavailable(() -> service.validate(installation, assignment));
    space.put("humanHandoverEnabled", true);
    version.setBehaviorConfigJson("{\"conversationHandover\":{\"enabled\":false}}");
    unavailable(() -> service.validate(installation, assignment));
    installation.setConfigurationJson("{\"capabilities\":{\"tickets\":false,\"handover\":true}}");
    version.setBehaviorConfigJson("{\"conversationHandover\":{\"enabled\":true}}");
    space.put("enabled", false).put("humanHandoverEnabled", true);
    service.validate(installation, assignment);
    installation.setConfigurationJson("{\"capabilities\":{\"tickets\":false,\"handover\":false}}");
    space.put("enabled", false).put("humanHandoverEnabled", false);
    service.validate(installation, assignment);
    verify(desk, never()).put(any(), anyString(), any());
  }

  private void unavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action).hasMessageContaining("SUPPORT_NOT_CONFIGURED");
  }
}
