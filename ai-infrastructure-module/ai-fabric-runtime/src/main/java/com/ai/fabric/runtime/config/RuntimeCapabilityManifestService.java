package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeCapabilityManifestService {

    public static final String SCHEMA_VERSION = "loomai-runtime-capabilities-v1";
    public static final String SPECIALIST_BUNDLE_CONTRACT_VERSION =
        "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1";
    private static final String RESOURCE = "META-INF/loomai/runtime-capabilities.json";
    private static final Pattern CONTENT_HASH = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern RESOURCE_REF = Pattern.compile("[a-z][a-z0-9-]{1,79}@[A-Za-z0-9][A-Za-z0-9._-]{0,39}");
    private static final Set<String> BEHAVIOR_TYPES = Set.of(
        "CONVERSATIONAL",
        "AGENTIC_SPECIALIST_TEAM",
        "SMART_BRAIN"
    );
    private static final Set<String> SPECIALIST_BUNDLE_FIELDS = Set.of(
        "bundleId",
        "contractVersion",
        "contentHash",
        "behaviorTypes",
        "specialistRefs",
        "chainRefs",
        "resourceLocations"
    );
    private static final List<String> ARRAY_FIELDS = List.of(
        "supportedBehaviorTypes",
        "supportedActivationSources",
        "supportedChannelBindings",
        "supportedExecutionExtensions",
        "capabilities",
        "endpointClasses",
        "migrationIds",
        "verificationPackIds"
    );

    private final ObjectMapper objectMapper;
    private ObjectNode manifest;
    private String manifestHash;

    public RuntimeCapabilityManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Packaged runtime capability manifest is missing: " + RESOURCE);
            }
            JsonNode raw = objectMapper.readTree(input);
            if (raw == null || !raw.isObject()) {
                throw new IllegalStateException("Packaged runtime capability manifest must be an object.");
            }
            if (!SCHEMA_VERSION.equals(raw.path("schemaVersion").asText(""))) {
                throw new IllegalStateException("Unsupported packaged runtime capability manifest schema.");
            }
            String aiFabricVersion = raw.path("aiFabricVersion").asText("").trim();
            if (!StringUtils.hasText(aiFabricVersion)) {
                throw new IllegalStateException("Packaged runtime capability manifest requires aiFabricVersion.");
            }
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("schemaVersion", SCHEMA_VERSION);
            normalized.put("aiFabricVersion", aiFabricVersion);
            for (String field : ARRAY_FIELDS) {
                normalized.set(field, normalizedArray(raw.path(field), field));
            }
            normalized.set("specialistBundles", normalizedBundles(raw.path("specialistBundles")));
            if (normalized.path("supportedBehaviorTypes").isEmpty()) {
                throw new IllegalStateException("Packaged runtime capability manifest requires a behavior type.");
            }
            verifyPackagedSpecialistBundles(normalized.path("specialistBundles"));
            this.manifest = normalized;
            this.manifestHash = sha256(objectMapper.writeValueAsString(normalized));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load packaged runtime capability manifest.", ex);
        }
    }

    public JsonNode manifest() {
        return manifest.deepCopy();
    }

    public Map<String, Object> manifestProjection() {
        return objectMapper.convertValue(
            manifest,
            new TypeReference<Map<String, Object>>() { }
        );
    }

    public String manifestHash() {
        return manifestHash;
    }

    private ArrayNode normalizedArray(JsonNode raw, String field) {
        if (!raw.isArray()) {
            throw new IllegalStateException("Packaged runtime capability manifest " + field + " must be an array.");
        }
        Set<String> values = new LinkedHashSet<>();
        raw.forEach(entry -> {
            String value = entry.asText("").trim();
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException("Packaged runtime capability manifest " + field + " has a blank value.");
            }
            values.add(value);
        });
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        ArrayNode result = objectMapper.createArrayNode();
        sorted.forEach(result::add);
        return result;
    }

    private ArrayNode normalizedBundles(JsonNode raw) {
        if (!raw.isArray()) {
            throw new IllegalStateException("Packaged runtime capability manifest specialistBundles must be an array.");
        }
        Set<String> ids = new LinkedHashSet<>();
        List<ObjectNode> bundles = new ArrayList<>();
        for (JsonNode item : raw) {
            if (!item.isObject()) {
                throw new IllegalStateException("Every packaged specialist bundle must be an object.");
            }
            item.fieldNames().forEachRemaining(field -> {
                if (!SPECIALIST_BUNDLE_FIELDS.contains(field)) {
                    throw new IllegalStateException("Unsupported packaged specialist bundle field: " + field);
                }
            });
            String bundleId = item.path("bundleId").asText("").trim();
            if (!RESOURCE_REF.matcher(bundleId).matches() || !ids.add(bundleId)) {
                throw new IllegalStateException("Every packaged specialist bundle requires a unique bundleId.");
            }
            String contractVersion = item.path("contractVersion").asText("").trim();
            if (!SPECIALIST_BUNDLE_CONTRACT_VERSION.equals(contractVersion)) {
                throw new IllegalStateException("Unsupported packaged specialist bundle contract version.");
            }
            String contentHash = item.path("contentHash").asText("").trim();
            if (!CONTENT_HASH.matcher(contentHash).matches()) {
                throw new IllegalStateException("Packaged specialist bundle contentHash is invalid.");
            }
            ArrayNode behaviorTypes = normalizedArray(item.path("behaviorTypes"), "specialistBundles.behaviorTypes");
            if (behaviorTypes.isEmpty()) {
                throw new IllegalStateException("Packaged specialist bundle behaviorTypes must not be empty.");
            }
            behaviorTypes.forEach(value -> {
                if (!BEHAVIOR_TYPES.contains(value.asText())) {
                    throw new IllegalStateException("Packaged specialist bundle behavior type is unsupported.");
                }
            });
            ArrayNode specialistRefs = normalizedRefs(item.path("specialistRefs"), true);
            ArrayNode chainRefs = normalizedRefs(item.path("chainRefs"), false);
            ArrayNode locations = normalizedArray(
                item.path("resourceLocations"),
                "specialistBundles.resourceLocations"
            );
            if (locations.isEmpty()) {
                throw new IllegalStateException("Packaged specialist bundle resourceLocations must not be empty.");
            }
            locations.forEach(value -> validateResourceLocation(value.asText()));

            ObjectNode bundle = objectMapper.createObjectNode();
            bundle.put("bundleId", bundleId);
            bundle.put("contractVersion", contractVersion);
            bundle.put("contentHash", contentHash);
            bundle.set("behaviorTypes", behaviorTypes);
            bundle.set("specialistRefs", specialistRefs);
            bundle.set("chainRefs", chainRefs);
            bundle.set("resourceLocations", locations);
            bundles.add(bundle);
        }
        ArrayNode result = objectMapper.createArrayNode();
        bundles.stream()
            .sorted(Comparator.comparing(bundle -> bundle.path("bundleId").asText()))
            .forEach(result::add);
        return result;
    }

    private ArrayNode normalizedRefs(JsonNode raw, boolean required) {
        ArrayNode refs = normalizedArray(raw, "specialistBundles.resourceRefs");
        if (required && refs.isEmpty()) {
            throw new IllegalStateException("Packaged specialist bundle specialistRefs must not be empty.");
        }
        refs.forEach(value -> {
            if (!RESOURCE_REF.matcher(value.asText()).matches()) {
                throw new IllegalStateException("Packaged specialist bundle resource reference is invalid.");
            }
        });
        return refs;
    }

    private void validateResourceLocation(String location) {
        if (!location.startsWith("classpath:ai-")
            || location.contains("..")
            || !(location.endsWith(".yml") || location.endsWith(".yaml") || location.endsWith(".json"))) {
            throw new IllegalStateException("Packaged specialist bundle resource location is invalid.");
        }
    }

    private void verifyPackagedSpecialistBundles(JsonNode bundles) {
        for (JsonNode bundle : bundles) {
            String expected = bundle.path("contentHash").asText();
            String actual = packagedBundleHash(bundle.path("resourceLocations"));
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                    "Packaged specialist bundle hash mismatch for " + bundle.path("bundleId").asText()
                        + ": expected " + expected + " but calculated " + actual
                );
            }
        }
    }

    private String packagedBundleHash(JsonNode locations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] newline = "\n".getBytes(StandardCharsets.UTF_8);
            for (JsonNode value : locations) {
                String location = value.asText();
                digest.update(location.getBytes(StandardCharsets.UTF_8));
                digest.update(newline);
                String resourcePath = location.substring("classpath:".length());
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (input == null) {
                        throw new IllegalStateException("Packaged specialist resource is missing: " + location);
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                        }
                    }
                }
                digest.update(newline);
            }
            return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash packaged specialist resources.", ex);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash packaged runtime capability manifest.", ex);
        }
    }
}
