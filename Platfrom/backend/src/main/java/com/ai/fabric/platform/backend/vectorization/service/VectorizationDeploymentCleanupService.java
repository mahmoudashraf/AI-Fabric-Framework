package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VectorizationDeploymentCleanupService {

    private final VectorizationVerificationRunRepository verificationRunRepository;
    private final VectorizationVerificationStepRepository verificationStepRepository;
    private final VectorizationRunRepository runRepository;
    private final VectorizationCheckpointRepository checkpointRepository;
    private final VectorizationFailureBucketRepository failureBucketRepository;
    private final VectorizationRunnerSessionRepository sessionRepository;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationPlanRevisionRepository revisionRepository;
    private final VectorizationPlanRepository planRepository;
    private final VectorizationSourceConnectionRepository connectionRepository;
    private final VectorizationRunnerProvisioningService runnerProvisioningService;

    public VectorizationDeploymentCleanupService(VectorizationVerificationRunRepository verificationRunRepository,
                                                VectorizationVerificationStepRepository verificationStepRepository,
                                                VectorizationRunRepository runRepository,
                                                VectorizationCheckpointRepository checkpointRepository,
                                                VectorizationFailureBucketRepository failureBucketRepository,
                                                VectorizationRunnerSessionRepository sessionRepository,
                                                VectorizationRunnerRegistrationRepository registrationRepository,
                                                VectorizationPlanRevisionRepository revisionRepository,
                                                VectorizationPlanRepository planRepository,
                                                VectorizationSourceConnectionRepository connectionRepository,
                                                VectorizationRunnerProvisioningService runnerProvisioningService) {
        this.verificationRunRepository = verificationRunRepository;
        this.verificationStepRepository = verificationStepRepository;
        this.runRepository = runRepository;
        this.checkpointRepository = checkpointRepository;
        this.failureBucketRepository = failureBucketRepository;
        this.sessionRepository = sessionRepository;
        this.registrationRepository = registrationRepository;
        this.revisionRepository = revisionRepository;
        this.planRepository = planRepository;
        this.connectionRepository = connectionRepository;
        this.runnerProvisioningService = runnerProvisioningService;
    }

    @Transactional
    public void deleteForDeployment(DeploymentEntity deployment) {
        String deploymentId = deployment.getId();
        runnerProvisioningService.clearManagedRegistrationSecret(deploymentId);

        verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).forEach(run ->
            verificationStepRepository.deleteByVerificationRunId(run.getId())
        );
        verificationRunRepository.deleteByDeploymentId(deploymentId);

        runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).forEach(run -> {
            checkpointRepository.deleteByRunId(run.getId());
            failureBucketRepository.deleteByRunId(run.getId());
        });
        runRepository.deleteByDeploymentId(deploymentId);

        sessionRepository.deleteByDeploymentId(deploymentId);
        registrationRepository.deleteByDeploymentId(deploymentId);
        revisionRepository.deleteByDeploymentId(deploymentId);
        planRepository.deleteByDeploymentId(deploymentId);
        connectionRepository.deleteByDeploymentId(deploymentId);
    }
}
