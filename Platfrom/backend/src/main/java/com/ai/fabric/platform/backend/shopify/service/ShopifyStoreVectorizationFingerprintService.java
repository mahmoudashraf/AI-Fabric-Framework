package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ShopifyStoreVectorizationFingerprintService {

    private final ShopifyStoreVectorizationFieldCatalogService fieldCatalogService;
    private final ObjectMapper objectMapper;

    public ShopifyStoreVectorizationFingerprintService(ShopifyStoreVectorizationFieldCatalogService fieldCatalogService,
                                                       ObjectMapper objectMapper) {
        this.fieldCatalogService = fieldCatalogService;
        this.objectMapper = objectMapper;
    }

    public FingerprintResult fingerprintRecord(String sourceCategory,
                                               Map<String, Object> normalizedRecord,
                                               VectorizationOverviewSummary overview) {
        Map<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> fieldValues =
            fieldCatalogService.fieldValuesForRecord(sourceCategory, normalizedRecord, overview);
        return fingerprintFieldValues(fieldValues);
    }

    public FingerprintResult fingerprintRecordSet(String sourceCategory,
                                                  List<Map<String, Object>> normalizedRecords,
                                                  VectorizationOverviewSummary overview) {
        List<Map<String, Object>> records = normalizedRecords == null ? List.of() : normalizedRecords.stream()
            .sorted(Comparator.comparing(record -> value(record, "id")))
            .toList();
        Map<String, List<String>> aggregatedValues = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            Map<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> fieldValues =
                fieldCatalogService.fieldValuesForRecord(sourceCategory, record, overview);
            for (Map.Entry<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> entry : fieldValues.entrySet()) {
                aggregatedValues.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                    .add(value(record, "id") + ":" + normalize(entry.getValue().value()));
            }
        }
        LinkedHashMap<String, String> fieldFingerprints = new LinkedHashMap<>();
        aggregatedValues.forEach((fieldKey, values) -> fieldFingerprints.put(fieldKey, sha256(values)));
        return fingerprintFieldMap(fieldFingerprints);
    }

    public boolean selectedFieldsChanged(Map<String, String> previousFieldFingerprints,
                                         Map<String, String> currentFieldFingerprints,
                                         List<String> selectedFieldKeys) {
        List<String> fields = selectedFieldKeys == null || selectedFieldKeys.isEmpty()
            ? List.of()
            : selectedFieldKeys;
        for (String fieldKey : fields) {
            String previous = previousFieldFingerprints == null ? null : previousFieldFingerprints.get(fieldKey);
            String current = currentFieldFingerprints == null ? null : currentFieldFingerprints.get(fieldKey);
            if (!safeEquals(previous, current)) {
                return true;
            }
        }
        return false;
    }

    public FingerprintResult fingerprintFieldValues(Map<String, ShopifyStoreVectorizationFieldSummarySupport.FieldValue> fieldValues) {
        LinkedHashMap<String, String> fieldFingerprints = new LinkedHashMap<>();
        if (fieldValues != null) {
            fieldValues.forEach((fieldKey, value) -> fieldFingerprints.put(fieldKey, sha256(normalize(value.value()))));
        }
        return fingerprintFieldMap(fieldFingerprints);
    }

    public FingerprintResult fingerprintAggregatedValues(Map<String, List<String>> valuesByFieldKey) {
        LinkedHashMap<String, String> fieldFingerprints = new LinkedHashMap<>();
        if (valuesByFieldKey != null) {
            valuesByFieldKey.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> fieldFingerprints.put(entry.getKey(), sha256(List.copyOf(entry.getValue()))));
        }
        return fingerprintFieldMap(fieldFingerprints);
    }

    private FingerprintResult fingerprintFieldMap(Map<String, String> fieldFingerprints) {
        LinkedHashMap<String, String> normalizedMap = new LinkedHashMap<>();
        if (fieldFingerprints != null) {
            fieldFingerprints.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> normalizedMap.put(entry.getKey(), entry.getValue()));
        }
        return new FingerprintResult(sha256(normalizedMap), Map.copyOf(normalizedMap));
    }

    private String sha256(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = objectMapper.writeValueAsBytes(value instanceof Map<?, ?> map ? new TreeMap<>(map) : value);
            byte[] result = digest.digest(input);
            StringBuilder builder = new StringBuilder(result.length * 2);
            for (byte current : result) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Shopify vectorization fingerprint.", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String value(Map<String, Object> source, String key) {
        if (source == null) {
            return "";
        }
        Object value = source.get(key);
        return value == null ? "" : value.toString();
    }

    private boolean safeEquals(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public record FingerprintResult(
        String fullFingerprint,
        Map<String, String> fieldFingerprints
    ) {
    }
}
