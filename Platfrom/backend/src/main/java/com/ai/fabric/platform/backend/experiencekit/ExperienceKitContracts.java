package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.OfferSummary;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ExperienceKitContracts {
    private ExperienceKitContracts() { }
    public record Definition(String code, String version, String name, String summary, String description,
        String experiencePackCode, String defaultWorkspaceName, List<String> features, String demoUrl) { }
    public record CatalogueEntry(Definition definition, List<OfferSummary> offers) { }
    public record CreateRequest(@NotBlank String kitCode, @NotBlank @Size(max=120) String name,
        @NotBlank String offerId, @NotBlank String targetProfileId, @NotBlank String workspaceProfileId,
        String aiStateStorageMode, @NotBlank @Size(max=120) String workspaceName,
        @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String brandColor,
        @NotBlank @Pattern(regexp="standalone|embedded|both") String deliveryMode,
        @Size(max=63) String appSlug, @Size(max=255) String websiteOrigin, boolean humanSupportEnabled,
        @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,128}") String operationKey) { }
    public record UpdateRequest(@NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=120) String workspaceName,
        @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String brandColor,
        @NotBlank @Pattern(regexp="standalone|embedded|both") String deliveryMode,
        @Size(max=63) String appSlug, @Size(max=255) String websiteOrigin,
        boolean humanSupportEnabled, @PositiveOrZero long expectedRevision,
        @NotNull @PositiveOrZero Long expectedWorkspaceRevision,
        @PositiveOrZero Long expectedEmbeddedWorkspaceRevision) { }
    public record Summary(String id, String kitCode, String kitVersion, String name, String deploymentId,
        String consumerId, String installationId, String offerId, String targetProfileId,
        boolean humanSupportEnabled, long revision, Instant createdAt, Instant updatedAt,
        AIWorkspaceInstallationSummary workspace, String embeddedInstallationId,
        AIWorkspaceInstallationSummary embeddedWorkspace, String deliveryMode) { }
}
