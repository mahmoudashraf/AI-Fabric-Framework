package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentStorageSelectionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerDeploymentStorageSelectionServiceTest {
    private final DeploymentDraftRepository drafts = mock(DeploymentDraftRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomerDeploymentStorageSelectionService service = new CustomerDeploymentStorageSelectionService(drafts, mapper);
    private DeploymentEntity deployment;
    private DeploymentDraftEntity draft;

    @BeforeEach
    void setUp() {
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setStatus("DRAFT");
        deployment.setActiveDraftId("draft");
        draft = new DeploymentDraftEntity(); draft.setId("draft"); draft.setDeploymentId("deployment");
        draft.setStatus("DRAFT"); draft.setProviderConfigJson("{\"existingConfiguration\":true}");
        when(drafts.findByIdForUpdate("draft")).thenReturn(Optional.of(draft));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGED_POSTGRES", "CUSTOMER_POSTGRES"})
    void storesOnlyExplicitChoiceInOwnedDraftWithoutProvisioning(String mode) throws Exception {
        service.initialize(deployment, mode);
        var config = mapper.readTree(draft.getProviderConfigJson());
        assertThat(config.path("aiStateStorageMode").asText()).isEqualTo(mode);
        assertThat(config.path("existingConfiguration").asBoolean()).isTrue();
        assertThat(config.size()).isEqualTo(2);
        verify(drafts).saveAndFlush(draft);
    }

    @Test
    void templateModifiedEditableDraftAcceptsTheInitialChoice() throws Exception {
        draft.setStatus("MODIFIED");
        service.initialize(deployment, "CUSTOMER_POSTGRES");
        assertThat(mapper.readTree(draft.getProviderConfigJson()).path("aiStateStorageMode").asText())
            .isEqualTo("CUSTOMER_POSTGRES");
        assertThat(draft.getStatus()).isEqualTo("MODIFIED");
        verify(drafts).saveAndFlush(draft);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "managed", " MANAGED_POSTGRES", "H2", "vector", "CUSTOMER_BUSINESS_DATABASE"})
    void rejectsUnreviewedModes(String mode) {
        assertThatThrownBy(() -> service.initialize(deployment, mode)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(drafts);
    }

    @Test
    void foreignOrPublishedDraftCannotAcquireChoice() {
        draft.setDeploymentId("foreign");
        assertThatThrownBy(() -> service.initialize(deployment, "MANAGED_POSTGRES"))
            .hasMessageContaining("REVIEWED_COMPOSITION_CHANGED");
        verify(drafts, never()).saveAndFlush(any());
        draft.setDeploymentId("deployment"); draft.setStatus("PUBLISHED");
        assertThatThrownBy(() -> service.initialize(deployment, "MANAGED_POSTGRES"))
            .hasMessageContaining("REVIEWED_COMPOSITION_CHANGED");
    }

    @Test
    void replayCannotReplaceAnExistingChoice() {
        draft.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        assertThatThrownBy(() -> service.initialize(deployment, "MANAGED_POSTGRES"))
            .hasMessageContaining("REVIEWED_COMPOSITION_CHANGED");
        verify(drafts, never()).saveAndFlush(any());
    }
}
