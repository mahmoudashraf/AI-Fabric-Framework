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
    public TargetRetirementGateService(DeploymentRepository deployments, TargetRetirementGateRepository gates) {
        this.deployments = deployments;
        this.gates = gates;
    }

    /** Call in the SAME short transaction as every activation/rebind/apply/resource-generation mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long requireAvailableForMutation(String deploymentId, String targetProfileId) {
        var gate = lock(deploymentId, targetProfileId);
        if (!"AVAILABLE".equals(gate.getState())) {
            throw new ResponseStatusException(CONFLICT, "TARGET_RETIREMENT_FENCED");
        }
        gate.setEpoch(gate.getEpoch() + 1);
        gate.setUpdatedAt(Instant.now());
        gates.save(gate);
        return gate.getEpoch();
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
