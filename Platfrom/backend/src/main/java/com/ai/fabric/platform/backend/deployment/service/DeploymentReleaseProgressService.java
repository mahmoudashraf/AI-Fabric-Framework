package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Iterator;

@Service
public class DeploymentReleaseProgressService {

    private final DeploymentReleaseRepository releaseRepository;
    private final ObjectMapper objectMapper;

    public DeploymentReleaseProgressService(DeploymentReleaseRepository releaseRepository,
                                            ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void transition(String releaseId,
                           String status,
                           String provisioningStatus,
                           String verificationStatus,
                           String stepKey,
                           String stepDescription,
                           String errorMessage) {
        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setStatus(status);
        release.setProvisioningStatus(provisioningStatus);
        release.setVerificationStatus(verificationStatus);
        release.setCurrentStepKey(stepKey);
        release.setCurrentStepDescription(stepDescription);
        release.setErrorMessage(errorMessage);
        release.setUpdatedAt(Instant.now());
        appendOrUpdateStep(release, stepKey, stepDescription, deriveStepStatus(status, verificationStatus, errorMessage), errorMessage);
        releaseRepository.save(release);
    }

    @Transactional
    public void stepStarted(String releaseId, String stepKey, String stepDescription) {
        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setCurrentStepKey(stepKey);
        release.setCurrentStepDescription(stepDescription);
        release.setErrorMessage(null);
        release.setUpdatedAt(Instant.now());
        appendOrUpdateStep(release, stepKey, stepDescription, "RUNNING", null);
        releaseRepository.save(release);
    }

    @Transactional
    public void stepCompleted(String releaseId, String stepKey, String stepDescription) {
        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setCurrentStepKey(stepKey);
        release.setCurrentStepDescription(stepDescription);
        release.setUpdatedAt(Instant.now());
        appendOrUpdateStep(release, stepKey, stepDescription, "COMPLETED", null);
        releaseRepository.save(release);
    }

    @Transactional
    public void stepFailed(String releaseId, String stepKey, String stepDescription, String errorMessage) {
        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setCurrentStepKey(stepKey);
        release.setCurrentStepDescription(stepDescription);
        release.setErrorMessage(errorMessage);
        release.setUpdatedAt(Instant.now());
        appendOrUpdateStep(release, stepKey, stepDescription, "FAILED", errorMessage);
        releaseRepository.save(release);
    }

    @Transactional
    public void mergeProvisioningDetails(String releaseId, String detailsJson) {
        DeploymentReleaseEntity release = getRelease(releaseId);
        ObjectNode merged = ensureObject(release.getProvisioningDetailsJson());
        ObjectNode incoming = ensureObject(detailsJson);

        Iterator<String> fieldNames = incoming.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode value = incoming.get(field);
            if (!"progress".equals(field)) {
                merged.set(field, value);
            }
        }

        ObjectNode progress = ensureProgressNode(merged);
        ObjectNode existingProgress = ensureProgressNode(ensureObject(release.getProvisioningDetailsJson()));
        progress.set("steps", existingProgress.path("steps").deepCopy());
        merged.set("progress", progress);

        release.setProvisioningDetailsJson(writeJson(merged));
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);
    }

    public ProvisioningProgressTracker tracker(String releaseId) {
        return new ProvisioningProgressTracker() {
            @Override
            public void stepStarted(String key, String description) {
                DeploymentReleaseProgressService.this.stepStarted(releaseId, key, description);
            }

            @Override
            public void stepCompleted(String key, String description) {
                DeploymentReleaseProgressService.this.stepCompleted(releaseId, key, description);
            }

            @Override
            public void stepFailed(String key, String description, String errorMessage) {
                DeploymentReleaseProgressService.this.stepFailed(releaseId, key, description, errorMessage);
            }
        };
    }

    private void appendOrUpdateStep(DeploymentReleaseEntity release,
                                    String stepKey,
                                    String stepDescription,
                                    String status,
                                    String errorMessage) {
        if (stepKey == null || stepKey.isBlank()) {
            return;
        }
        ObjectNode root = ensureObject(release.getProvisioningDetailsJson());
        ObjectNode progress = ensureProgressNode(root);
        ArrayNode steps = progress.withArray("steps");
        ObjectNode existing = null;
        for (JsonNode node : steps) {
            if (node.isObject() && stepKey.equals(node.path("key").asText())) {
                existing = (ObjectNode) node;
                break;
            }
        }
        Instant now = Instant.now();
        if (existing == null) {
            existing = objectMapper.createObjectNode();
            existing.put("key", stepKey);
            existing.put("description", stepDescription);
            existing.put("status", status);
            existing.put("startedAt", now.toString());
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                existing.put("completedAt", now.toString());
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                existing.put("errorMessage", errorMessage);
            }
            steps.add(existing);
        } else {
            existing.put("description", stepDescription);
            existing.put("status", status);
            if (existing.path("startedAt").asText("").isBlank()) {
                existing.put("startedAt", now.toString());
            }
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                existing.put("completedAt", now.toString());
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                existing.put("errorMessage", errorMessage);
            } else {
                existing.remove("errorMessage");
            }
        }

        progress.put("currentStepKey", stepKey);
        progress.put("currentStepDescription", stepDescription);
        progress.put("currentStepStatus", status);
        if (errorMessage != null && !errorMessage.isBlank()) {
            progress.put("errorMessage", errorMessage);
        } else {
            progress.remove("errorMessage");
        }
        root.set("progress", progress);
        release.setProvisioningDetailsJson(writeJson(root));
    }

    private String deriveStepStatus(String status, String verificationStatus, String errorMessage) {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return "FAILED";
        }
        if ("APPLIED_VERIFIED".equals(status) || "APPLIED_VERIFICATION_FAILED".equals(status)) {
            return "COMPLETED";
        }
        if ("VERIFYING".equals(status) || "PROVISIONING".equals(status) || "APPLY_REQUESTED".equals(status)) {
            return "RUNNING";
        }
        if ("PASSED".equals(verificationStatus)) {
            return "COMPLETED";
        }
        return "RUNNING";
    }

    private ObjectNode ensureProgressNode(ObjectNode root) {
        JsonNode progressNode = root.path("progress");
        ObjectNode progress = progressNode.isObject() ? (ObjectNode) progressNode.deepCopy() : objectMapper.createObjectNode();
        if (!progress.has("steps") || !progress.path("steps").isArray()) {
            progress.set("steps", objectMapper.createArrayNode());
        }
        return progress;
    }

    private ObjectNode ensureObject(String value) {
        try {
            if (value == null || value.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(value);
            return node.isObject() ? (ObjectNode) node.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse provisioning details JSON.", ex);
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write provisioning details JSON.", ex);
        }
    }

    private DeploymentReleaseEntity getRelease(String releaseId) {
        return releaseRepository.findById(releaseId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Release not found: " + releaseId
            ));
    }
}
