package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorType;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DeploymentSourceCapabilityManifestService {

    public static final String SCHEMA_VERSION = "loomai-runtime-capabilities-v1";
    private static final Pattern CONTENT_HASH = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern RESOURCE_REF = Pattern.compile("[a-z][a-z0-9-]{1,79}@[A-Za-z0-9][A-Za-z0-9._-]{0,39}");
    private static final Set<String> MANIFEST_FIELDS = Set.of(
        "schemaVersion",
        "aiFabricVersion",
        "supportedBehaviorTypes",
        "supportedActivationSources",
        "supportedChannelBindings",
        "supportedExecutionExtensions",
        "capabilities",
        "endpointClasses",
        "migrationIds",
        "verificationPackIds",
        "specialistBundles"
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

    public DeploymentSourceCapabilityManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedCapabilityManifest normalize(JsonNode candidate) {
        if (candidate == null || candidate.isNull()) {
            return new NormalizedCapabilityManifest(objectMapper.createObjectNode(), null);
        }
        if (!candidate.isObject()) {
            throw badRequest("capabilityManifest must be an object.");
        }
        candidate.fieldNames().forEachRemaining(field -> {
            if (!MANIFEST_FIELDS.contains(field)) {
                throw badRequest("Unsupported capabilityManifest field: " + field);
            }
        });
        if (!SCHEMA_VERSION.equals(candidate.path("schemaVersion").asText(""))) {
            throw badRequest("capabilityManifest.schemaVersion must be " + SCHEMA_VERSION + ".");
        }
        if (!StringUtils.hasText(candidate.path("aiFabricVersion").asText(""))) {
            throw badRequest("capabilityManifest.aiFabricVersion is required.");
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("schemaVersion", SCHEMA_VERSION);
        normalized.put("aiFabricVersion", candidate.path("aiFabricVersion").asText().trim());
        for (String field : ARRAY_FIELDS) {
            normalized.set(field, normalizeStringArray(candidate.path(field), field));
        }
        normalized.set("specialistBundles", normalizeSpecialistBundles(candidate.path("specialistBundles")));
        for (JsonNode behavior : normalized.path("supportedBehaviorTypes")) {
            DeploymentBehaviorType.require(behavior.asText());
        }
        try {
            String json = objectMapper.writeValueAsString(normalized);
            return new NormalizedCapabilityManifest(normalized, sha256(json));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize source capability manifest.", ex);
        }
    }

    public JsonNode read(String value) {
        try {
            JsonNode node = objectMapper.readTree(value == null ? "{}" : value);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalStateException("Stored source capability manifest is invalid.", ex);
        }
    }

    public void requireSupports(JsonNode manifest,
                                DeploymentBehaviorCatalogService.RuntimeRequirements requirements) {
        if (manifest == null || !manifest.isObject() || manifest.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This deployment behavior requires a source artifact with a reviewed capability manifest."
            );
        }
        requireContains(
            manifest.path("supportedBehaviorTypes"),
            List.of(requirements.behaviorType()),
            "supportedBehaviorTypes"
        );
        requireContains(
            manifest.path("supportedActivationSources"),
            requirements.activationSources(),
            "supportedActivationSources"
        );
        requireContains(
            manifest.path("supportedChannelBindings"),
            requirements.channelBindings(),
            "supportedChannelBindings"
        );
        requireContains(
            manifest.path("supportedExecutionExtensions"),
            requirements.executionExtensions(),
            "supportedExecutionExtensions"
        );
        requireContains(manifest.path("capabilities"), requirements.capabilities(), "capabilities");
        requireContains(manifest.path("endpointClasses"), requirements.endpointClasses(), "endpointClasses");
        requireContains(manifest.path("migrationIds"), requirements.migrationIds(), "migrationIds");
        requireContains(
            manifest.path("verificationPackIds"),
            requirements.verificationPackIds(),
            "verificationPackIds"
        );
        requireSpecialistBundles(manifest.path("specialistBundles"), requirements.specialistBundles());
    }

    private ArrayNode normalizeSpecialistBundles(JsonNode node) {
        ArrayNode result = objectMapper.createArrayNode();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return result;
        }
        if (!node.isArray()) {
            throw badRequest("capabilityManifest.specialistBundles must be an array.");
        }
        Set<String> ids = new LinkedHashSet<>();
        List<ObjectNode> bundles = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw badRequest("Every capabilityManifest.specialistBundles entry must be an object.");
            }
            item.fieldNames().forEachRemaining(field -> {
                if (!SPECIALIST_BUNDLE_FIELDS.contains(field)) {
                    throw badRequest("Unsupported specialist bundle field: " + field);
                }
            });
            String bundleId = item.path("bundleId").asText("").trim();
            if (!RESOURCE_REF.matcher(bundleId).matches() || !ids.add(bundleId)) {
                throw badRequest("Every specialist bundle requires a unique stable bundleId.");
            }
            String contractVersion = item.path("contractVersion").asText("").trim();
            if (!DeploymentBehaviorCatalogService.SPECIALIST_BUNDLE_CONTRACT_VERSION.equals(contractVersion)) {
                throw badRequest(
                    "Specialist bundle contractVersion must be "
                        + DeploymentBehaviorCatalogService.SPECIALIST_BUNDLE_CONTRACT_VERSION + "."
                );
            }
            String contentHash = item.path("contentHash").asText("").trim();
            if (!CONTENT_HASH.matcher(contentHash).matches()) {
                throw badRequest("Specialist bundle contentHash must be a lowercase sha256 hash.");
            }
            ArrayNode behaviorTypes = normalizeStringArray(item.path("behaviorTypes"), "specialistBundles.behaviorTypes");
            if (behaviorTypes.isEmpty()) {
                throw badRequest("Every specialist bundle requires at least one behavior type.");
            }
            behaviorTypes.forEach(behavior -> DeploymentBehaviorType.require(behavior.asText()));
            ArrayNode specialistRefs = normalizeResourceRefs(
                item.path("specialistRefs"),
                "specialistBundles.specialistRefs",
                true
            );
            ArrayNode chainRefs = normalizeResourceRefs(
                item.path("chainRefs"),
                "specialistBundles.chainRefs",
                false
            );
            ArrayNode resourceLocations = normalizeResourceLocations(item.path("resourceLocations"));
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("bundleId", bundleId);
            normalized.put("contractVersion", contractVersion);
            normalized.put("contentHash", contentHash);
            normalized.set("behaviorTypes", behaviorTypes);
            normalized.set("specialistRefs", specialistRefs);
            normalized.set("chainRefs", chainRefs);
            normalized.set("resourceLocations", resourceLocations);
            bundles.add(normalized);
        }
        bundles.stream()
            .sorted(Comparator.comparing(bundle -> bundle.path("bundleId").asText()))
            .forEach(result::add);
        return result;
    }

    private ArrayNode normalizeResourceRefs(JsonNode node, String field, boolean requireNonEmpty) {
        ArrayNode values = normalizeStringArray(node, field);
        if (requireNonEmpty && values.isEmpty()) {
            throw badRequest("capabilityManifest." + field + " must not be empty.");
        }
        values.forEach(value -> {
            if (!RESOURCE_REF.matcher(value.asText()).matches()) {
                throw badRequest("capabilityManifest." + field + " contains an invalid resource reference.");
            }
        });
        return values;
    }

    private ArrayNode normalizeResourceLocations(JsonNode node) {
        ArrayNode values = normalizeStringArray(node, "specialistBundles.resourceLocations");
        if (values.isEmpty()) {
            throw badRequest("Every specialist bundle requires at least one resource location.");
        }
        values.forEach(value -> {
            String location = value.asText();
            if (!location.startsWith("classpath:ai-")
                || location.contains("..")
                || !(location.endsWith(".yml") || location.endsWith(".yaml") || location.endsWith(".json"))) {
                throw badRequest("Specialist bundle resource locations must be fixed classpath AI resources.");
            }
        });
        return values;
    }

    private void requireSpecialistBundles(JsonNode node,
                                          List<DeploymentSpecialistBundleSummary> required) {
        Map<String, JsonNode> actual = new java.util.LinkedHashMap<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> actual.put(item.path("bundleId").asText(""), item));
        }
        List<String> missing = new ArrayList<>();
        for (DeploymentSpecialistBundleSummary requirement : required) {
            JsonNode bundle = actual.get(requirement.bundleId());
            if (bundle == null
                || !requirement.contractVersion().equals(bundle.path("contractVersion").asText(""))
                || !requirement.contentHash().equals(bundle.path("contentHash").asText(""))
                || !sameStrings(bundle.path("behaviorTypes"), requirement.behaviorTypes())
                || !sameStrings(bundle.path("specialistRefs"), requirement.specialistRefs())
                || !sameStrings(bundle.path("chainRefs"), requirement.chainRefs())
                || !sameStrings(bundle.path("resourceLocations"), requirement.resourceLocations())) {
                missing.add(requirement.bundleId());
            }
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Source artifact capability manifest is missing exact specialist bundles: "
                    + String.join(", ", missing)
            );
        }
    }

    private boolean sameStrings(JsonNode node, List<String> expected) {
        if (!node.isArray()) {
            return false;
        }
        LinkedHashSet<String> actual = new LinkedHashSet<>();
        node.forEach(item -> actual.add(item.asText("")));
        return actual.size() == node.size() && actual.equals(new LinkedHashSet<>(expected));
    }

    private ArrayNode normalizeStringArray(JsonNode node, String field) {
        if (!node.isArray()) {
            throw badRequest("capabilityManifest." + field + " must be an array.");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (!StringUtils.hasText(value)) {
                throw badRequest("capabilityManifest." + field + " cannot contain blank values.");
            }
            values.add(value);
        }
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        ArrayNode result = objectMapper.createArrayNode();
        sorted.forEach(result::add);
        return result;
    }

    private void requireContains(JsonNode node, List<String> required, String field) {
        Set<String> actual = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> actual.add(item.asText()));
        }
        List<String> missing = required.stream().filter(item -> !actual.contains(item)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Source artifact capability manifest is missing " + field + ": " + String.join(", ", missing)
            );
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash source capability manifest.", ex);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record NormalizedCapabilityManifest(JsonNode manifest, String hash) {
    }
}
