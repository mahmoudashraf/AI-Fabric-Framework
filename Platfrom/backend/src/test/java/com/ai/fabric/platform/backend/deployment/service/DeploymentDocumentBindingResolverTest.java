package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentDocumentBindingResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    private final DeploymentDocumentBindingResolver resolver = new DeploymentDocumentBindingResolver(handles, secrets, mapper);

    @Test
    void sameReviewedSlotResolvesToDifferentTargetResourcesWithoutChangingComposition() {
        var staging = handle("storage-stage", "stage");
        var production = handle("storage-prod", "production");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage")).thenReturn(List.of(staging));
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "production")).thenReturn(List.of(production));
        assertThat(resolver.resolve("dep-a", "stage", DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT)).isSameAs(staging);
        assertThat(resolver.resolve("dep-a", "production", DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT)).isSameAs(production);
    }

    @Test
    void missingOrAmbiguousSlotNeverUsesLatestBinding() {
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage"))
            .thenReturn(List.of()).thenReturn(List.of(handle("one", "stage"), handle("two", "stage")));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resolver.resolve("dep-a", "stage", DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("exactly one");
        }
        verify(handles, never()).findTopByDeploymentIdOrderByUpdatedAtDesc(anyString());
    }

    @Test
    void physicalReferenceCannotCrossTargetOrCustomerAndRetiredBindingCannotBeUsed() {
        var handle = handle("storage-stage", "stage");
        when(handles.findById(handle.getId())).thenReturn(Optional.of(handle));
        assertThatThrownBy(() -> resolver.resolve("dep-a", "production", handle.getId())).hasMessageContaining("ownership, target");
        assertThatThrownBy(() -> resolver.resolve("dep-b", "stage", handle.getId())).hasMessageContaining("ownership, target");
        handle.setStatus("RETIRED");
        assertThatThrownBy(() -> resolver.resolve("dep-a", "stage", handle.getId())).hasMessageContaining("lifecycle");
    }

    @Test
    void unsupportedSlotAndAbsentTargetFailClosed() {
        assertThatThrownBy(() -> resolver.resolve("dep-a", "stage", "slot:arbitrary-source")).hasMessageContaining("not supported");
        assertThatThrownBy(() -> resolver.resolve("dep-a", null, "storage-stage")).hasMessageContaining("explicit target");
        verifyNoInteractions(handles);
    }

    @Test
    void releaseFreezesSafeEvidenceAndRejectsStorageOrSecretRevisionDrift() throws Exception {
        var deployment = deployment();
        var version = version();
        var release = release();
        var handle = handle("storage-stage", "stage");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage")).thenReturn(List.of(handle));
        var secret = credentials("stage");
        resolver.freeze(deployment, version, release);
        assertThat(release.getDocumentBindingFingerprint()).hasSize(64);
        assertThat(release.getDocumentBindingSnapshotJson()).contains("secretRevisions", "storage-stage")
            .doesNotContain("very-private-secret", "approved/policies");
        resolver.requireUnchanged(deployment, version, release);
        secret.setUpdatedAt(Instant.parse("2026-10-14T01:01:00Z"));
        assertThatThrownBy(() -> resolver.requireUnchanged(deployment, version, release)).hasMessageContaining("changed");
        secret.setUpdatedAt(Instant.parse("2026-10-14T01:00:00Z"));
        handle.setMetadataJson("{\"connectorType\":\"S3_COMPATIBLE_OBJECT_STORAGE\",\"bindingRevision\":2}");
        assertThatThrownBy(() -> resolver.requireUnchanged(deployment, version, release)).hasMessageContaining("changed");
    }

    @Test
    void fingerprintsDoNotDependOnJsonObjectFieldOrder() throws Exception {
        assertThat(resolver.fingerprint(mapper.readTree("{\"a\":1,\"nested\":{\"b\":2,\"c\":3}}")))
            .isEqualTo(resolver.fingerprint(mapper.readTree("{\"nested\":{\"c\":3,\"b\":2},\"a\":1}")));
    }

    @Test
    void differentTargetSnapshotsHaveDistinctFingerprints() {
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage"))
            .thenReturn(List.of(handle("storage-stage", "stage")));
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "production"))
            .thenReturn(List.of(handle("storage-prod", "production")));
        credentials("stage");
        credentials("production");
        assertThat(resolver.fingerprint(resolver.snapshot(deployment(), version(), "stage")))
            .isNotEqualTo(resolver.fingerprint(resolver.snapshot(deployment(), version(), "production")));
    }

    @Test
    void oneImmutableVersionFreezesBothTargetsAndStagingDriftDoesNotChangeProduction() {
        var staging = handle("storage-stage", "stage");
        var production = handle("storage-prod", "production");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage")).thenReturn(List.of(staging));
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "production")).thenReturn(List.of(production));
        credentials("stage"); credentials("production");
        var version = version();
        var stageRelease = release();
        var prodRelease = release(); prodRelease.setTargetProfileId("production");
        resolver.freeze(deployment(), version, stageRelease);
        resolver.freeze(deployment(), version, prodRelease);
        assertThat(stageRelease.getDocumentBindingSnapshotJson()).contains("storage-stage");
        assertThat(prodRelease.getDocumentBindingSnapshotJson()).contains("storage-prod");
        assertThat(version.getMarketplaceDatasetConfigJson()).contains("slot:document-storage").doesNotContain("storage-stage", "storage-prod");
        staging.setMetadataJson("{\"connectorType\":\"S3_COMPATIBLE_OBJECT_STORAGE\",\"bindingRevision\":2}");
        assertThatThrownBy(() -> resolver.requireUnchanged(deployment(), version, stageRelease)).hasMessageContaining("changed");
        resolver.requireUnchanged(deployment(), version, prodRelease);
    }

    @Test
    void missingCredentialsOrConnectorChangeBlocksFreeze() {
        var handle = handle("storage-stage", "stage");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage")).thenReturn(List.of(handle));
        assertThatThrownBy(() -> resolver.freeze(deployment(), version(), release())).hasMessageContaining("credentials are incomplete");
        handle.setMetadataJson("{\"connectorType\":\"MOUNTED_FOLDER\"}");
        assertThatThrownBy(() -> resolver.freeze(deployment(), version(), release())).hasMessageContaining("reviewed composition");
    }

    @Test
    void missingFrozenEvidenceCannotAuthorizeDocumentOperations() {
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep-a", "stage"))
            .thenReturn(List.of(handle("storage-stage", "stage")));
        credentials("stage");
        assertThatThrownBy(() -> resolver.requireUnchanged(deployment(), version(), release())).hasMessageContaining("lacks frozen evidence");
    }

    private PlatformSecretEntity credentials(String target) {
        var secret = new PlatformSecretEntity();
        secret.setSecretValue("very-private-secret");
        secret.setUpdatedAt(Instant.parse("2026-10-14T01:00:00Z"));
        String key = DeploymentDocumentStorageBindingService.secretKey("dep-a", target);
        for (String suffix : List.of("ENDPOINT", "REGION", "BUCKET", "ACCESS_KEY", "SECRET_KEY")) {
            when(secrets.findById(key + "_" + suffix)).thenReturn(Optional.of(secret));
        }
        return secret;
    }

    private DeploymentProviderResourceHandleEntity handle(String id, String target) {
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId(id); handle.setDeploymentId("dep-a"); handle.setTargetProfileId(target);
        handle.setResourceKind(DeploymentDocumentStorageBindingService.RESOURCE_KIND); handle.setStatus("CONFIGURED");
        handle.setMetadataJson("{\"connectorType\":\"S3_COMPATIBLE_OBJECT_STORAGE\",\"bindingRevision\":1}");
        return handle;
    }
    private DeploymentEntity deployment() {
        var value = new DeploymentEntity(); value.setId("dep-a"); return value;
    }
    private DeploymentVersionEntity version() {
        var value = new DeploymentVersionEntity(); value.setId("ver-a"); value.setDeploymentId("dep-a");
        value.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"ingestionMode":"EXTERNAL_DOCUMENT_STORAGE","sourceConnector":{
              "bindingRef":"slot:document-storage","connectorType":"S3_COMPATIBLE_OBJECT_STORAGE"}}]}
            """);
        return value;
    }
    private DeploymentReleaseEntity release() {
        var value = new DeploymentReleaseEntity(); value.setTargetProfileId("stage"); return value;
    }
}
