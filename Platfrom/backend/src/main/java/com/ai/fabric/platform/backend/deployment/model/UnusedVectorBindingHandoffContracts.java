package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.InventoryApproval;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.ClusterReadback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Explicit staff approval to reuse an unused cluster, never an authorization to migrate customer data. */
public final class UnusedVectorBindingHandoffContracts {
    private UnusedVectorBindingHandoffContracts() { }

    public record Source(String bindingId, String cancellationReceiptId, String expectedBindingFingerprint,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String knownNoEffectSettlementReceiptId) {
        public Source {
            if ((cancellationReceiptId == null) == (knownNoEffectSettlementReceiptId == null)) throw invalid();
        }
        public Source(String bindingId, String cancellationReceiptId, String expectedBindingFingerprint) {
            this(bindingId, cancellationReceiptId, expectedBindingFingerprint, null);
        }
    }

    public record Request(String operationKey, String approvalReference, List<Source> sources,
                          ExistingVectorBindingContracts.Request connection,
                          @JsonInclude(JsonInclude.Include.NON_NULL) InventoryApproval inventoryApproval) {
        public Request { if (sources != null) sources = List.copyOf(sources); }
        public Request(String operationKey, String approvalReference, List<Source> sources,
                       ExistingVectorBindingContracts.Request connection) {
            this(operationKey, approvalReference, sources, connection, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Request fromJson(JsonNode node) {
            fields(node, Set.of("operationKey", "approvalReference", "sources", "connection", "inventoryApproval"));
            JsonNode values = node.get("sources");
            if (values == null || !values.isArray() || values.isEmpty() || values.size() > 32) throw invalid();
            var sources = new ArrayList<Source>();
            for (JsonNode source : values) {
                fields(source, Set.of("bindingId", "cancellationReceiptId", "expectedBindingFingerprint", "knownNoEffectSettlementReceiptId"));
                sources.add(new Source(text(source, "bindingId"), optionalProof(source, "cancellationReceiptId"),
                    text(source, "expectedBindingFingerprint"), optionalProof(source, "knownNoEffectSettlementReceiptId")));
            }
            InventoryApproval inventory = null;
            JsonNode review = node.get("inventoryApproval");
            if (review != null && !review.isNull()) {
                fields(review, Set.of("expectedCredentialFingerprint", "fullVisibilityReviewReference", "noWriterWindowReference"));
                inventory = new InventoryApproval(text(review, "expectedCredentialFingerprint"),
                    text(review, "fullVisibilityReviewReference"), text(review, "noWriterWindowReference"));
            }
            if (sources.stream().anyMatch(source -> source.knownNoEffectSettlementReceiptId() != null) != (inventory != null)) throw invalid();
            JsonNode c = node.get("connection");
            fields(c, Set.of("versionId", "clusterId", "projectId", "regionId", "expectedDeploymentOption",
                "databaseName", "username", "password", "approvalReference", "authType", "token"));
            return new Request(text(node, "operationKey"), text(node, "approvalReference"), sources,
                new ExistingVectorBindingContracts.Request(text(c, "versionId"), text(c, "clusterId"), text(c, "projectId"),
                    text(c, "regionId"), text(c, "expectedDeploymentOption"), text(c, "databaseName"), optional(c, "username"),
                    optional(c, "password"), text(c, "approvalReference"),
                    ExistingVectorBindingContracts.AuthenticationType.fromJson(c.get("authType")), optional(c, "token")), inventory);
        }

        @Override public String toString() { return "UnusedVectorBindingHandoffRequest[credentials redacted]"; }
    }

    public record Receipt(String id, String operationKey, String commandFingerprint, String deploymentId,
                          String customerId, String tenantId, String targetProfileId, String clusterId,
                          String versionId, String versionConfigHash, List<Source> sources, String approvalReference,
                          ExistingVectorBindingContracts.Summary binding, String actorId, Instant handedOffAt,
                          String fingerprint,
                          @JsonInclude(JsonInclude.Include.NON_NULL) ClusterReadback emptyClusterReadback,
                          @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SourceTransition> sourceTransitions) {
        public Receipt {
            sources = List.copyOf(sources);
            sourceTransitions = sourceTransitions == null ? List.of() : List.copyOf(sourceTransitions);
        }
        public Receipt(String id, String operationKey, String commandFingerprint, String deploymentId,
                       String customerId, String tenantId, String targetProfileId, String clusterId,
                       String versionId, String versionConfigHash, List<Source> sources, String approvalReference,
                       ExistingVectorBindingContracts.Summary binding, String actorId, Instant handedOffAt,
                       String fingerprint) {
            this(id, operationKey, commandFingerprint, deploymentId, customerId, tenantId, targetProfileId, clusterId,
                versionId, versionConfigHash, sources, approvalReference, binding, actorId, handedOffAt, fingerprint, null, List.of());
        }
    }

    public record SourceTransition(String bindingId, String knownNoEffectSettlementReceiptId, String fromStatus, String toStatus,
                                   String beforeRowFingerprint, String afterRowFingerprint,
                                   Instant beforeUpdatedAt, Instant transitionedAt) { }

    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid();
        node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid(); });
    }

    private static String text(JsonNode node, String name) {
        String value = optional(node, name);
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    private static String optional(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static String optionalProof(JsonNode node, String name) {
        return node.hasNonNull(name) ? text(node, name) : null;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("UNUSED_VECTOR_HANDOFF_REQUEST_INVALID"); }
}
