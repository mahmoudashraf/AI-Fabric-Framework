package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffectiveRuntimeArtifactResolverTest {
    final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    final RuntimeArtifactRepairJournal journal = mock(RuntimeArtifactRepairJournal.class);
    final RuntimeArtifactRepairCompatibility compatibility = mock(RuntimeArtifactRepairCompatibility.class);
    final EffectiveRuntimeArtifactResolver resolver = new EffectiveRuntimeArtifactResolver(artifacts, journal, compatibility);
    @Test void noRepairStillUsesOnlyTheOriginalPinnedArtifact() {
        var release = release(); var original = new DeploymentSourceArtifactEntity(); when(artifacts.require("original")).thenReturn(original);
        assertThat(resolver.resolve(release)).isSameAs(original); verify(artifacts).require("original"); verifyNoMoreInteractions(artifacts);
    }
    @Test void claimedButUnverifiedRepairCannotFallBackToOriginal() {
        when(journal.hasClaim("release")).thenReturn(true);
        assertThatThrownBy(() -> resolver.resolve(release())).hasMessageContaining("VERIFIED_RUNTIME_ARTIFACT_REPAIR_REQUIRED");
    }
    @Test void verifiedReceiptAuthorizesOnlyItsExactScopeAndImmutablePins() {
        var proof = proof(); var original = new DeploymentSourceArtifactEntity(); var replacement = new DeploymentSourceArtifactEntity();
        when(artifacts.require("original")).thenReturn(original); when(artifacts.require("replacement")).thenReturn(replacement);
        when(compatibility.pin(original)).thenReturn(proof.original()); when(compatibility.pin(replacement)).thenReturn(proof.replacement());
        when(journal.verifiedRepair("release")).thenReturn(proof);
        assertThat(resolver.resolve(release())).isSameAs(replacement);
        var changed = release(); changed.setTargetProfileId("other-target");
        assertThatThrownBy(() -> resolver.resolve(changed)).isInstanceOf(AdmissionFailure.class);
        when(compatibility.pin(replacement)).thenReturn(proof.original());
        assertThatThrownBy(() -> resolver.resolve(release())).isInstanceOf(AdmissionFailure.class);
    }
    @Test void runtimeAndConnectorExceptionsRequireTheirExactIndividualJobsAndSources() {
        var proof = proof(); when(journal.verifiedRepair("release")).thenReturn(proof);
        assertThat(resolver.permitsRuntimeJob("release", "runtime-app", "runtime-job", "reviewed/runtime", "sha256-" + "b".repeat(64), Instant.EPOCH, Instant.EPOCH, "finished")).isTrue();
        assertThat(resolver.permitsRuntimeJob("release", "runtime-app", "other", "reviewed/runtime", "sha256-" + "b".repeat(64), Instant.EPOCH, Instant.EPOCH, "finished")).isFalse();
        assertThat(resolver.permitsConnectorJob("release", "connector-app", "connector-job", "reviewed/runtime", "sha256-" + "a".repeat(64), Instant.EPOCH, Instant.EPOCH, "finished")).isTrue();
        assertThat(resolver.permitsConnectorJob("release", "connector-app", "runtime-job", "reviewed/runtime", "sha256-" + "a".repeat(64), Instant.EPOCH, Instant.EPOCH, "finished")).isFalse();
        assertThat(resolver.permitsConnectorJob("release", "connector-app", "connector-job", "reviewed/runtime", "sha256-" + "b".repeat(64), Instant.EPOCH, Instant.EPOCH, "finished")).isFalse();
    }
    @Test void verifiedColdSameImageReceiptKeepsItsModeThroughEffectiveArtifactSelection() {
        var old = proof();
        var proof = new VerifiedRepair(old.planId(), old.planFingerprint(), old.scope(), old.original(), old.original(),
            old.applicationUuid(), old.deploymentJobUuid(), old.jobCreatedAt(), old.jobFinishedAt(), old.connectorJob(), old.wallet(),
            old.finalLocalFingerprints(), Map.of("coldMaintenanceReceipt", "sealed-runtime-cold-receipt"), old.verifiedAt());
        var original = new DeploymentSourceArtifactEntity();
        when(artifacts.require("original")).thenReturn(original);
        when(compatibility.pin(original)).thenReturn(proof.original());
        when(journal.verifiedRepair("release")).thenReturn(proof);
        assertThat(resolver.resolve(release())).isSameAs(original);
        verify(compatibility).requireCompatible(original, original, Mode.COLD_MAINTENANCE_REAPPLY);
    }
    @Test void successorReleaseReceivesNoImplicitApproval() {
        var successor = release(); successor.setId("successor"); when(journal.hasClaim("successor")).thenReturn(false);
        var original = new DeploymentSourceArtifactEntity(); when(artifacts.require("original")).thenReturn(original);
        when(journal.verifiedRepair("release")).thenReturn(proof());
        assertThat(resolver.resolve(successor)).isSameAs(original);
    }
    private DeploymentReleaseEntity release() {
        var r = new DeploymentReleaseEntity(); r.setId("release"); r.setDeploymentId("deployment1"); r.setTargetProfileId(AdmissionFixture.TARGET);
        r.setDeploymentVersionId("version"); r.setRuntimeAuthorityFingerprint("authority"); r.setSourceArtifactId("original"); return r;
    }
    private VerifiedRepair proof() {
        var scope = new Scope("customer", "tenant", "deployment1", AdmissionFixture.TARGET, "release", "version", "config", "authority", "reservation", "generation", "https://runtime.example.test", "runtime-app", "connector-app", "runtime-pg");
        var original = new ArtifactPin("original", "old", "reviewed/runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "old-ci", "staging", "old-manifest");
        var replacement = new ArtifactPin("replacement", "new", "reviewed/runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "new-ci", "staging", "new-manifest");
        return new VerifiedRepair("plan", "plan-hash", scope, original, replacement, "runtime-app", "runtime-job", Instant.EPOCH, Instant.EPOCH,
            new NativeJob("connector-job", "connector-app", "finished", Instant.EPOCH, Instant.EPOCH), null, Map.of(), Map.of(), Instant.EPOCH);
    }
}
