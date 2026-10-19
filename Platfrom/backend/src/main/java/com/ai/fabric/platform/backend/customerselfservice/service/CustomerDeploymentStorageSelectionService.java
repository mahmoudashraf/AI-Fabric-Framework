package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerDeploymentStorageSelectionService {
    private final DeploymentDraftRepository drafts;
    private final ObjectMapper mapper;

    public CustomerDeploymentStorageSelectionService(DeploymentDraftRepository drafts, ObjectMapper mapper) {
        this.drafts = drafts;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(DeploymentEntity deployment, String mode) {
        try {
            AiStateStorageMode.valueOf(mode);
        } catch (RuntimeException error) {
            throw invalid();
        }
        if (deployment.getActiveVersionId() != null || !"DRAFT".equals(deployment.getStatus())
            || deployment.getActiveDraftId() == null) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        var draft = drafts.findByIdForUpdate(deployment.getActiveDraftId())
            .filter(value -> deployment.getId().equals(value.getDeploymentId())
                && ("DRAFT".equals(value.getStatus()) || "MODIFIED".equals(value.getStatus())))
            .orElseThrow(() -> conflict(REVIEWED_COMPOSITION_CHANGED));
        ObjectNode provider;
        try {
            var node = mapper.readTree(draft.getProviderConfigJson());
            if (!(node instanceof ObjectNode object)) throw invalid();
            provider = object;
        } catch (Exception error) {
            throw invalid();
        }
        if (provider.has("aiStateStorageMode") && !mode.equals(provider.path("aiStateStorageMode").asText())) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        provider.put("aiStateStorageMode", mode);
        draft.setProviderConfigJson(provider.toString());
        draft.setUpdatedAt(Instant.now());
        drafts.saveAndFlush(draft);
    }
}
