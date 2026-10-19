package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalVectorClusterReadbackTest {
    private static final String KEY = "synthetic-reviewed-inventory-key";
    private final UnusedVectorTransferJson json = new UnusedVectorTransferJson();
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final ZillizCloudControlPlaneClient provider = mock(ZillizCloudControlPlaneClient.class);
    private final ExternalVectorClusterReadback reader = new ExternalVectorClusterReadback(secrets, provider, json);
    private final VectorIdentity vector = new VectorIdentity("cluster", "project", "region", "Free", "free.example.test", "default", "COOLIFY");
    private final InventoryApproval review = new InventoryApproval(json.bytesHash(KEY), "staff-full-visibility-review", "attended-no-writer-window");

    @BeforeEach void provider() {
        when(secrets.resolveSecret("ZILLIZ_CLOUD_API_KEY")).thenReturn(KEY);
        when(provider.getCluster("cluster", KEY)).thenReturn(cluster("cluster", "project", "region", "Free", "RUNNING", "https://free.example.test"));
        when(provider.listServingCollections("https://free.example.test", KEY)).thenReturn(List.of());
    }

    @Test void reviewedBroadKeyProducesDefaultOnlyProofWithoutAclOrMutationClaims() {
        ClusterReadback proof = reader.requireEmpty(vector, review);
        assertThat(proof.collectionsByDatabase()).isEqualTo(Map.of("default", List.of()));
        assertThat(proof.credentialFingerprint()).isEqualTo(json.bytesHash(KEY));
        assertThat(proof.fullVisibilityReviewReference()).isEqualTo(review.fullVisibilityReviewReference());
        assertThat(proof.noWriterWindowReference()).isEqualTo(review.noWriterWindowReference());
        assertThat(proof.fingerprint()).isEqualTo(json.receiptHash(proof));
        assertThat(json.write(proof)).doesNotContain(KEY);
        verify(provider).getCluster("cluster", KEY);
        verify(provider).listServingCollections("https://free.example.test", KEY);
        verifyNoMoreInteractions(provider);
    }

    @ParameterizedTest @ValueSource(strings = {"fingerprint", "full-visibility-review", "writer-window", "dedicated", "database"})
    void noInventoryRequestWithoutExactReviewedScope(String change) {
        InventoryApproval supplied = switch (change) {
            case "fingerprint" -> new InventoryApproval("0".repeat(64), review.fullVisibilityReviewReference(), review.noWriterWindowReference());
            case "full-visibility-review" -> new InventoryApproval(review.expectedCredentialFingerprint(), "", review.noWriterWindowReference());
            case "writer-window" -> new InventoryApproval(review.expectedCredentialFingerprint(), review.fullVisibilityReviewReference(), "");
            default -> review;
        };
        VectorIdentity identity = new VectorIdentity(vector.clusterId(), vector.projectId(), vector.regionId(),
            change.equals("dedicated") ? "Dedicated" : "Free", vector.endpointHost(), change.equals("database") ? "another" : "default", vector.providerType());
        assertThatThrownBy(() -> reader.requireEmpty(identity, supplied)).hasMessageContaining("EMPTY_INVENTORY_UNPROVEN");
        verifyNoInteractions(provider);
    }

    @ParameterizedTest @ValueSource(strings = {"cluster", "project", "region", "option", "status", "endpoint", "collections", "null-list"})
    void metadataAccessOrRestrictedEmptyAssumptionsCannotReplaceExactInventory(String change) {
        when(provider.getCluster("cluster", KEY)).thenReturn(cluster(change.equals("cluster") ? "other" : "cluster",
            change.equals("project") ? "other" : "project", change.equals("region") ? "other" : "region",
            change.equals("option") ? "Dedicated" : "Free", change.equals("status") ? "SUSPENDED" : "RUNNING",
            change.equals("endpoint") ? "https://other.example.test" : "https://free.example.test"));
        if (change.equals("collections")) when(provider.listServingCollections(anyString(), eq(KEY))).thenReturn(List.of("foreign_collection"));
        if (change.equals("null-list")) when(provider.listServingCollections(anyString(), eq(KEY))).thenReturn(null);
        assertThatThrownBy(() -> reader.requireEmpty(vector, review)).hasMessageContaining("EMPTY_INVENTORY_UNPROVEN");
        assertThat(mockingDetails(provider).getInvocations()).allSatisfy(i ->
            assertThat(i.getMethod().getName()).isIn("getCluster", "listServingCollections"));
    }

    private ZillizCloudControlPlaneClient.ZillizClusterSummary cluster(String id, String project, String region, String option, String status, String endpoint) {
        return new ZillizCloudControlPlaneClient.ZillizClusterSummary(id, "synthetic", project, region, "Serverless", "", 0, status, endpoint, option);
    }

    @ParameterizedTest @ValueSource(strings = {"describe", "collections"})
    void upstreamExceptionTextAndCauseAreNeverReturnedByThePlatformErrorHandler(String operation) {
        String sensitive = "synthetic-upstream-secret-like-token";
        var upstream = new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
            "Authorization: Bearer " + sensitive, new IllegalStateException(sensitive));
        if (operation.equals("describe")) when(provider.getCluster("cluster", KEY)).thenThrow(upstream);
        else when(provider.listServingCollections("https://free.example.test", KEY)).thenThrow(upstream);
        Throwable failure = catchThrowable(() -> reader.requireEmpty(vector, review));
        assertThat(failure).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("UNUSED_VECTOR_TRANSFER_EMPTY_INVENTORY_UNPROVEN").hasNoCause();
        var response = new com.ai.fabric.platform.backend.web.PlatformApiExceptionHandler()
            .handleResponseStatusException((org.springframework.web.server.ResponseStatusException) failure);
        assertThat(response.getStatusCode().value()).isEqualTo(412);
        assertThat(json.write(response.getBody())).doesNotContain(sensitive, "Authorization", "Bearer");
    }
}
