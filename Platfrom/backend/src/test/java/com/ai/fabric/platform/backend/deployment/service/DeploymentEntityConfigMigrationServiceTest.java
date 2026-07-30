package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.EntityConfigMigrationAuditEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigV03ToV04Migrator;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.EntityConfigMigrationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentEntityConfigMigrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeploymentDraftRepository draftRepository = mock(DeploymentDraftRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final EntityConfigMigrationAuditRepository auditRepository =
        mock(EntityConfigMigrationAuditRepository.class);
    private final DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
    private final PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
    private final EntityConfigContractService contractService = new EntityConfigContractService(objectMapper);
    private final EntityConfigV03ToV04Migrator migrator =
        new EntityConfigV03ToV04Migrator(objectMapper, contractService);
    private final DeploymentEntityConfigMigrationService service =
        new DeploymentEntityConfigMigrationService(
            draftRepository,
            deploymentRepository,
            auditRepository,
            deploymentAccessService,
            migrator,
            platformAuditService,
            objectMapper
        );

    @Test
    void appliesMigrationOnlyToActiveMutableDraftAndBacksUpOriginalConfig() {
        DeploymentEntity deployment = deployment("drf-active");
        String originalConfig = validLegacyConfig();
        DeploymentDraftEntity draft = draft("drf-active", "DRAFT", originalConfig);
        stubTarget(deployment, draft);
        when(draftRepository.save(any(DeploymentDraftEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any(EntityConfigMigrationAuditEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.apply(draft.getId());

        assertThat(result.applied()).isTrue();
        assertThat(result.currentContractVersion()).isEqualTo(EntityConfigContractService.CONTRACT_VERSION_V04);
        assertThat(draft.getStatus()).isEqualTo("MODIFIED");
        assertThat(draft.getEntityConfigJson())
            .contains("\"indexing\"")
            .doesNotContain("\"indexable\"");
        verify(draftRepository).save(draft);

        ArgumentCaptor<EntityConfigMigrationAuditEntity> auditCaptor =
            ArgumentCaptor.forClass(EntityConfigMigrationAuditEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        EntityConfigMigrationAuditEntity audit = auditCaptor.getValue();
        assertThat(audit.getStatus()).isEqualTo("APPLIED");
        assertThat(audit.getBeforeConfigJson()).isEqualTo(originalConfig);
        assertThat(audit.getAfterConfigJson()).contains("\"indexing\"");
        assertThat(audit.getBeforeHash()).isNotEqualTo(audit.getAfterHash());
    }

    @Test
    void blockedMigrationLeavesDraftUnchangedAndPersistsEvidence() {
        DeploymentEntity deployment = deployment("drf-active");
        String blockedConfig = legacyConfigWithUnknownMetadataType();
        DeploymentDraftEntity draft = draft("drf-active", "DRAFT", blockedConfig);
        stubTarget(deployment, draft);
        when(auditRepository.save(any(EntityConfigMigrationAuditEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.apply(draft.getId());

        assertThat(result.applied()).isFalse();
        assertThat(result.report().blocked()).isTrue();
        assertThat(draft.getEntityConfigContractVersion())
            .isEqualTo(EntityConfigContractService.CONTRACT_VERSION_V03);
        assertThat(draft.getEntityConfigJson()).isEqualTo(blockedConfig);
        verify(draftRepository, never()).save(any());

        ArgumentCaptor<EntityConfigMigrationAuditEntity> auditCaptor =
            ArgumentCaptor.forClass(EntityConfigMigrationAuditEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getStatus()).isEqualTo("BLOCKED");
        assertThat(auditCaptor.getValue().getBeforeConfigJson()).isEqualTo(blockedConfig);
        assertThat(auditCaptor.getValue().getReportJson()).contains("UNKNOWN_METADATA_TYPE");
    }

    @Test
    void repeatedApplyOnNormalizedV04DraftIsNoOpButAudited() throws Exception {
        DeploymentEntity deployment = deployment("drf-active");
        var normalized = migrator.preview(objectMapper.readTree(validLegacyConfig())).migratedConfig();
        DeploymentDraftEntity draft = draft(
            "drf-active",
            "MODIFIED",
            objectMapper.writeValueAsString(normalized)
        );
        draft.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V04);
        stubTarget(deployment, draft);
        when(auditRepository.save(any(EntityConfigMigrationAuditEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.apply(draft.getId());

        assertThat(result.applied()).isFalse();
        assertThat(result.report().blocked()).isFalse();
        assertThat(result.report().migrationRequired()).isFalse();
        verify(draftRepository, never()).save(any());
        ArgumentCaptor<EntityConfigMigrationAuditEntity> auditCaptor =
            ArgumentCaptor.forClass(EntityConfigMigrationAuditEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getStatus()).isEqualTo("NO_CHANGE");
        assertThat(auditCaptor.getValue().getBeforeHash()).isEqualTo(auditCaptor.getValue().getAfterHash());
    }

    @Test
    void canonicalRepairAdvancesLegacyLabelOnNormalizedV04ConfigWithoutRequestAccess() throws Exception {
        DeploymentEntity deployment = deployment("drf-active");
        var normalized = migrator.preview(objectMapper.readTree(validLegacyConfig())).migratedConfig();
        DeploymentDraftEntity draft = draft(
            "drf-active",
            "MODIFIED",
            objectMapper.writeValueAsString(normalized)
        );
        stubInternalTarget(deployment, draft);
        when(draftRepository.save(any(DeploymentDraftEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any(EntityConfigMigrationAuditEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.applyForCanonicalRolloutInternal(draft.getId());

        assertThat(result.applied()).isTrue();
        assertThat(result.report().migrationRequired()).isFalse();
        assertThat(result.currentContractVersion())
            .isEqualTo(EntityConfigContractService.CONTRACT_VERSION_V04);
        verify(deploymentAccessService, never()).requireDeploymentEditorAccess(any());
        ArgumentCaptor<EntityConfigMigrationAuditEntity> auditCaptor =
            ArgumentCaptor.forClass(EntityConfigMigrationAuditEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getStatus()).isEqualTo("APPLIED");
        assertThat(auditCaptor.getValue().getBeforeHash())
            .isEqualTo(auditCaptor.getValue().getAfterHash());
    }

    @Test
    void refusesNonActiveOrPublishedDraftsBeforeMutation() {
        DeploymentEntity deployment = deployment("drf-other");
        DeploymentDraftEntity nonActive = draft("drf-active", "DRAFT", validLegacyConfig());
        stubTarget(deployment, nonActive);

        assertThatThrownBy(() -> service.apply(nonActive.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only the active deployment draft");
        verify(draftRepository, never()).save(any());
        verify(auditRepository, never()).save(any());

        DeploymentEntity publishedDeployment = deployment("drf-published");
        DeploymentDraftEntity published = draft("drf-published", "PUBLISHED", validLegacyConfig());
        stubTarget(publishedDeployment, published);

        assertThatThrownBy(() -> service.apply(published.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Published drafts and deployment versions are immutable");
        verify(draftRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
    }

    private void stubTarget(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        stubInternalTarget(deployment, draft);
        when(deploymentAccessService.requireDeploymentEditorAccess(deployment)).thenReturn(deployment);
    }

    private void stubInternalTarget(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        when(draftRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
    }

    private static DeploymentEntity deployment(String activeDraftId) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-test");
        deployment.setActiveDraftId(activeDraftId);
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        return deployment;
    }

    private static DeploymentDraftEntity draft(String id, String status, String entityConfigJson) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId(id);
        draft.setDeploymentId("dep-test");
        draft.setStatus(status);
        draft.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V03);
        draft.setEntityConfigJson(entityConfigJson);
        draft.setProviderConfigJson("{}");
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        return draft;
    }

    private static String validLegacyConfig() {
        return """
            {
              "ai-config": {
                "vector-dimensions": 512
              },
              "ai-entities": {
                "document": {
                  "indexable": true,
                  "searchable-fields": [
                    {
                      "name": "content"
                    }
                  ]
                }
              }
            }
            """;
    }

    private static String legacyConfigWithUnknownMetadataType() {
        return """
            {
              "ai-config": {
                "vector-dimensions": 512
              },
              "ai-entities": {
                "document": {
                  "searchable-fields": [
                    {
                      "name": "content"
                    }
                  ],
                  "metadata-fields": [
                    {
                      "name": "classification",
                      "type": "custom-value"
                    }
                  ]
                }
              }
            }
            """;
    }
}
