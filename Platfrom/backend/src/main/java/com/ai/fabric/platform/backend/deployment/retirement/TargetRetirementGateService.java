package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TargetRetirementGateService {
    private final DeploymentRepository deployments;
    private final TargetRetirementGateRepository gates;
    private final TargetRetirementOperationRepository operations;
    public TargetRetirementGateService(DeploymentRepository deployments, TargetRetirementGateRepository gates,
                                       TargetRetirementOperationRepository operations) {
        this.deployments = deployments;
        this.gates = gates;
        this.operations = operations;
    }

    /** Call in the SAME short transaction as every activation/rebind/apply/resource-generation mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long requireAvailableForMutation(String deploymentId, String targetProfileId) {
        var gate = lock(deploymentId, targetProfileId);
        requireAvailable(deploymentId, targetProfileId, gate);
        gate.setEpoch(gate.getEpoch() + 1);
        gate.setUpdatedAt(Instant.now());
        gates.save(gate);
        return gate.getEpoch();
    }

    /** A committed command replay must respect retirement without claiming a new mutation epoch. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForReplay(String deploymentId, String targetProfileId) {
        deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        var gate = gates.findForUpdate(deploymentId, targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "TARGET_RETIREMENT_FENCE_MISSING"));
        requireAvailable(deploymentId, targetProfileId, gate);
    }

    private void requireAvailable(String deploymentId, String targetProfileId, TargetRetirementGateEntity gate) {
        if (!"AVAILABLE".equals(gate.getState())) {
            throw new ResponseStatusException(CONFLICT, "TARGET_RETIREMENT_FENCED");
        }
        boolean plannedOrRunning = operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(
                deploymentId, targetProfileId).stream()
            .anyMatch(operation -> operation.getState() != TargetRetirementContracts.State.COMPLETE
                && operation.getState() != TargetRetirementContracts.State.CANCELLED);
        if (plannedOrRunning) throw new ResponseStatusException(CONFLICT, "TARGET_RETIREMENT_FENCED");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TargetRetirementGateEntity lock(String deploymentId, String targetProfileId) {
        // The deployment row serializes creation of a previously absent target gate.
        deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        return gates.findForUpdate(deploymentId, targetProfileId).orElseGet(() -> {
            var gate = new TargetRetirementGateEntity();
            gate.setId(UUID.randomUUID().toString());
            gate.setDeploymentId(deploymentId);
            gate.setTargetProfileId(targetProfileId);
            gate.setState("AVAILABLE");
            gate.setUpdatedAt(Instant.now());
            return gates.saveAndFlush(gate);
        });
    }
}
