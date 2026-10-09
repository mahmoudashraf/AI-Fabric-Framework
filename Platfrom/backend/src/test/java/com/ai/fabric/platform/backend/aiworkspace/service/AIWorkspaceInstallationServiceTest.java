package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIWorkspaceInstallationServiceTest {

    private final AIWorkspaceInstallationRepository repository = mock(AIWorkspaceInstallationRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformCustomerAccessService access = mock(PlatformCustomerAccessService.class);
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final AIWorkspaceConnectionProfileRegistry profiles = mock(AIWorkspaceConnectionProfileRegistry.class);
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null));
    private final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        var created = service().create("customer-1", new CreateAIWorkspaceInstallationRequest(
            "northfield-web", " Northfield website ", "dealership", "1.1.0",
            "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
            objectMapper.createObjectNode(), List.of("https://Dealer.Example", "https://dealer.example"),
            dealershipConfiguration()
        ));

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.displayName()).isEqualTo("Northfield website");
        assertThat(created.installationId()).matches("awi_pub_[a-f0-9]{32}");
        assertThat(created.allowedOrigins()).containsExactly("https://dealer.example");
        ArgumentCaptor<AIWorkspaceInstallationEntity> saved = ArgumentCaptor.forClass(AIWorkspaceInstallationEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo("customer-1");
        assertThat(saved.getValue().getConnectionConfigurationJson()).isEqualTo("{}");
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
            objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration()
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
                objectMapper.createObjectNode(), List.of("https://dealer.example"), dealershipConfiguration(), 0L
            ))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Disable");
    }

    private AIWorkspaceInstallationService service() {
        return new AIWorkspaceInstallationService(
            repository, customers, consumers, access, packs, profiles, validator,
            readiness, assets, audit, objectMapper
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
            "loomai-runtime-anonymous-v1", true, null, java.util.Map.of()
        );
    }
}
