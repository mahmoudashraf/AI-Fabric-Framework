package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;

/** Strictness is local to the transfer boundary, including duplicate keys before tree conversion. */
@Component
public class UnusedVectorTransferJson {
    private final ObjectMapper mapper = JsonMapper.builder()
        .findAndAddModules().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS, MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
            MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES).build();

    public UnusedVectorTransferJson() {
        for (CoercionInputShape shape : new CoercionInputShape[] {
            CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean })
            mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
    }

    public <T> T read(String value, Class<T> type) {
        try {
            if (value == null || value.length() > 131_072) throw new IllegalArgumentException();
            return mapper.readValue(value, type);
        } catch (Exception ex) { throw new IllegalArgumentException("UNUSED_VECTOR_TRANSFER_JSON_INVALID"); }
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("UNUSED_VECTOR_TRANSFER_JSON_INVALID"); }
    }
    public String hash(Object value) { return DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(value)); }
    public String bytesHash(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public String receiptHash(Object value) {
        ObjectNode node = mapper.valueToTree(value); node.remove("fingerprint");
        return DeploymentEvidenceFingerprint.hash(mapper, node);
    }
    public <T> T seal(T value, Class<T> type) {
        ObjectNode node = mapper.valueToTree(value); node.put("fingerprint", receiptHash(value));
        return read(node.toString(), type);
    }
}
