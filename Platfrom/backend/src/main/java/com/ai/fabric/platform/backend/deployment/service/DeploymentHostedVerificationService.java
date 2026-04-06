package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDiagnosticsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentHostedVerificationService {

    private static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");

    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentHostedVerificationRunRepository runRepository;
    private final DeploymentHostedVerificationContextService contextService;
    private final DeploymentHostedVerificationExecutionService executionService;
    private final DeploymentVerificationRolloutService deploymentVerificationRolloutService;
    private final PlatformAuditService platformAuditService;
    private final DeploymentHostedVerificationLogParser logParser;

    public DeploymentHostedVerificationService(DeploymentAccessService deploymentAccessService,
                                               DeploymentRepository deploymentRepository,
                                               DeploymentHostedVerificationRunRepository runRepository,
                                               DeploymentHostedVerificationContextService contextService,
                                               DeploymentHostedVerificationExecutionService executionService,
                                               DeploymentVerificationRolloutService deploymentVerificationRolloutService,
                                               PlatformAuditService platformAuditService,
                                               DeploymentHostedVerificationLogParser logParser) {
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentRepository = deploymentRepository;
        this.runRepository = runRepository;
        this.contextService = contextService;
        this.executionService = executionService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
        this.platformAuditService = platformAuditService;
        this.logParser = logParser;
    }

    public List<DeploymentHostedVerificationRunSummary> listRuns(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentAccess(deployment);
        return runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .map(this::toSummary)
            .toList();
    }

    public DeploymentHostedVerificationDispatchSummary dispatch(String deploymentId,
                                                                DeploymentHostedVerificationDispatchRequest request) {
        boolean verifyWrite = request != null && Boolean.TRUE.equals(request.verifyWrite());
        if (verifyWrite && !deploymentVerificationRolloutService.isCanonicalRolloutDeployment(deploymentId)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Write-enabled hosted verification is restricted to canonical verification rollout deployments."
            );
        }
        DeploymentHostedVerificationContextSummary context = contextService.buildContextForOperator(
            deploymentId,
            request == null ? null : request.profile(),
            verifyWrite
        );
        if (runRepository.existsByDeploymentIdAndStatusIn(deploymentId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(CONFLICT, "A hosted verification run is already queued or running for this deployment.");
        }

        DeploymentHostedVerificationRunEntity run = new DeploymentHostedVerificationRunEntity();
        run.setId(generateId());
        run.setDeploymentId(context.deploymentId());
        run.setReleaseId(context.releaseId());
        run.setDeploymentVersionId(context.deploymentVersionId());
        run.setVerificationProfile(context.profile());
        run.setRunnerType("PLATFORM_HOSTED");
        run.setScriptPath(context.script());
        run.setStatus("QUEUED");
        run.setVerifyWrite(context.verifyWrite());
        run.setSummaryMessage("Hosted verification is queued on the platform deployment.");
        run.setLogOutput("");
        run.setCreatedAt(Instant.now());
        runRepository.save(run);

        platformAuditService.record(
            "HOSTED_VERIFICATION_DISPATCHED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "runId", run.getId(),
                "releaseId", run.getReleaseId(),
                "deploymentVersionId", run.getDeploymentVersionId(),
                "profile", run.getVerificationProfile()
            )
        );
        executionService.execute(run.getId());
        return new DeploymentHostedVerificationDispatchSummary(
            run.getDeploymentId(),
            run.getReleaseId(),
            run.getDeploymentVersionId(),
            run.getVerificationProfile(),
            run.isVerifyWrite(),
            run.getSummaryMessage(),
            toSummary(run)
        );
    }

    public DeploymentHostedVerificationContextSummary getContext(String deploymentId, String profile, boolean verifyWrite) {
        return contextService.buildContextForOperator(deploymentId, profile, verifyWrite);
    }

    private DeploymentHostedVerificationRunSummary toSummary(DeploymentHostedVerificationRunEntity run) {
        DeploymentHostedVerificationDiagnosticsSummary diagnostics = logParser.parse(run.getLogOutput());
        return new DeploymentHostedVerificationRunSummary(
            run.getId(),
            run.getDeploymentId(),
            run.getReleaseId(),
            run.getDeploymentVersionId(),
            run.getVerificationProfile(),
            run.getRunnerType(),
            run.getScriptPath(),
            run.getStatus(),
            run.isVerifyWrite(),
            run.getSummaryMessage(),
            run.getLogOutput(),
            diagnostics,
            run.getExitCode(),
            run.getCreatedAt(),
            run.getStartedAt(),
            run.getCompletedAt()
        );
    }

    private String generateId() {
        return "hvr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
