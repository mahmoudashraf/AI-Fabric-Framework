package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AIWorkspaceBindingGuard {

    private final AIWorkspaceInstallationRepository repository;
    private final AIWorkspaceReadinessService readinessService;

    public AIWorkspaceBindingGuard(AIWorkspaceInstallationRepository repository,
                                   AIWorkspaceReadinessService readinessService) {
        this.repository = repository;
        this.readinessService = readinessService;
    }

    public void validateRebind(PlatformConsumerEntity consumer,
                               String deploymentId,
                               String releaseId) {
        var active = repository.findByConsumerEntityIdAndStatusIn(
            consumer.getId(), List.of(AIWorkspaceInstallationStatus.ACTIVE));
        if (active.isEmpty()) return;
        if (deploymentId == null || deploymentId.isBlank() || releaseId == null || releaseId.isBlank()) {
            throw new ResponseStatusException(CONFLICT,
                "Disable active AI Workspace installations before unbinding this consumer.");
        }
        for (var installation : active) {
            var readiness = readinessService.evaluateAgainst(installation, consumer, deploymentId, releaseId);
            if (!readiness.ready()) {
                String blockers = readiness.checks().stream()
                    .filter(check -> "BLOCKED".equals(check.status()))
                    .map(check -> check.code() + ": " + check.message())
                    .limit(3)
                    .collect(java.util.stream.Collectors.joining("; "));
                throw new ResponseStatusException(CONFLICT,
                    "Target binding is incompatible with active AI Workspace "
                        + installation.getInstallationId() + ". " + blockers);
            }
        }
    }

    public void validateConsumerStatusChange(PlatformConsumerEntity consumer, String nextStatus) {
        if ("ACTIVE".equalsIgnoreCase(nextStatus)) return;
        var active = repository.findByConsumerEntityIdAndStatusIn(
            consumer.getId(), List.of(AIWorkspaceInstallationStatus.ACTIVE));
        if (!active.isEmpty()) {
            throw new ResponseStatusException(CONFLICT,
                "Disable active AI Workspace installations before disabling this consumer.");
        }
    }

    public void validateConsumerDeletion(PlatformConsumerEntity consumer) {
        if (repository.existsByConsumerEntityId(consumer.getId())) {
            throw new ResponseStatusException(CONFLICT,
                "This consumer has AI Workspace installation history and cannot be deleted.");
        }
    }
}
