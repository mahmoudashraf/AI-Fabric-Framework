package com.ai.fabric.platform.backend.customerselfservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Explicit fences for assigning an existing, never-activated app without activating a release. */
public final class CustomerStagingConsumerConfigurationContracts {
    private CustomerStagingConsumerConfigurationContracts() { }

    public record DraftWorkspaceExpectation(String installationEntityId, long expectedRowVersion,
            @JsonInclude(JsonInclude.Include.NON_NULL) String selectedWorkspaceProfileId) {
        public DraftWorkspaceExpectation {
            text(installationEntityId, 64);
            if (expectedRowVersion < 0) throw invalid();
            if (selectedWorkspaceProfileId != null) text(selectedWorkspaceProfileId, 100);
        }
    }

    public record Request(String consumerId, long expectedBindingRevision, long expectedEntitlementRevision,
                          String operationKey, List<DraftWorkspaceExpectation> expectedDraftWorkspaces) {
        public Request {
            text(consumerId, 128); text(operationKey, 128);
            if (!operationKey.matches("[A-Za-z0-9._:-]{1,128}") || expectedBindingRevision < 0
                || expectedBindingRevision == Long.MAX_VALUE || expectedEntitlementRevision < 0
                || expectedDraftWorkspaces == null || expectedDraftWorkspaces.size() > 100
                || expectedDraftWorkspaces.stream().anyMatch(java.util.Objects::isNull)
                || expectedDraftWorkspaces.stream().map(DraftWorkspaceExpectation::installationEntityId).distinct().count()
                    != expectedDraftWorkspaces.size()) throw invalid();
            expectedDraftWorkspaces = expectedDraftWorkspaces.stream()
                .sorted(Comparator.comparing(DraftWorkspaceExpectation::installationEntityId)).toList();
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Request fromJson(JsonNode value) {
            fields(value, Set.of("consumerId", "expectedBindingRevision", "expectedEntitlementRevision",
                "operationKey", "expectedDraftWorkspaces"));
            var workspaces = value.get("expectedDraftWorkspaces");
            if (workspaces == null || !workspaces.isArray() || workspaces.size() > 100) throw invalid();
            var expectations = new ArrayList<DraftWorkspaceExpectation>();
            for (var workspace : workspaces) {
                if (workspace == null || !workspace.isObject() || workspace.size() < 2 || workspace.size() > 3
                    || !workspace.has("installationEntityId") || !workspace.has("expectedRowVersion")) throw invalid();
                workspace.fieldNames().forEachRemaining(name -> {
                    if (!Set.of("installationEntityId", "expectedRowVersion", "selectedWorkspaceProfileId").contains(name)) throw invalid();
                });
                expectations.add(new DraftWorkspaceExpectation(string(workspace, "installationEntityId"),
                    revision(workspace, "expectedRowVersion"), workspace.hasNonNull("selectedWorkspaceProfileId")
                        ? string(workspace, "selectedWorkspaceProfileId") : null));
            }
            return new Request(string(value, "consumerId"), revision(value, "expectedBindingRevision"),
                revision(value, "expectedEntitlementRevision"), string(value, "operationKey"), expectations);
        }
    }

    private static void fields(JsonNode node, Set<String> names) {
        if (node == null || !node.isObject() || node.size() != names.size()) throw invalid();
        node.fieldNames().forEachRemaining(name -> { if (!names.contains(name)) throw invalid(); });
    }

    private static long revision(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw invalid();
        return value.longValue();
    }

    private static String string(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl)) throw invalid();
    }
}
