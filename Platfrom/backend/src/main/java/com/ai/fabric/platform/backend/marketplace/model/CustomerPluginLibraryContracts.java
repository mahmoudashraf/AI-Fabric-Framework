package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class CustomerPluginLibraryContracts {
    private CustomerPluginLibraryContracts() { }

    public record Create(JsonNode manifest) { }
    public record Save(JsonNode manifest, Long expectedRevision) { }
    public record Revision(Long expectedRevision) { }
    public record Library(String customerId, boolean canManage, List<Summary> plugins) { }
    public record Issue(String path, String code, String message) { }
    public record Check(boolean valid, long revision, List<Issue> issues, boolean connectionTested) { }
    public record Version(String id, String version, String status, JsonNode manifest, Instant createdAt) { }
    public record Install(String id, String deploymentId, String deploymentName, String pluginVersionId,
                          String pluginVersion, String status, Instant updatedAt) { }
    public record AppliedUse(String deploymentId, String targetProfileId, String releaseId,
                             String deploymentVersionId, String pluginVersionId, String pluginVersion,
                             Instant appliedAt) { }
    public record Usage(long savedCount, long appliedCount, List<Install> saved, List<AppliedUse> applied) { }
    public record Summary(String id, String ownerCustomerId, String visibility, String displayName,
                          String pluginType, String status, JsonNode manifest, long revision,
                          List<Version> versions, Usage installs, boolean canManage,
                          String catalogueFamily, String catalogueAudience, String adapterContract,
                          Instant updatedAt) { }
}
