package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpStatus.CONFLICT;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;

/** Exact release receipt only. Does not authorize successor releases, offers or target profiles. */
@Service
public class EffectiveRuntimeArtifactResolver {
    private final DeploymentSourceArtifactService artifacts;
    private final RuntimeArtifactRepairJournal journal;
    private final RuntimeArtifactRepairCompatibility compatibility;
    public EffectiveRuntimeArtifactResolver(DeploymentSourceArtifactService artifacts, RuntimeArtifactRepairJournal journal,
        RuntimeArtifactRepairCompatibility compatibility) {
        this.artifacts = artifacts; this.journal = journal; this.compatibility = compatibility;
    }
    public DeploymentSourceArtifactEntity resolve(DeploymentReleaseEntity release) {
        if (release == null || release.getSourceArtifactId() == null) fail();
        var original = artifacts.require(release.getSourceArtifactId());
        var repair = verifiedRepair(release.getId());
        if (repair == null) { if (journal.hasClaim(release.getId())) fail(); return original; }
        requireRelease(release, repair);
        var replacement = artifacts.require(repair.replacement().artifactId());
        if (!compatibility.pin(original).equals(repair.original()) || !compatibility.pin(replacement).equals(repair.replacement())) fail();
        compatibility.requireCompatible(original, replacement);
        return replacement;
    }
    public VerifiedRepair verifiedRepair(String releaseId) { return journal.verifiedRepair(releaseId); }
    public boolean permitsRuntimeJob(String releaseId, String appUuid, String jobUuid, String repository, String tag,
        Instant createdAt, Instant finishedAt, String status) {
        var proof = verifiedRepair(releaseId);
        return proof != null && proof.applicationUuid().equals(appUuid) && proof.deploymentJobUuid().equals(jobUuid)
            && proof.replacement().repository().equals(repository)
            && ("sha256-" + proof.replacement().digest().substring(7)).equals(tag)
            && proof.jobCreatedAt().equals(createdAt) && proof.jobFinishedAt().equals(finishedAt) && "finished".equals(status);
    }
    public boolean permitsConnectorJob(String releaseId, String appUuid, String jobUuid, String repository, String tag,
        Instant createdAt, Instant finishedAt, String status) {
        var proof = verifiedRepair(releaseId);
        return proof != null && proof.connectorJob() != null && proof.scope().connectorApplicationUuid().equals(appUuid)
            && proof.connectorJob().applicationUuid().equals(appUuid) && proof.connectorJob().uuid().equals(jobUuid)
            && proof.connectorJob().createdAt().equals(createdAt) && proof.connectorJob().finishedAt().equals(finishedAt)
            && "finished".equals(status) && proof.original().repository().equals(repository)
            && ("sha256-" + proof.original().digest().substring(7)).equals(tag);
    }
    private static void requireRelease(DeploymentReleaseEntity release, VerifiedRepair repair) {
        var scope = repair.scope();
        if (!Objects.equals(release.getId(), scope.releaseId()) || !Objects.equals(release.getDeploymentId(), scope.deploymentId())
            || !Objects.equals(release.getTargetProfileId(), scope.targetProfileId())
            || !Objects.equals(release.getDeploymentVersionId(), scope.versionId())
            || !Objects.equals(release.getRuntimeAuthorityFingerprint(), scope.authorityFingerprint())
            || !Objects.equals(release.getSourceArtifactId(), repair.original().artifactId())) fail();
    }
    private static void fail() { throw new AdmissionFailure(CONFLICT, "VERIFIED_RUNTIME_ARTIFACT_REPAIR_REQUIRED"); }
}
