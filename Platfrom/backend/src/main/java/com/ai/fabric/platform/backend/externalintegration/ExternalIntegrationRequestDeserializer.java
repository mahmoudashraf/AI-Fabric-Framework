package com.ai.fabric.platform.backend.externalintegration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;

/** Strict only for this API. No application-wide Jackson coercion/default changes. */
public final class ExternalIntegrationRequestDeserializer {
    private ExternalIntegrationRequestDeserializer() { }
    public static final class ApprovalReader extends JsonDeserializer<ApprovalRequest> {
        @Override public ApprovalRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, Set.of("versionId", "targetProfileId", "bindingSlotId", "selectedProfileId", "config", "accountScope", "reviewReference", "operationKey"));
            return new ApprovalRequest(text(parser, node, "versionId"), text(parser, node, "targetProfileId"), text(parser, node, "bindingSlotId"),
                text(parser, node, "selectedProfileId"), textualObject(parser, node, "config"), textualObject(parser, node, "accountScope"),
                text(parser, node, "reviewReference"), text(parser, node, "operationKey"));
        }
    }
    public static final class BindReader extends JsonDeserializer<BindRequest> {
        @Override public BindRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, Set.of("versionId", "targetProfileId", "bindingSlotId", "selectedProfileId", "approvalId", "expectedEntitlementRevision", "operationKey", "credentials"));
            return new BindRequest(text(parser, node, "versionId"), text(parser, node, "targetProfileId"), text(parser, node, "bindingSlotId"),
                text(parser, node, "selectedProfileId"), text(parser, node, "approvalId"), revision(parser, node, "expectedEntitlementRevision"),
                text(parser, node, "operationKey"), textualObject(parser, node, "credentials"));
        }
    }
    public static final class RevokeReader extends JsonDeserializer<RevokeRequest> {
        @Override public RevokeRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, Set.of("versionId", "targetProfileId", "bindingSlotId", "selectedProfileId", "bindingId", "expectedBindingRevision", "expectedEntitlementRevision", "operationKey"));
            return new RevokeRequest(text(parser, node, "versionId"), text(parser, node, "targetProfileId"), text(parser, node, "bindingSlotId"),
                text(parser, node, "selectedProfileId"), text(parser, node, "bindingId"), revision(parser, node, "expectedBindingRevision"),
                revision(parser, node, "expectedEntitlementRevision"), text(parser, node, "operationKey"));
        }
    }
    private static ObjectNode object(JsonParser parser, Set<String> fields) throws IOException {
        JsonNode tree = parser.getCodec().readTree(parser);
        if (!(tree instanceof ObjectNode object)) throw invalid(parser);
        Set<String> actual = new HashSet<>(); object.fieldNames().forEachRemaining(actual::add);
        if (!fields.equals(actual)) throw invalid(parser);
        return object;
    }
    private static String text(JsonParser parser, ObjectNode node, String field) throws JsonMappingException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid(parser);
        return value.textValue();
    }
    private static long revision(JsonParser parser, ObjectNode node, String field) throws JsonMappingException {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw invalid(parser);
        return value.longValue();
    }
    private static JsonNode textualObject(JsonParser parser, ObjectNode node, String field) throws JsonMappingException {
        JsonNode value = node.path(field);
        if (!value.isObject() || value.isEmpty() || value.size() > 32) throw invalid(parser);
        for (JsonNode item : value) if (!item.isTextual() || item.textValue().isBlank()) throw invalid(parser);
        return value.deepCopy();
    }
    private static JsonMappingException invalid(JsonParser parser) { return JsonMappingException.from(parser, "Invalid external integration request"); }
}
