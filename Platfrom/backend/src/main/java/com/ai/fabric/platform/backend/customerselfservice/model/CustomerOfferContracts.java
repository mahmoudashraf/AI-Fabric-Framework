package com.ai.fabric.platform.backend.customerselfservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The only supported self-service contract revision. No raw provider or draft configuration. */
public final class CustomerOfferContracts {
    private CustomerOfferContracts() { }

    public enum TargetIntent { CUSTOMER_STAGING, CUSTOMER_PRODUCTION }
    public enum StaffAccess { VIEWER, OPERATOR, EDITOR, ADMIN }
    public enum Operation {
        TEMPLATE_SELECT, BOOTSTRAP_CONFIGURATION, BOOTSTRAP, READ, DRAFT_EDIT, VERSION_PUBLISH,
        BINDING_REPLACE, BINDING_REVOKE,
        // Historical audit values only; these operations no longer have active endpoints or grants.
        INBOX_OPERATOR_ENROLL, INBOX_OPERATOR_ROTATE, INBOX_OPERATOR_DELIVER, INBOX_OPERATOR_REVOKE,
        CONVERSATION_HANDOVER_RECOVER,
        SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE,
        APP_CONFIGURE, DOMAIN_CONFIGURE, AUTH_CONFIGURE, APPLY, VERIFY,
        PROMOTION_PLAN, PROMOTION_APPLY, ACTIVATE, ROLLBACK,
        STAGING_RETAIN, STAGING_EXTEND, TARGET_RETIRE
    }
    public enum Setting {
        DEPLOYMENT_NAME, APP_TITLE, LOGO_ASSET_ID, THEME_PROFILE_ID, INITIAL_VIEW,
        INTERACTION_PROFILE_ID, AUTH_PROFILE_ID, APP_DOMAIN, DOCUMENT_AUDIENCE, AI_STATE_STORAGE_MODE
    }
    public enum ValueType { TEXT, CHOICE }
    public enum BindingKind { CUSTOMER_S3, CUSTOMER_IDENTITY, CUSTOMER_AI_PROVIDER, INBOX_OPERATOR, CUSTOMER_AI_STATE, EXTERNAL_SERVICE }
    public enum DocumentAudience { PUBLIC, AUTHENTICATED }
    public enum Prerequisite { APPROVED_POLICY, IMMUTABLE_ARTIFACTS, STORAGE, IDENTITY, CAPACITY,
        NOTIFICATIONS, OPERATING_POLICY }
    public enum EntitlementStatus { INVITED, ACTIVE, REVOKED }

    public record PluginPin(@NotBlank String pluginId, @NotBlank String versionId,
                            @NotBlank String manifestSha256) { }
    public record ArtifactPin(@NotBlank String artifactId, @NotBlank String fingerprint) { }
    public record TargetPermission(@NotBlank String targetProfileId, @NotNull TargetIntent intent,
                                   @NotBlank String profileFingerprint) { }
    public record SettingRule(@NotNull Setting setting, @NotNull ValueType type,
                              @Positive int maxLength, @NotNull Set<String> allowedValues) {
        public SettingRule { allowedValues = ordered(allowedValues); }
    }
    public record WorkspaceProfile(@NotBlank String id, @NotBlank String label,
                                   @NotNull JsonNode interaction, @NotNull JsonNode theme,
                                   @NotNull JsonNode authentication) { }
    public record BindingSlot(@NotBlank String id, @NotNull BindingKind kind,
                              @NotNull Set<String> supportedProfileIds,
                              String pluginId, String installConfigField) {
        public BindingSlot(String id, BindingKind kind, Set<String> supportedProfileIds) {
            this(id, kind, supportedProfileIds, null, null);
        }

        public BindingSlot {
            supportedProfileIds = ordered(supportedProfileIds);
        }
    }
    public record ReviewedLimits(@NotBlank String measuredPolicyRef, @Positive int activeStagingTargets,
                                 @Positive int replicas, @Positive long cpuMillis, @Positive long memoryMiB,
                                 @Positive long diskMiB, @PositiveOrZero int databases, @PositiveOrZero int indexes,
                                 @PositiveOrZero int dependencies, @Positive int concurrentApplies,
                                 @Positive int stagingExpiryHours, @Positive int maxExtensionHours,
                                 @Positive int recoveryWindowHours) { }
    /** Exact minimum full-topology demand reviewed for one target of this offer. */
    public record ReviewedTopology(@Positive int replicas, @Positive long cpuMillis,
                                   @Positive long memoryMiB, @PositiveOrZero long diskMiB,
                                   @PositiveOrZero int databases, @PositiveOrZero int indexes,
                                   @PositiveOrZero int dependencies, @Positive int concurrentApplies) { }
    public record Definition(@NotBlank String schemaVersion, @NotBlank String name,
                             @NotBlank String templateId, @NotNull @Valid PluginPin template,
                             @NotNull @Valid List<PluginPin> plugins,
                             @NotNull @Valid List<ArtifactPin> artifacts,
                             @NotNull @Valid List<TargetPermission> targets,
                             @NotNull Set<Operation> operations,
                             @NotNull @Valid List<SettingRule> settings,
                             @NotNull @Valid List<BindingSlot> bindingSlots,
                             @NotNull Set<DocumentAudience> documentAudiences,
                             @NotNull Set<Prerequisite> admissionPrerequisites,
                             @NotNull @Valid ReviewedLimits limits, @NotNull @Valid ReviewedTopology topology,
                             @NotBlank String approvedPolicyRef, @NotBlank String operatingPolicyRef,
                             @NotBlank String identityPolicyRef, @NotBlank String storagePolicyRef,
                             @JsonInclude(JsonInclude.Include.NON_EMPTY) @Valid List<WorkspaceProfile> workspaceProfiles,
                             @JsonInclude(JsonInclude.Include.NON_EMPTY) @Valid Map<AiStateStorageMode, ReviewedTopology> aiStateStorageTopologies,
                             @JsonInclude(JsonInclude.Include.NON_EMPTY) @Valid List<Profile> externalIntegrationProfiles) {
        public Definition(String schemaVersion, String name, String templateId, PluginPin template,
                          List<PluginPin> plugins, List<ArtifactPin> artifacts, List<TargetPermission> targets,
                          Set<Operation> operations, List<SettingRule> settings, List<BindingSlot> bindingSlots,
                          Set<DocumentAudience> documentAudiences, Set<Prerequisite> admissionPrerequisites,
                          ReviewedLimits limits, ReviewedTopology topology, String approvedPolicyRef,
                          String operatingPolicyRef, String identityPolicyRef, String storagePolicyRef) {
            this(schemaVersion, name, templateId, template, plugins, artifacts, targets, operations, settings,
                bindingSlots, documentAudiences, admissionPrerequisites, limits, topology, approvedPolicyRef,
                operatingPolicyRef, identityPolicyRef, storagePolicyRef, List.of(), Map.of(), List.of());
        }
        public Definition(String schemaVersion, String name, String templateId, PluginPin template,
                          List<PluginPin> plugins, List<ArtifactPin> artifacts, List<TargetPermission> targets,
                          Set<Operation> operations, List<SettingRule> settings, List<BindingSlot> bindingSlots,
                          Set<DocumentAudience> documentAudiences, Set<Prerequisite> admissionPrerequisites,
                          ReviewedLimits limits, ReviewedTopology topology, String approvedPolicyRef,
                          String operatingPolicyRef, String identityPolicyRef, String storagePolicyRef,
                          List<WorkspaceProfile> workspaceProfiles) {
            this(schemaVersion, name, templateId, template, plugins, artifacts, targets, operations, settings,
                bindingSlots, documentAudiences, admissionPrerequisites, limits, topology, approvedPolicyRef,
                operatingPolicyRef, identityPolicyRef, storagePolicyRef, workspaceProfiles, Map.of(), List.of());
        }
        public Definition(String schemaVersion, String name, String templateId, PluginPin template,
                          List<PluginPin> plugins, List<ArtifactPin> artifacts, List<TargetPermission> targets,
                          Set<Operation> operations, List<SettingRule> settings, List<BindingSlot> bindingSlots,
                          Set<DocumentAudience> documentAudiences, Set<Prerequisite> admissionPrerequisites,
                          ReviewedLimits limits, ReviewedTopology topology, String approvedPolicyRef,
                          String operatingPolicyRef, String identityPolicyRef, String storagePolicyRef,
                          List<WorkspaceProfile> workspaceProfiles, Map<AiStateStorageMode, ReviewedTopology> aiStateStorageTopologies) {
            this(schemaVersion, name, templateId, template, plugins, artifacts, targets, operations, settings,
                bindingSlots, documentAudiences, admissionPrerequisites, limits, topology, approvedPolicyRef,
                operatingPolicyRef, identityPolicyRef, storagePolicyRef, workspaceProfiles, aiStateStorageTopologies, List.of());
        }
        public Definition {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            targets = targets == null ? List.of() : List.copyOf(targets);
            operations = ordered(operations);
            settings = settings == null ? List.of() : List.copyOf(settings);
            bindingSlots = bindingSlots == null ? List.of() : List.copyOf(bindingSlots);
            documentAudiences = ordered(documentAudiences);
            admissionPrerequisites = ordered(admissionPrerequisites);
            workspaceProfiles = workspaceProfiles == null ? List.of() : List.copyOf(workspaceProfiles);
            aiStateStorageTopologies = aiStateStorageTopologies == null ? Map.of() : Map.copyOf(aiStateStorageTopologies);
            externalIntegrationProfiles = externalIntegrationProfiles == null ? List.of() : List.copyOf(externalIntegrationProfiles);
        }
    }
    public record PublishOfferRequest(@NotBlank String offerId, @NotNull @Valid Definition definition,
                                      @NotBlank String reviewReference) { }
    public record InviteRequest(@NotBlank String customerId, @NotBlank String ownerUserId,
                                @NotBlank String invitationReference, @NotNull Instant expiresAt,
                                @PositiveOrZero long expectedRevision, @NotBlank String operationKey,
                                @NotBlank String reason) { }
    public record EntitlementCommand(@PositiveOrZero long expectedRevision,
                                     @NotBlank String operationKey, @NotBlank String reason) { }
    public record EntitlementSummary(String id, String offerId, String customerId, String ownerUserId,
                                     EntitlementStatus status, long revision, Instant expiresAt) { }

    /** Internal integration input: owning services derive changed fields and IDs from their parsed command. */
    public record AuthorizationRequest(String offerId, String targetProfileId, String deploymentId,
                                       String tenantId, String releaseId, Operation operation,
                                       String operationKey, Set<Setting> changedSettings,
                                       String bindingSlotId, String selectedProfileId,
                                       DocumentAudience documentAudience) {
        public AuthorizationRequest {
            changedSettings = ordered(changedSettings);
        }
    }
    public record AdmissionContext(String customerId, String tenantId, String deploymentId, String releaseId,
                                   String targetProfileId, TargetIntent intent, String offerId,
                                   String offerFingerprint, long entitlementRevision, String placementEvidenceId, Operation operation,
                                   String operationKey, ReviewedLimits limits, ReviewedTopology topology) { }
    public record PrerequisiteEvidence(AdmissionContext context, Set<Prerequisite> passed,
                                       Instant checkedAt, Instant expiresAt,
                                       String evidenceReference, String capacityReservationId) {
        public PrerequisiteEvidence { passed = ordered(passed); }
    }
    public record Authorization(AdmissionContext context, Definition definition) { }

    public record PermissionRow(Operation operation, StaffAccess staffAccess, Set<TargetIntent> targetIntents, Set<Setting> settings,
                                boolean ownedBindingRequired, boolean admissionEvidenceRequired) { }
    public record TargetSummary(String id, TargetIntent intent, String readiness,
                                Set<Operation> capabilities, ReviewedLimits limits, List<String> blockers) { }
    public record OfferSummary(String id, String name, String templateId, String templateVersionId,
                               String fingerprint, EntitlementSummary entitlement,
                               List<TargetSummary> targets, List<PermissionRow> permissions,
                               List<SettingRule> settings, List<BindingSlot> bindingSlots,
                               Set<DocumentAudience> documentAudiences, List<String> blockers,
                               List<WorkspaceProfile> workspaceProfiles, List<Profile> externalIntegrationProfiles) { }
    public record CustomerDeploymentSummary(String deploymentId, String name, String environmentName,
                                            String status, String tenantId, String offerId,
                                            String bootstrapTargetProfileId,
                                            String offerFingerprint, long authorizationEntitlementRevision,
                                            String activeVersionId, Instant createdAt) { }
    public record SettingsPatch(@NotNull Map<Setting, JsonNode> values) { }
    public record PlacementProbeRequest(@NotNull TargetIntent intent) { }
    public record PlacementEvidenceSummary(String id, String targetProfileId, TargetIntent intent,
                                            String profileFingerprint, Instant checkedAt, Instant expiresAt) { }

    private static <T extends Comparable<? super T>> Set<T> ordered(Set<T> values) {
        return values == null ? Set.of() : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(values));
    }
}
