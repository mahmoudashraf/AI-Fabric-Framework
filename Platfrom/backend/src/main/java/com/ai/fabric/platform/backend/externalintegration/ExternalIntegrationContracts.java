package com.ai.fabric.platform.backend.externalintegration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExternalIntegrationContracts {
    private ExternalIntegrationContracts() { }
    public static final String PROFILE_SCHEMA = "loomai-external-integration-profile/v1";

    public record Profile(String schemaVersion, String id, String bindingSlotId,
                          String pluginId, String pluginVersionId, String manifestSha256,
                          Set<String> targetProfileIds, Set<String> configFields, Set<String> secretFields,
                          Set<String> endpointFields, Set<String> accountScopeFields, Set<String> allowedHosts) {
        public Profile {
            targetProfileIds = copy(targetProfileIds); configFields = copy(configFields); secretFields = copy(secretFields);
            endpointFields = copy(endpointFields); accountScopeFields = copy(accountScopeFields); allowedHosts = copy(allowedHosts);
        }
    }

    @JsonDeserialize(using = ExternalIntegrationRequestDeserializer.ApprovalReader.class)
    public record ApprovalRequest(@NotBlank String versionId, @NotBlank String targetProfileId,
                                  @NotBlank String bindingSlotId, @NotBlank String selectedProfileId,
                                  @NotNull JsonNode config, @NotNull JsonNode accountScope,
                                  @NotBlank String reviewReference, @NotBlank String operationKey) { }
    @JsonDeserialize(using = ExternalIntegrationRequestDeserializer.BindReader.class)
    public record BindRequest(@NotBlank String versionId, @NotBlank String targetProfileId,
                              @NotBlank String bindingSlotId, @NotBlank String selectedProfileId,
                              @NotBlank String approvalId, @NotNull @PositiveOrZero Long expectedEntitlementRevision,
                              @NotBlank String operationKey,
                              @NotNull @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) JsonNode credentials) {
        @Override public String toString() { return "ExternalIntegrationBindRequest[credentials=REDACTED]"; }
    }
    @JsonDeserialize(using = ExternalIntegrationRequestDeserializer.RevokeReader.class)
    public record RevokeRequest(@NotBlank String versionId, @NotBlank String targetProfileId,
                                @NotBlank String bindingSlotId, @NotBlank String selectedProfileId,
                                @NotBlank String bindingId, @NotNull @PositiveOrZero Long expectedBindingRevision,
                                @NotNull @PositiveOrZero Long expectedEntitlementRevision,
                                @NotBlank String operationKey) { }
    public record Scope(String offerId, String customerId, String tenantId, String deploymentId,
                        String versionId, String targetProfileId, String bindingSlotId, String profileId) { }
    public record Approval(String id, Scope scope, String offerFingerprint, String versionConfigHash,
                           String targetFingerprint, Profile profile, Map<String, String> config,
                           Map<String, String> accountScope, String reviewReference, String reviewedBy,
                           String operationKey, String requestFingerprint) {
        public Approval { config = Map.copyOf(config); accountScope = Map.copyOf(accountScope); }
    }
    public record ApprovalOption(String id, String versionId, String targetProfileId, String bindingSlotId,
                                 String selectedProfileId, Map<String, String> config,
                                 Map<String, String> accountScope, String reviewReference) {
        public ApprovalOption { config = Map.copyOf(config); accountScope = Map.copyOf(accountScope); }
    }
    public record Binding(String id, Scope scope, String approvalId, Map<String, String> secretReferences,
                          Map<String, String> secretRevisions) {
        public Binding { secretReferences = Map.copyOf(secretReferences); secretRevisions = Map.copyOf(secretRevisions); }
    }
    public record Summary(String id, String approvalId, String deploymentId, String versionId,
                          String targetProfileId, String bindingSlotId, String selectedProfileId, String status, long revision) {
        public Summary(String id, String approvalId, String deploymentId, String versionId, String targetProfileId,
                       String bindingSlotId, String selectedProfileId, String status) {
            this(id, approvalId, deploymentId, versionId, targetProfileId, bindingSlotId, selectedProfileId, status, 0);
        }
    }
    public record InstallInputs(ObjectNode config, ObjectNode secretRefs) { }
    public record Projection(String bindingId, String approvalId, Scope scope, String fingerprint,
                             Map<String, String> configEnvironment, Map<String, String> secretEnvironment) {
        public Projection {
            configEnvironment = Map.copyOf(configEnvironment); secretEnvironment = Map.copyOf(secretEnvironment);
        }
    }
    public record ReleaseSnapshot(String releaseId, String runtimeAuthorityFingerprint, List<Projection> projections) {
        public ReleaseSnapshot { projections = List.copyOf(projections); }
    }
    public record ReleaseEnvironment(Map<String, String> configEnvironment, Map<String, String> secretEnvironment) {
        public ReleaseEnvironment {
            configEnvironment = Map.copyOf(configEnvironment); secretEnvironment = Map.copyOf(secretEnvironment);
        }
    }
    private static Set<String> copy(Set<String> values) {
        return values == null ? Set.of() : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(values));
    }
}
