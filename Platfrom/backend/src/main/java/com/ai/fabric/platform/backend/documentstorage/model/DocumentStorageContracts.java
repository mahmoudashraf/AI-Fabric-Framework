package com.ai.fabric.platform.backend.documentstorage.model;

import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class DocumentStorageContracts {
    private DocumentStorageContracts() { }
    public record Connection(String id, String name, String mode, String bucket, String region,
        String rootPrefix, String status, String lastError, Instant createdAt, Instant updatedAt) { }
    public record Catalogue(boolean hostedAvailable, long maxUploadBytes, List<String> supportedExtensions,
        List<Connection> connections) { }
    public record Create(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=160) String operationKey) { }
    public record Command(@NotBlank @Size(max=160) String operationKey) { }
    public record Bind(@NotBlank @Size(max=64) String targetProfileId, @Size(max=480) String prefix,
        @NotBlank @Size(max=160) String operationKey) { }
    public record Binding(Connection connection, DocumentStorageBindingSummary binding) { }
    public record Upload(String id, String connectionId, String filename, String objectKey, String prefix,
        long sizeBytes, String contentType, String sha256, String status, String lastError, Instant createdAt) { }
    public record PrepareSelection(@NotBlank @Size(max=64) String targetProfileId,
        @NotBlank @Size(max=64) String releaseId, @NotEmpty @Size(max=20) List<@NotBlank String> uploadIds,
        @NotBlank @Size(max=160) String datasetId, boolean publicApproved,
        @NotBlank @Size(max=160) String operationKey) { }
    public record SelectionItem(String uploadId, String sourceId, String status, String workId, String message) { }
    public record SelectionResult(List<SelectionItem> items, boolean complete) { }
}
