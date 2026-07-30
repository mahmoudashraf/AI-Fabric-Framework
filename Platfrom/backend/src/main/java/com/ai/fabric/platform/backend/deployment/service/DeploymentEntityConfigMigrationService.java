package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.EntityConfigMigrationAuditEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigMigrationResult;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigV03ToV04Migrator;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigValidationContext;
import com.ai.fabric.platform.backend.deployment.model.DeploymentEntityConfigMigrationSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.EntityConfigMigrationAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentEntityConfigMigrationService {

    private final DeploymentDraftRepository draftRepository;
    private final DeploymentRepository deploymentRepository;
    private final EntityConfigMigrationAuditRepository auditRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final EntityConfigV03ToV04Migrator migrator;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentEntityConfigMigrationService(DeploymentDraftRepository draftRepository,
                                                  DeploymentRepository deploymentRepository,
                                                  EntityConfigMigrationAuditRepository auditRepository,
                                                  DeploymentAccessService deploymentAccessService,
                                                  EntityConfigV03ToV04Migrator migrator,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.deploymentRepository = deploymentRepository;
        this.auditRepository = auditRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.migrator = migrator;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public DeploymentEntityConfigMigrationSummary preview(String draftId) {
        MigrationTarget target = target(draftId);
        EntityConfigMigrationResult result = evaluate(target.draft());
        return summary(target, result, false, Instant.now());
    }

    @Transactional
    public DeploymentEntityConfigMigrationSummary apply(String draftId) {
        MigrationTarget target = target(draftId);
        assertActiveMutableDraft(target.deployment(), target.draft());
        String beforeConfigJson = target.draft().getEntityConfigJson();
        EntityConfigMigrationResult result = evaluate(target.draft());
        Instant now = Instant.now();
        boolean applied = false;
        String auditStatus;
        if (result.report().blocked()) {
            auditStatus = "BLOCKED";
        } else if (!result.report().migrationRequired()
            && EntityConfigContractService.CONTRACT_VERSION_V04.equals(
                target.draft().getEntityConfigContractVersion()
            )) {
            auditStatus = "NO_CHANGE";
        } else {
            target.draft().setEntityConfigJson(writeJson(result.migratedConfig()));
            target.draft().setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V04);
            target.draft().setStatus("MODIFIED");
            target.draft().setUpdatedAt(now);
            draftRepository.save(target.draft());
            applied = true;
            auditStatus = "APPLIED";
        }

        saveAudit(target, result, auditStatus, beforeConfigJson, now);
        platformAuditService.record(
            "ENTITY_CONFIG_MIGRATION_" + auditStatus,
            "DEPLOYMENT_DRAFT",
            target.draft().getId(),
            Map.of(
                "deploymentId", target.deployment().getId(),
                "sourceContractVersion", result.report().sourceContractVersion(),
                "targetContractVersion", result.report().targetContractVersion(),
                "beforeHash", result.report().beforeHash(),
                "afterHash", result.report().afterHash(),
                "blocked", result.report().blocked()
            )
        );
        return summary(target, result, applied, now);
    }

    private MigrationTarget target(String draftId) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        DeploymentEntity deployment = deploymentRepository.findById(draft.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + draft.getDeploymentId()));
        deploymentAccessService.requireDeploymentEditorAccess(deployment);
        return new MigrationTarget(deployment, draft);
    }

    private EntityConfigMigrationResult evaluate(DeploymentDraftEntity draft) {
        try {
            JsonNode entityConfig = objectMapper.readTree(draft.getEntityConfigJson());
            JsonNode providerConfig = objectMapper.readTree(draft.getProviderConfigJson());
            return migrator.preview(
                entityConfig,
                new EntityConfigValidationContext(
                    false,
                    ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)
                )
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(
                CONFLICT,
                "Entity configuration migration could not parse the draft: " + ex.getMessage(),
                ex
            );
        }
    }

    private void assertActiveMutableDraft(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        if (!draft.getId().equals(deployment.getActiveDraftId())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Only the active deployment draft can be migrated."
            );
        }
        if ("PUBLISHED".equalsIgnoreCase(draft.getStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Published drafts and deployment versions are immutable."
            );
        }
    }

    private void saveAudit(MigrationTarget target,
                           EntityConfigMigrationResult result,
                           String status,
                           String beforeConfigJson,
                           Instant now) {
        EntityConfigMigrationAuditEntity audit = new EntityConfigMigrationAuditEntity();
        audit.setId("ecm-" + UUID.randomUUID());
        audit.setDeploymentId(target.deployment().getId());
        audit.setDraftId(target.draft().getId());
        audit.setSourceContractVersion(result.report().sourceContractVersion());
        audit.setTargetContractVersion(result.report().targetContractVersion());
        audit.setStatus(status);
        audit.setBeforeHash(result.report().beforeHash());
        audit.setAfterHash(result.report().afterHash());
        audit.setBeforeConfigJson(beforeConfigJson);
        audit.setAfterConfigJson(writeJson(result.migratedConfig()));
        audit.setReportJson(writeJson(result.report()));
        audit.setCreatedAt(now);
        auditRepository.save(audit);
    }

    private DeploymentEntityConfigMigrationSummary summary(MigrationTarget target,
                                                           EntityConfigMigrationResult result,
                                                           boolean applied,
                                                           Instant now) {
        return new DeploymentEntityConfigMigrationSummary(
            target.deployment().getId(),
            target.draft().getId(),
            target.draft().getEntityConfigContractVersion(),
            applied,
            result.report(),
            result.migratedConfig(),
            now
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize entity migration evidence.", ex);
        }
    }

    private record MigrationTarget(
        DeploymentEntity deployment,
        DeploymentDraftEntity draft
    ) {
    }
}
