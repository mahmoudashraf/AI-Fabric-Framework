package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Free/Serverless support only default. Full visibility is an explicit staff review, not inferred ACL. */
@Component
public class ExternalVectorClusterReadback {
    private final PlatformSecretService secrets;
    private final ZillizCloudControlPlaneClient client;
    private final UnusedVectorTransferJson json;
    public ExternalVectorClusterReadback(PlatformSecretService secrets, ZillizCloudControlPlaneClient client, UnusedVectorTransferJson json) {
        this.secrets = secrets; this.client = client; this.json = json;
    }
    public com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.ClusterReadback requireEmpty(VectorIdentity vector, InventoryApproval review) {
        String key = secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY");
        if (key == null || key.isBlank() || review == null || !json.bytesHash(key).equals(review.expectedCredentialFingerprint())
            || !text(review.fullVisibilityReviewReference()) || !text(review.noWriterWindowReference())
            || !List.of("Free", "Serverless").contains(vector.deploymentOption()) || !"default".equals(vector.databaseName())) throw invalid();
        ZillizCloudControlPlaneClient.ZillizClusterSummary observed;
        try { observed = client.getCluster(vector.clusterId(), key); }
        catch (RuntimeException ignored) { throw invalid(); }
        if (observed == null || !vector.clusterId().equals(observed.clusterId()) || !vector.projectId().equals(observed.projectId())
            || !vector.regionId().equals(observed.regionId()) || !vector.deploymentOption().equals(observed.deploymentOption())
            || !"RUNNING".equalsIgnoreCase(observed.status()) || !("https://" + vector.endpointHost()).equals(observed.connectAddress())) throw invalid();
        List<String> collections;
        try { collections = client.listServingCollections(observed.connectAddress(), key); }
        catch (RuntimeException ignored) { throw invalid(); }
        if (collections == null || !collections.isEmpty()) throw invalid();
        String scope = json.hash(Map.of("vector", vector, "credentialFingerprint", review.expectedCredentialFingerprint(),
            "fullVisibilityReviewReference", review.fullVisibilityReviewReference(), "noWriterWindowReference", review.noWriterWindowReference()));
        var unsigned = new ClusterReadback(vector.endpointHost(), Map.of("default", List.of()), review.expectedCredentialFingerprint(),
            review.fullVisibilityReviewReference(), review.noWriterWindowReference(), scope, Instant.now().truncatedTo(ChronoUnit.MICROS), null);
        return new ClusterReadback(unsigned.endpointHost(), unsigned.collectionsByDatabase(), unsigned.credentialFingerprint(),
            unsigned.fullVisibilityReviewReference(), unsigned.noWriterWindowReference(), unsigned.scopeReviewDigest(), unsigned.observedAt(), json.receiptHash(unsigned));
    }
    private boolean text(String s) { return s != null && !s.isBlank() && s.length() <= 255 && s.equals(s.trim()) && s.chars().noneMatch(Character::isISOControl); }
    private ResponseStatusException invalid() { return new ResponseStatusException(PRECONDITION_FAILED, "UNUSED_VECTOR_TRANSFER_EMPTY_INVENTORY_UNPROVEN"); }
}
