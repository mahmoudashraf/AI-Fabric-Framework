package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Explicit staff approval to reuse an unused cluster, never an authorization to migrate customer data. */
public final class UnusedVectorBindingHandoffContracts {
    private UnusedVectorBindingHandoffContracts() { }

    public record Source(String bindingId, String cancellationReceiptId, String expectedBindingFingerprint) { }

    public record Request(String operationKey, String approvalReference, List<Source> sources,
                          ExistingVectorBindingContracts.Request connection) {
        public Request { if (sources != null) sources = List.copyOf(sources); }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Request fromJson(JsonNode node) {
            fields(node, Set.of("operationKey", "approvalReference", "sources", "connection"));
            JsonNode values = node.get("sources");
            if (values == null || !values.isArray() || values.isEmpty() || values.size() > 32) throw invalid();
            var sources = new ArrayList<Source>();
            for (JsonNode source : values) {
                fields(source, Set.of("bindingId", "cancellationReceiptId", "expectedBindingFingerprint"));
                sources.add(new Source(text(source, "bindingId"), text(source, "cancellationReceiptId"),
                    text(source, "expectedBindingFingerprint")));
            }
            JsonNode c = node.get("connection");
            fields(c, Set.of("versionId", "clusterId", "projectId", "regionId", "expectedDeploymentOption",
                "databaseName", "username", "password", "approvalReference", "authType", "token"));
            return new Request(text(node, "operationKey"), text(node, "approvalReference"), sources,
                new ExistingVectorBindingContracts.Request(text(c, "versionId"), text(c, "clusterId"), text(c, "projectId"),
                    text(c, "regionId"), text(c, "expectedDeploymentOption"), text(c, "databaseName"), optional(c, "username"),
                    optional(c, "password"), text(c, "approvalReference"),
                    ExistingVectorBindingContracts.AuthenticationType.fromJson(c.get("authType")), optional(c, "token")));
        }

        @Override public String toString() { return "UnusedVectorBindingHandoffRequest[credentials redacted]"; }
    }

    public record Receipt(String id, String operationKey, String commandFingerprint, String deploymentId,
                          String customerId, String tenantId, String targetProfileId, String clusterId,
                          String versionId, String versionConfigHash, List<Source> sources, String approvalReference,
                          ExistingVectorBindingContracts.Summary binding, String actorId, Instant handedOffAt,
                          String fingerprint) {
        public Receipt { sources = List.copyOf(sources); }
    }

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

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("UNUSED_VECTOR_HANDOFF_REQUEST_INVALID"); }
}
