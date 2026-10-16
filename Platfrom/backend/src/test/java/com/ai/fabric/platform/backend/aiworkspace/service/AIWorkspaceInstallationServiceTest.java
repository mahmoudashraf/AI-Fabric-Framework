package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIWorkspaceInstallationServiceTest {

    private final AIWorkspaceInstallationRepository repository = mock(AIWorkspaceInstallationRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformCustomerAccessService access = mock(PlatformCustomerAccessService.class);
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final AIWorkspaceConnectionProfileRegistry profiles = mock(AIWorkspaceConnectionProfileRegistry.class);
    private final AIWorkspaceAuthenticationProfileRegistry authenticationProfiles =
        new AIWorkspaceAuthenticationProfileRegistry(new ObjectMapper());
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.loomai.test", "https://workspace.loomai.test"));
    private final AIWorkspaceDomainService domains = mock(AIWorkspaceDomainService.class);
    private final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CustomerSelfServiceOperationBoundary offerBoundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final AIWorkspaceDeploymentSecurityProjectionService deploymentSecurityProjection =
        mock(AIWorkspaceDeploymentSecurityProjectionService.class);

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"APP_CONFIGURE", "AUTH_CONFIGURE", "DOMAIN_CONFIGURE"})
    void offerConfigurationDenialsStopBeforeInstallationOrDomainMutation(Operation operation) throws Exception {
        when(customers.existsById("customer-1")).thenReturn(true);
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "northfield-web"))
            .thenReturn(Optional.of(consumer()));
        when(profiles.resolve(any(), any(), any(), any())).thenReturn(profile());
        when(offerBoundary.profileId(any(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");
        when(offerBoundary.requireConsumer(any(), any(), any(), any())).thenReturn(new Authorization(null, null));
        doThrow(CustomerSelfServiceException.deny(CustomerSelfServiceException.Code.SETTING_FORBIDDEN))
            .when(offerBoundary).requireConsumer(any(), eq(operation), any(), any());

        assertThatThrownBy(() -> service().create("customer-1", new CreateAIWorkspaceInstallationRequest(
            "northfield-web", "Workspace", "dealership", "1.1.0",
            "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
            objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration(),
            "embedded", null, List.of(), "public", "none", "1.0.0", objectMapper.createObjectNode()), "app-operation"))
            .hasMessageContaining("SETTING_FORBIDDEN");
        verify(repository, never()).saveAndFlush(any());
        verify(domains, never()).synchronize(any(), any(), any(), any(), any());
    }

    @Test
    void removingThemeAndDomainStillChecksTheirReviewedSettingsBeforeMutation() throws Exception {
        when(customers.existsById("customer-1")).thenReturn(true);
        var consumer = consumer();
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "northfield-web"))
            .thenReturn(Optional.of(consumer));
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        var installation = installation();
        installation.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        installation.setAllowedOriginsJson("[\"https://old.example\"]");
        when(repository.findByCustomerIdAndInstallationId("customer-1", installation.getInstallationId()))
            .thenReturn(Optional.of(installation));
        when(profiles.resolve(any(), any(), any(), any())).thenReturn(profile());
        when(offerBoundary.profileId(any(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");
        when(offerBoundary.requireConsumer(any(), any(), any(), any())).thenReturn(new Authorization(null, null));
        doThrow(CustomerSelfServiceException.deny(CustomerSelfServiceException.Code.SETTING_FORBIDDEN))
            .when(offerBoundary).requireConsumer(eq(consumer), eq(Operation.DOMAIN_CONFIGURE), eq("app-update"),
                eq(Map.of(Setting.APP_DOMAIN, objectMapper.getNodeFactory().textNode("https://old.example"))));

        assertThatThrownBy(() -> service().update("customer-1", installation.getInstallationId(),
            new UpdateAIWorkspaceInstallationRequest("northfield-web", "Workspace", "dealership", "1.1.0",
                "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0", objectMapper.createObjectNode(),
                List.of("https://dealer.example"), dealershipConfiguration(), "embedded", null, List.of(),
                "public", "none", "1.0.0", objectMapper.createObjectNode(), 0L), "app-update"))
            .hasMessageContaining("SETTING_FORBIDDEN");

        verify(offerBoundary).profileId(eq("theme"), isNull());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Setting, JsonNode>> values = ArgumentCaptor.forClass(Map.class);
        verify(offerBoundary, times(2)).requireConsumer(eq(consumer), eq(Operation.APP_CONFIGURE), eq("app-update"), values.capture());
        assertThat(values.getAllValues().getLast()).containsEntry(Setting.THEME_PROFILE_ID,
            objectMapper.getNodeFactory().textNode("theme:profile"));
        verify(repository, never()).saveAndFlush(any());
        verify(domains, never()).synchronize(any(), any(), any(), any(), any());
    }

    @Test
    void createsCustomerScopedDraftWithOpaquePublicIdAndNormalizedConfiguration() throws Exception {
        PlatformConsumerEntity consumer = consumer();
        when(customers.existsById("customer-1")).thenReturn(true);
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "northfield-web"))
            .thenReturn(Optional.of(consumer));
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(readiness.evaluate(any())).thenAnswer(call -> notReady(call.getArgument(0)));
        when(profiles.resolve(any(), any(), any(), any())).thenReturn(profile());
        when(domains.synchronize(any(), any(), any(), any(), any()))
            .thenAnswer(call -> call.getArgument(4));
        when(domains.summaries(any())).thenReturn(List.of());

        var created = service().create("customer-1", new CreateAIWorkspaceInstallationRequest(
            "northfield-web", " Northfield website ", "dealership", "1.1.0",
            "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
            objectMapper.createObjectNode(), List.of("https://Dealer.Example", "https://dealer.example"),
            dealershipConfiguration(), "embedded", null, List.of(), "public", "none", "1.0.0",
            objectMapper.createObjectNode()
        ));

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.displayName()).isEqualTo("Northfield website");
        assertThat(created.installationId()).matches("awi_pub_[a-f0-9]{32}");
        assertThat(created.allowedOrigins()).containsExactly("https://dealer.example");
        ArgumentCaptor<AIWorkspaceInstallationEntity> saved = ArgumentCaptor.forClass(AIWorkspaceInstallationEntity.class);
        verify(repository, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo("customer-1");
        assertThat(saved.getValue().getConnectionConfigurationJson()).isEqualTo("{}");
        verify(deploymentSecurityProjection).synchronizeForConsumer(consumer);
        var authorizationOrder = inOrder(deploymentSecurityProjection, offerBoundary);
        authorizationOrder.verify(deploymentSecurityProjection).lockDeploymentsForConsumers(List.of(consumer));
        authorizationOrder.verify(offerBoundary).requireConsumer(
            eq(consumer), eq(Operation.APP_CONFIGURE), isNull(), any());
        verify(access).requireCustomerManagementAccess("customer-1");
        verify(audit).record(any(), any(), any(), any());
    }

    @Test
    void rejectsConsumerFromAnotherCustomerAndPreventsEditingActiveInstallation() throws Exception {
        when(customers.existsById("customer-1")).thenReturn(true);
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "other-consumer"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create("customer-1", new CreateAIWorkspaceInstallationRequest(
            "other-consumer", "Workspace", "dealership", "1.1.0",
            "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
            objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration(),
            "embedded", null, List.of(), "public", "none", "1.0.0", objectMapper.createObjectNode()
        ))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Consumer not found");
        verify(repository, never()).saveAndFlush(any());

        AIWorkspaceInstallationEntity active = installation();
        active.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        when(repository.findByCustomerIdAndInstallationId("customer-1", active.getInstallationId()))
            .thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service().update("customer-1", active.getInstallationId(),
            new UpdateAIWorkspaceInstallationRequest(
                "northfield-web", "Changed", "dealership", "1.1.0",
                "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
                objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration(),
                "embedded", null, List.of(), "public", "none", "1.0.0",
                objectMapper.createObjectNode(), 0L
            ))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Disable");
    }

    @Test
    void editingDisabledInstallationReturnsItToDraftAndProjectsItsOrigins() throws Exception {
        when(customers.existsById("customer-1")).thenReturn(true);
        PlatformConsumerEntity consumer = consumer();
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "northfield-web"))
            .thenReturn(Optional.of(consumer));
        AIWorkspaceInstallationEntity disabled = configuredInstallation();
        disabled.setStatus(AIWorkspaceInstallationStatus.DISABLED);
        disabled.setDisabledAt(Instant.now());
        when(repository.findByCustomerIdAndInstallationId("customer-1", disabled.getInstallationId()))
            .thenReturn(Optional.of(disabled));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(profiles.resolve(any(), any(), any(), any())).thenReturn(profile());
        when(domains.synchronize(any(), any(), any(), any(), any())).thenAnswer(call -> call.getArgument(4));
        when(domains.summaries(any())).thenReturn(List.of());
        when(readiness.evaluate(any())).thenAnswer(call -> notReady(call.getArgument(0)));
        when(offerBoundary.profileId(any(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");

        var result = service().update("customer-1", disabled.getInstallationId(),
            new UpdateAIWorkspaceInstallationRequest(
                "northfield-web", "Northfield", "dealership", "1.1.0",
                "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
                objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration(),
                "embedded", null, List.of(), "public", "none", "1.0.0",
                objectMapper.createObjectNode(), 0L
            ), "workspace-edit");

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(disabled.getDisabledAt()).isNull();
        verify(deploymentSecurityProjection).synchronizeForConsumer(consumer);
    }

    @Test
    void activatingReadyInstallationRestoresDeploymentOriginProjection() throws Exception {
        when(customers.existsById("customer-1")).thenReturn(true);
        PlatformConsumerEntity consumer = consumer();
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        AIWorkspaceInstallationEntity installation = configuredInstallation();
        installation.setStatus(AIWorkspaceInstallationStatus.DISABLED);
        when(repository.findByCustomerIdAndInstallationId("customer-1", installation.getInstallationId()))
            .thenReturn(Optional.of(installation));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(readiness.evaluate(installation)).thenReturn(new AIWorkspaceReadinessSummary(
            true, installation.getInstallationId(), installation.getConnectionMode().manifestValue(),
            consumer.getConsumerId(), "deployment-1", "release-1", "https://runtime.example", "assignment-1", List.of()));
        when(domains.summaries(any())).thenReturn(List.of());
        when(offerBoundary.profileId(any(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");

        var result = service().activate("customer-1", installation.getInstallationId(), "workspace-activate");

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(deploymentSecurityProjection).synchronizeForConsumer(consumer);
    }

    private AIWorkspaceInstallationService service() {
        return new AIWorkspaceInstallationService(
            repository, customers, consumers, access, packs, profiles, authenticationProfiles, validator,
            domains, readiness, assets, audit, objectMapper,
            offerBoundary, deploymentSecurityProjection
        );
    }

    private com.fasterxml.jackson.databind.node.ObjectNode dealershipConfiguration() throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Northfield AI"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page","maxChars":1800,"maxPages":3,"maxTotalChars":10000}
            }
            """);
    }

    private PlatformConsumerEntity consumer() {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity-1");
        consumer.setCustomerId("customer-1");
        consumer.setConsumerId("northfield-web");
        consumer.setStatus("ACTIVE");
        return consumer;
    }

    private AIWorkspaceInstallationEntity installation() {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId("awi-1");
        installation.setInstallationId("awi_pub_0123456789abcdef0123456789abcdef");
        installation.setCustomerId("customer-1");
        installation.setConsumerEntityId("consumer-entity-1");
        installation.setDisplayName("Northfield");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setCreatedAt(Instant.now());
        installation.setUpdatedAt(Instant.now());
        return installation;
    }

    private AIWorkspaceInstallationEntity configuredInstallation() throws Exception {
        AIWorkspaceInstallationEntity installation = installation();
        installation.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        installation.setExperiencePackCode("dealership");
        installation.setExperiencePackVersion("1.1.0");
        installation.setConnectionProfileCode("runtime-anonymous-direct");
        installation.setConnectionProfileVersion("1.0.0");
        installation.setDeliveryMode(com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode.EMBEDDED);
        installation.setInteractionPolicy(
            com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy.PUBLIC);
        installation.setAuthProfileCode("none");
        installation.setAuthProfileVersion("1.0.0");
        installation.setAuthConfigurationJson("{}");
        installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson("[\"https://dealer.example\"]");
        installation.setConfigurationJson(objectMapper.writeValueAsString(dealershipConfiguration()));
        return installation;
    }

    private AIWorkspaceReadinessSummary notReady(AIWorkspaceInstallationEntity entity) {
        return new AIWorkspaceReadinessSummary(
            false, entity.getInstallationId(), entity.getConnectionMode().manifestValue(), "northfield-web",
            null, null, null, null, List.of()
        );
    }

    private AIWorkspaceConnectionProfileRegistry.ConnectionProfile profile() {
        return new AIWorkspaceConnectionProfileRegistry.ConnectionProfile(
            "runtime-anonymous-direct", "1.0.0", "Anonymous",
            AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS, "direct-public-runtime",
            "loomai-runtime-anonymous-v1", true, null, null, java.util.Map.of()
        );
    }
}
