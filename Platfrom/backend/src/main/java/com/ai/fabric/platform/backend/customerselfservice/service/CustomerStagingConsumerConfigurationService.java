package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Configuration only: reuses the canonical consumer binding/history and atomic draft-origin projection. */
@Service
public class CustomerStagingConsumerConfigurationService {
    private static final String KIND = "CONFIGURE_EXISTING_STAGING_CONSUMER_V1";
    // This is the existing schema's configuration category, not a new-consumer creation receipt.
    private static final String COMMAND_TYPE = "BOOTSTRAP_CONFIG";
    private final CustomerOfferEntitlementService entitlements;
    private final PlatformConsumerRepository consumers;
    private final DeploymentRepository deployments;
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final PlatformCustomerConsumerService consumerService;
    private final CustomerEntitlementCommandRepository commands;
    private final CustomerOfferCodec codec;
    private final EntityManager entityManager;
    private final AIWorkspaceInstallationService workspaces;

    public CustomerStagingConsumerConfigurationService(CustomerOfferEntitlementService entitlements,
            PlatformConsumerRepository consumers, DeploymentRepository deployments,
            CustomerOfferDeploymentRepository associations, CustomerSelfServiceAuthorizationService authorization,
            PlatformCustomerConsumerService consumerService, CustomerEntitlementCommandRepository commands,
            CustomerOfferCodec codec, EntityManager entityManager, AIWorkspaceInstallationService workspaces) {
        this.entitlements = entitlements; this.consumers = consumers; this.deployments = deployments;
        this.associations = associations; this.authorization = authorization; this.consumerService = consumerService;
        this.commands = commands; this.codec = codec; this.entityManager = entityManager;
        this.workspaces = workspaces;
    }

    @Transactional
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public PlatformConsumerSummary configure(String offerId, String deploymentId, Request request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null) throw invalid();
        var actor = entitlements.requireCustomerActor();
        // Same order as workspace configuration and existing staging activation: consumer -> D -> entitlement.
        var consumer = consumers.findBindingForUpdate(actor.customerId(), request.consumerId())
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        entityManager.refresh(consumer);
        if (!actor.customerId().equals(consumer.getCustomerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        entityManager.refresh(deployment);
        if (!actor.customerId().equals(deployment.getCustomerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var association = associations.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        entityManager.refresh(association);
        if (!actor.customerId().equals(association.getCustomerId()) || !offerId.equals(association.getOfferId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || blank(association.getBootstrapTargetProfileId(), 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
        var entitlement = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        entityManager.refresh(entitlement);
        // Revalidate owner/status/expiry after refreshing a potentially Open-In-View-cached entitlement.
        entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            association.getBootstrapTargetProfileId(), deploymentId, deployment.getTenantId(), null,
            Operation.APP_CONFIGURE, request.operationKey(), Set.of(), null, null, null));
        if (approved.context().intent() != TargetIntent.CUSTOMER_STAGING
            || !association.getBootstrapTargetProfileId().equals(approved.context().targetProfileId())) throw deny(UNENTITLED_TARGET);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("kind", KIND, "offerId", offerId, "deploymentId", deploymentId,
            "customerId", actor.customerId(), "ownerId", actor.userId(), "actorId", actorId, "request", request));
        var previous = commands.findByEntitlementIdAndOperationKey(entitlement.getId(), request.operationKey()).orElse(null);
        if (previous != null) {
            if (!COMMAND_TYPE.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
                || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
            var receipt = codec.read(previous.getResultJson(), ConfigurationReceipt.class);
            if (!KIND.equals(receipt.kind()) || !consumer.getId().equals(receipt.consumerEntityId())
                || !actor.userId().equals(receipt.ownerUserId()) || !request.equals(receipt.request())
                || !deployment.getTenantId().equals(receipt.tenantId())) throw conflict(OPERATION_KEY_CONFLICT);
            requireResult(receipt.result(), consumer.getConsumerId(), actor.customerId(), deploymentId,
                association.getBootstrapTargetProfileId(), request.expectedBindingRevision());
            return receipt.result();
        }
        if (entitlement.getRevision() != request.expectedEntitlementRevision()
            || consumer.getBindingRevision() != request.expectedBindingRevision()) throw conflict(REVISION_CONFLICT);
        if (!"ACTIVE".equals(consumer.getStatus()) || consumer.getBoundDeploymentId() != null
            || consumer.getBoundReleaseId() != null || consumer.getBoundTargetProfileId() != null) throw conflict(RESOURCE_SCOPE_MISMATCH);
        requireUnusedDraft(deployment);
        requireDraftWorkspaces(actor.customerId(), consumer, deployment, offerId,
            association.getBootstrapTargetProfileId(), request, approved.definition());
        var result = consumerService.updateBindingForTrustedPromotion(actor.customerId(), consumer.getConsumerId(),
            deploymentId, null, association.getBootstrapTargetProfileId(),
            "Customer configured an existing unbound staging consumer.", request.expectedBindingRevision(), request.operationKey());
        requireResult(result, consumer.getConsumerId(), actor.customerId(), deploymentId,
            association.getBootstrapTargetProfileId(), request.expectedBindingRevision());
        var receipt = new ConfigurationReceipt(KIND, actor.userId(), consumer.getId(), deployment.getTenantId(), request, result);
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), entitlement.getId(),
            request.operationKey(), fingerprint, COMMAND_TYPE, actorId, codec.write(receipt), Instant.now()));
        return result;
    }

    private void requireDraftWorkspaces(String customerId,
            com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity consumer,
            DeploymentEntity deployment, String offerId, String targetId, Request request,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition definition) {
        String consumerEntityId = consumer.getId();
        var applications = entityManager.createNativeQuery(
            "SELECT * FROM ai_workspace_installations WHERE consumer_entity_id=?1 ORDER BY id FOR UPDATE NOWAIT",
            AIWorkspaceInstallationEntity.class).setParameter(1, consumerEntityId).getResultList();
        var actual = new java.util.TreeMap<String, Long>();
        for (Object value : applications) {
            var app = (AIWorkspaceInstallationEntity) value;
            entityManager.refresh(app);
            if (!customerId.equals(app.getCustomerId()) || !consumerEntityId.equals(app.getConsumerEntityId())
                || app.getStatus() != AIWorkspaceInstallationStatus.DRAFT || app.getActivatedAt() != null
                || app.getDisabledAt() != null || actual.put(app.getId(), app.getRowVersion()) != null)
                throw conflict(RESOURCE_SCOPE_MISMATCH);
        }
        var expected = request.expectedDraftWorkspaces().stream().collect(Collectors.toMap(
            value -> value.installationEntityId(), value -> value.expectedRowVersion()));
        if (!actual.equals(expected)) throw conflict(REVISION_CONFLICT);
        for (Object value : applications) {
            var app = (AIWorkspaceInstallationEntity) value;
            var expectation = request.expectedDraftWorkspaces().stream()
                .filter(item -> item.installationEntityId().equals(app.getId())).findFirst().orElseThrow();
            if (expectation.selectedWorkspaceProfileId() == null) {
                workspaces.authorizeDraftConfigurationForDestination(app, consumer,
                    deployment, offerId, targetId, request.operationKey());
            } else {
                var profiles = definition.workspaceProfiles().stream()
                    .filter(profile -> profile.id().equals(expectation.selectedWorkspaceProfileId())).toList();
                if (profiles.size() != 1) throw deny(SETTING_FORBIDDEN);
                workspaces.adoptReviewedDraftConfigurationForDestination(app, consumer, deployment,
                    offerId, targetId, request.operationKey(), profiles.getFirst());
            }
        }
    }

    private void requireUnusedDraft(DeploymentEntity deployment) {
        if (deployment.getArchivedAt() != null || !"DRAFT".equals(deployment.getStatus())
            || deployment.getActiveVersionId() != null || deployment.getRuntimeBaseUrl() != null
            || deployment.getConnectorBaseUrl() != null || blank(deployment.getActiveDraftId(), 64)) throw conflict(RELEASE_IN_PROGRESS);
        var drafts = entityManager.createNativeQuery("SELECT * FROM platform_deployment_drafts WHERE id=?1 FOR UPDATE NOWAIT",
            DeploymentDraftEntity.class).setParameter(1, deployment.getActiveDraftId()).getResultList();
        if (drafts.size() != 1) throw conflict(RESOURCE_SCOPE_MISMATCH);
        var draft = (DeploymentDraftEntity) drafts.getFirst(); entityManager.refresh(draft);
        if (!deployment.getId().equals(draft.getDeploymentId()) || !Set.of("DRAFT", "MODIFIED").contains(draft.getStatus()))
            throw conflict(RELEASE_IN_PROGRESS);
        for (String table : List.of("platform_deployment_versions", "platform_deployment_releases",
            "deployment_managed_dependency_graphs", "provider_usage_allocations", "platform_deployment_managed_vector_resources",
            "platform_tenant_scoped_vector_resources", "platform_vectorization_runs", "platform_vectorization_runner_sessions",
            "platform_deployment_deletion_operations",
            "platform_runtime_target_authorities", "platform_runtime_authority_snapshots", "deployment_external_integration_snapshots")) {
            if (((Number) entityManager.createNativeQuery("SELECT count(*) FROM " + table + " WHERE deployment_id=?1")
                .setParameter(1, deployment.getId()).getSingleResult()).longValue() != 0) throw conflict(RELEASE_IN_PROGRESS);
        }
        if (((Number) entityManager.createNativeQuery("""
            SELECT count(*) FROM platform_vectorization_plans WHERE deployment_id=?1
              AND (active_indexed_output_hash IS NOT NULL OR last_successful_indexed_output_hash IS NOT NULL
                   OR last_run_id IS NOT NULL OR last_successful_run_id IS NOT NULL)
            """).setParameter(1, deployment.getId()).getSingleResult()).longValue() != 0) throw conflict(RELEASE_IN_PROGRESS);
        var handles = entityManager.createQuery("SELECT h FROM DeploymentProviderResourceHandleEntity h WHERE h.deploymentId=:deployment",
            DeploymentProviderResourceHandleEntity.class).setParameter("deployment", deployment.getId()).getResultList();
        for (var handle : handles) {
            entityManager.refresh(handle);
            JsonNode meta = codec.read(handle.getMetadataJson(), JsonNode.class);
            boolean document = PreservedExternalBindingKinds.DOCUMENT_STORAGE.equals(handle.getResourceKind())
                && "CONFIGURED".equals(handle.getStatus()) && "CUSTOMER_MANAGED".equals(meta.path("storageOwnership").asText())
                && meta.path("readOnly").isBoolean() && meta.path("readOnly").booleanValue()
                && meta.path("deleteSourceOnRemoval").isBoolean() && !meta.path("deleteSourceOnRemoval").booleanValue();
            boolean vector = PreservedExternalBindingKinds.VECTOR_STORAGE.equals(handle.getResourceKind())
                && "ACTIVE".equals(handle.getStatus()) && "RETAIN_EXTERNAL".equals(meta.path("lifecycle").asText())
                && meta.path("externalResourceOwned").isBoolean() && !meta.path("externalResourceOwned").booleanValue()
                && deployment.getCustomerId().equals(meta.path("customerId").asText())
                && deployment.getTenantId().equals(meta.path("tenantId").asText())
                && deployment.getId().equals(meta.path("deploymentId").asText());
            if (handle.getReleaseId() != null || !(document || vector)) throw conflict(RELEASE_IN_PROGRESS);
        }
    }

    private void requireResult(PlatformConsumerSummary result, String consumerId, String customerId,
                               String deploymentId, String targetId, long previousRevision) {
        if (result == null || !consumerId.equals(result.consumerId()) || !customerId.equals(result.customerId())
            || !deploymentId.equals(result.boundDeploymentId()) || result.boundReleaseId() != null
            || !targetId.equals(result.boundTargetProfileId()) || result.bindingRevision() != previousRevision + 1)
            throw conflict(OPERATION_KEY_CONFLICT);
    }

    private boolean blank(String value, int max) {
        return value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl);
    }

    public record ConfigurationReceipt(String kind, String ownerUserId, String consumerEntityId, String tenantId,
                                       Request request, PlatformConsumerSummary result) { }
}
