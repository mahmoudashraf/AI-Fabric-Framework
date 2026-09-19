package com.ai.fabric.platform.backend.deployment.behavior;

import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentExecutionExtensionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URI;

@Service
public class DeploymentBehaviorCatalogService {

    public static final String SCHEMA_VERSION = "loomai-deployment-behavior-v1";
    public static final int CONTRACT_VERSION = 1;
    public static final String SPECIALIST_BUNDLE_CONTRACT_VERSION =
        "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1";
    private static final String AGENTIC_SPECIALIST_BUNDLE_ID =
        "deployment-intelligence-team@1";
    private static final String AGENTIC_SPECIALIST_BUNDLE_HASH_V100 =
        "sha256:00b9f8f582195eb18857361d94c02c48ab703e72a9a5d70d9e4c2cd8ea51a0d8";
    private static final String AGENTIC_SPECIALIST_BUNDLE_HASH_V101 =
        "sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025";
    private static final String SMART_BRAIN_SPECIALIST_BUNDLE_ID =
        "smart-brain-event-analysis@1";
    private static final String SMART_BRAIN_SPECIALIST_BUNDLE_HASH_V100 =
        "sha256:1059173cfb1fe7e0794e971af43d373c4d047a02e9fb50b77c6fe0328094e61d";

    private final ObjectMapper objectMapper;
    private final Map<DeploymentBehaviorType, BehaviorContract> contracts;
    private final Map<String, ExecutionExtensionContract> extensionContracts;

    public DeploymentBehaviorCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.contracts = buildContracts();
        this.extensionContracts = buildExtensionContracts();
    }

    public List<DeploymentBehaviorSummary> list() {
        return contracts.values().stream().map(this::toSummary).toList();
    }

    public List<DeploymentExecutionExtensionSummary> listExecutionExtensions() {
        return extensionContracts.values().stream().map(this::toSummary).toList();
    }

    public DeploymentBehaviorSummary requireSummary(String behaviorType) {
        return toSummary(requireContract(DeploymentBehaviorType.require(behaviorType)));
    }

    public JsonNode defaultConfig(String behaviorType) {
        return defaultConfig(DeploymentBehaviorType.require(behaviorType));
    }

    public JsonNode defaultConfig(DeploymentBehaviorType behaviorType) {
        BehaviorContract contract = requireContract(behaviorType);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("type", behaviorType.name());
        root.put("contractVersion", CONTRACT_VERSION);

        ObjectNode activation = root.putObject("activation");
        activation.set("sources", strings(contract.activationSources()));

        root.set("channelBindings", strings(contract.channelBindings()));
        root.set("executionExtensions", objectMapper.createArrayNode());
        root.set("specialistBundles", objectMapper.createArrayNode());

        ObjectNode durability = root.putObject("durability");
        durability.put("mode", contract.durabilityMode());

        ObjectNode runtime = root.putObject("runtimeRequirements");
        runtime.set("capabilities", strings(contract.requiredRuntimeCapabilities()));
        runtime.set("endpointClasses", strings(contract.requiredRuntimeEndpointClasses()));
        runtime.set("migrationIds", strings(contract.requiredRuntimeMigrationIds()));
        runtime.set("verificationPackIds", strings(contract.baselineVerificationPackIds()));

        ObjectNode authority = root.putObject("authority");
        authority.put("trustedContextRequired", true);
        authority.put("requestMaySelectIdentityOrAuthority", false);
        authority.put("automaticWritesAllowed", false);
        if (behaviorType == DeploymentBehaviorType.SMART_BRAIN) {
            ObjectNode smartBrain = root.putObject("smartBrain");
            smartBrain.put("contractVersion", "LOOMAI_SMART_BRAIN_CONFIG_V1");
            smartBrain.put("maxEventBytes", 262_144);
            ObjectNode trigger = smartBrain.putArray("triggers").addObject();
            trigger.put("code", "event-analysis");
            trigger.put("name", "Event analysis");
            trigger.putArray("eventTypes").add("com.loomai.smart-brain.analysis.requested");
            trigger.put("specialistRef", "smart-brain-event-analyst@1");
            trigger.put("enabled", true);
            smartBrain.putArray("schedules");
            ObjectNode delivery = smartBrain.putObject("delivery");
            delivery.put("mode", "POLL");
            delivery.putNull("callbackUrl");
        }
        return root;
    }

    public Validation validate(JsonNode config, String expectedBehaviorType) {
        DeploymentBehaviorType expected;
        try {
            expected = DeploymentBehaviorType.require(expectedBehaviorType);
        } catch (ResponseStatusException ex) {
            return Validation.invalid("BEHAVIOR_TYPE_UNSUPPORTED", ex.getReason());
        }
        if (config == null || !config.isObject()) {
            return Validation.invalid("BEHAVIOR_CONFIG_OBJECT_REQUIRED", "behaviorConfig must be an object.");
        }
        if (!SCHEMA_VERSION.equals(config.path("schemaVersion").asText(""))) {
            return Validation.invalid(
                "BEHAVIOR_SCHEMA_VERSION_UNSUPPORTED",
                "behaviorConfig.schemaVersion must be " + SCHEMA_VERSION + "."
            );
        }
        if (config.path("contractVersion").asInt(-1) != CONTRACT_VERSION) {
            return Validation.invalid(
                "BEHAVIOR_CONTRACT_VERSION_UNSUPPORTED",
                "behaviorConfig.contractVersion must be " + CONTRACT_VERSION + "."
            );
        }
        DeploymentBehaviorType configured;
        try {
            configured = DeploymentBehaviorType.require(config.path("type").asText(""));
        } catch (ResponseStatusException ex) {
            return Validation.invalid("BEHAVIOR_TYPE_UNSUPPORTED", ex.getReason());
        }
        if (configured != expected) {
            return Validation.invalid(
                "BEHAVIOR_TYPE_MISMATCH",
                "behaviorConfig.type must match deployment behaviorType " + expected.name() + "."
            );
        }

        BehaviorContract contract = requireContract(expected);
        Validation activation = validateSelection(
            config.path("activation").path("sources"),
            contract.activationSources(),
            true,
            "BEHAVIOR_ACTIVATION_SOURCE_UNSUPPORTED",
            "activation.sources"
        );
        if (!activation.valid()) {
            return activation;
        }
        Validation channels = validateSelection(
            config.path("channelBindings"),
            contract.channelBindings(),
            true,
            "BEHAVIOR_CHANNEL_UNSUPPORTED",
            "channelBindings"
        );
        if (!channels.valid()) {
            return channels;
        }
        Validation extensions = validateSelection(
            config.path("executionExtensions"),
            contract.allowedExecutionExtensions(),
            false,
            "BEHAVIOR_EXTENSION_UNSUPPORTED",
            "executionExtensions"
        );
        if (!extensions.valid()) {
            return extensions;
        }
        if (!contract.durabilityMode().equals(config.path("durability").path("mode").asText(""))) {
            return Validation.invalid(
                "BEHAVIOR_DURABILITY_MISMATCH",
                "durability.mode is fixed by the selected deployment behavior."
            );
        }
        Validation runtimeCapabilities = requireExactValues(
            config.path("runtimeRequirements").path("capabilities"),
            contract.requiredRuntimeCapabilities(),
            "BEHAVIOR_RUNTIME_CAPABILITIES_MISMATCH",
            "runtimeRequirements.capabilities"
        );
        if (!runtimeCapabilities.valid()) {
            return runtimeCapabilities;
        }
        Validation endpointClasses = requireExactValues(
            config.path("runtimeRequirements").path("endpointClasses"),
            contract.requiredRuntimeEndpointClasses(),
            "BEHAVIOR_RUNTIME_ENDPOINTS_MISMATCH",
            "runtimeRequirements.endpointClasses"
        );
        if (!endpointClasses.valid()) {
            return endpointClasses;
        }
        Validation migrationIds = requireExactValues(
            config.path("runtimeRequirements").path("migrationIds"),
            contract.requiredRuntimeMigrationIds(),
            "BEHAVIOR_RUNTIME_MIGRATIONS_MISMATCH",
            "runtimeRequirements.migrationIds"
        );
        if (!migrationIds.valid()) {
            return migrationIds;
        }
        Validation verificationPacks = requireExactValues(
            config.path("runtimeRequirements").path("verificationPackIds"),
            contract.baselineVerificationPackIds(),
            "BEHAVIOR_VERIFICATION_PACKS_MISMATCH",
            "runtimeRequirements.verificationPackIds"
        );
        if (!verificationPacks.valid()) {
            return verificationPacks;
        }
        Validation specialistBundles = validateSpecialistBundles(
            config.path("specialistBundles"),
            contract.requiredSpecialistBundles()
        );
        if (!specialistBundles.valid()) {
            return specialistBundles;
        }
        JsonNode authority = config.path("authority");
        if (!authority.path("trustedContextRequired").asBoolean(false)
            || authority.path("requestMaySelectIdentityOrAuthority").asBoolean(true)
            || authority.path("automaticWritesAllowed").asBoolean(true)) {
            return Validation.invalid(
                "BEHAVIOR_AUTHORITY_CONTRACT_MISMATCH",
                "The deployment behavior authority boundary cannot be widened by draft configuration."
            );
        }
        if (expected == DeploymentBehaviorType.SMART_BRAIN) {
            Validation smartBrain = validateSmartBrain(config.path("smartBrain"));
            if (!smartBrain.valid()) {
                return smartBrain;
            }
        }
        return Validation.valid(config.deepCopy());
    }

    private Validation validateSpecialistBundles(JsonNode configured,
                                                  List<DeploymentSpecialistBundleSummary> required) {
        if (configured.isMissingNode() || configured.isNull()) {
            configured = objectMapper.createArrayNode();
        }
        if (!configured.isArray()) {
            return Validation.invalid(
                "BEHAVIOR_SPECIALIST_BUNDLES_INVALID",
                "specialistBundles must be an array."
            );
        }
        if (configured.size() != required.size()) {
            return Validation.invalid(
                "BEHAVIOR_SPECIALIST_BUNDLES_REQUIRED",
                required.isEmpty()
                    ? "The selected behavior does not accept specialist bundles."
                    : "Install the exact required specialist bundle for the selected behavior."
            );
        }
        Map<String, DeploymentSpecialistBundleSummary> expected = required.stream()
            .collect(java.util.stream.Collectors.toMap(
                DeploymentSpecialistBundleSummary::bundleId,
                value -> value,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (JsonNode item : configured) {
            if (!item.isObject()) {
                return Validation.invalid(
                    "BEHAVIOR_SPECIALIST_BUNDLES_INVALID",
                    "Every specialist bundle selection must be an object."
                );
            }
            String bundleId = item.path("bundleId").asText("").trim();
            DeploymentSpecialistBundleSummary requirement = expected.get(bundleId);
            if (requirement == null || !seen.add(bundleId)) {
                return Validation.invalid(
                    "BEHAVIOR_SPECIALIST_BUNDLES_INVALID",
                    "specialistBundles contains an unsupported or duplicate bundle: " + bundleId
                );
            }
            if (!SPECIALIST_BUNDLE_CONTRACT_VERSION.equals(item.path("contractVersion").asText(""))
                || !supportedSpecialistBundleHashes(bundleId).contains(item.path("contentHash").asText(""))
                || !exactStrings(item.path("specialistRefs"), requirement.specialistRefs())
                || !exactStrings(item.path("chainRefs"), requirement.chainRefs())
                || !StringUtils.hasText(item.path("marketplacePluginId").asText(""))
                || !StringUtils.hasText(item.path("marketplacePluginVersionId").asText(""))
                || !StringUtils.hasText(item.path("marketplacePluginVersion").asText(""))
                || !StringUtils.hasText(item.path("marketplaceInstallId").asText(""))
                || !item.path("marketplaceManaged").asBoolean(false)) {
                return Validation.invalid(
                    "BEHAVIOR_SPECIALIST_BUNDLES_INVALID",
                    "Specialist bundle selection does not match the reviewed behavior contract: " + bundleId
                );
            }
        }
        return Validation.valid(configured.deepCopy());
    }

    public boolean supportsSpecialistBundle(String behaviorType,
                                            String bundleId,
                                            String contractVersion,
                                            String contentHash,
                                            List<String> specialistRefs,
                                            List<String> chainRefs) {
        BehaviorContract behavior = requireContract(
            DeploymentBehaviorType.require(behaviorType)
        );
        return behavior.requiredSpecialistBundles().stream().anyMatch(expected ->
            expected.bundleId().equals(bundleId)
                && expected.contractVersion().equals(contractVersion)
                && supportedSpecialistBundleHashes(bundleId).contains(contentHash)
                && expected.specialistRefs().equals(specialistRefs)
                && expected.chainRefs().equals(chainRefs)
        );
    }

    private Set<String> supportedSpecialistBundleHashes(String bundleId) {
        return switch (bundleId) {
            case AGENTIC_SPECIALIST_BUNDLE_ID -> Set.of(
                AGENTIC_SPECIALIST_BUNDLE_HASH_V100,
                AGENTIC_SPECIALIST_BUNDLE_HASH_V101
            );
            case SMART_BRAIN_SPECIALIST_BUNDLE_ID -> Set.of(
                SMART_BRAIN_SPECIALIST_BUNDLE_HASH_V100
            );
            default -> Set.of();
        };
    }

    private boolean exactStrings(JsonNode values, List<String> expected) {
        if (!values.isArray()) {
            return false;
        }
        LinkedHashSet<String> actual = new LinkedHashSet<>();
        values.forEach(value -> actual.add(value.asText("")));
        return actual.size() == values.size() && actual.equals(new LinkedHashSet<>(expected));
    }

    private Validation validateSmartBrain(JsonNode config) {
        if (!config.isObject()
            || !"LOOMAI_SMART_BRAIN_CONFIG_V1".equals(config.path("contractVersion").asText(""))) {
            return Validation.invalid(
                "SMART_BRAIN_CONFIG_INVALID",
                "smartBrain.contractVersion must be LOOMAI_SMART_BRAIN_CONFIG_V1."
            );
        }
        int maxEventBytes = config.path("maxEventBytes").asInt(-1);
        if (maxEventBytes < 1_024 || maxEventBytes > 1_048_576) {
            return Validation.invalid(
                "SMART_BRAIN_EVENT_LIMIT_INVALID",
                "smartBrain.maxEventBytes must be between 1024 and 1048576."
            );
        }
        JsonNode triggers = config.path("triggers");
        if (!triggers.isArray() || triggers.isEmpty() || triggers.size() > 32) {
            return Validation.invalid(
                "SMART_BRAIN_TRIGGER_REGISTRY_INVALID",
                "smartBrain.triggers must contain between 1 and 32 registered triggers."
            );
        }
        LinkedHashSet<String> triggerCodes = new LinkedHashSet<>();
        for (JsonNode trigger : triggers) {
            String code = trigger.path("code").asText("").trim();
            if (!code.matches("^[a-z][a-z0-9-]{1,63}$") || !triggerCodes.add(code)) {
                return Validation.invalid(
                    "SMART_BRAIN_TRIGGER_REGISTRY_INVALID",
                    "Every Smart Brain trigger requires a unique lower-kebab-case code."
                );
            }
            if (!"smart-brain-event-analyst@1".equals(trigger.path("specialistRef").asText(""))) {
                return Validation.invalid(
                    "SMART_BRAIN_SPECIALIST_UNSUPPORTED",
                    "Smart Brain V1 triggers use the server-owned smart-brain-event-analyst@1 specialist."
                );
            }
            JsonNode eventTypes = trigger.path("eventTypes");
            if (!eventTypes.isArray() || eventTypes.isEmpty() || eventTypes.size() > 16) {
                return Validation.invalid(
                    "SMART_BRAIN_EVENT_TYPES_INVALID",
                    "Every Smart Brain trigger requires between 1 and 16 event types."
                );
            }
            LinkedHashSet<String> types = new LinkedHashSet<>();
            for (JsonNode eventType : eventTypes) {
                String value = eventType.asText("").trim();
                if (!value.matches("^[A-Za-z0-9][A-Za-z0-9._/-]{1,199}$") || !types.add(value)) {
                    return Validation.invalid(
                        "SMART_BRAIN_EVENT_TYPES_INVALID",
                        "Smart Brain event types must be unique stable identifiers."
                    );
                }
            }
        }
        JsonNode schedules = config.path("schedules");
        if (!schedules.isArray() || schedules.size() > 16) {
            return Validation.invalid(
                "SMART_BRAIN_SCHEDULES_INVALID",
                "smartBrain.schedules must be an array with at most 16 schedules."
            );
        }
        LinkedHashSet<String> scheduleCodes = new LinkedHashSet<>();
        for (JsonNode schedule : schedules) {
            String code = schedule.path("code").asText("").trim();
            String triggerCode = schedule.path("triggerCode").asText("").trim();
            String cron = schedule.path("cron").asText("").trim();
            String zoneId = schedule.path("zoneId").asText("").trim();
            if (!code.matches("^[a-z][a-z0-9-]{1,63}$")
                || !scheduleCodes.add(code)
                || !triggerCodes.contains(triggerCode)
                || cron.length() < 9
                || cron.length() > 120
                || zoneId.isEmpty()
                || zoneId.length() > 80) {
                return Validation.invalid(
                    "SMART_BRAIN_SCHEDULES_INVALID",
                    "Every schedule requires a unique code, registered triggerCode, Quartz cron expression, and zoneId."
                );
            }
            try {
                java.time.ZoneId.of(zoneId);
            } catch (RuntimeException exception) {
                return Validation.invalid("SMART_BRAIN_SCHEDULES_INVALID", "Smart Brain schedule zoneId is invalid.");
            }
        }
        JsonNode delivery = config.path("delivery");
        String mode = delivery.path("mode").asText("");
        if (!Set.of("POLL", "SIGNED_WEBHOOK").contains(mode)) {
            return Validation.invalid(
                "SMART_BRAIN_DELIVERY_INVALID",
                "smartBrain.delivery.mode must be POLL or SIGNED_WEBHOOK."
            );
        }
        String callbackUrl = delivery.path("callbackUrl").asText("").trim();
        if ("SIGNED_WEBHOOK".equals(mode)) {
            try {
                URI uri = URI.create(callbackUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException exception) {
                return Validation.invalid(
                    "SMART_BRAIN_DELIVERY_INVALID",
                    "Signed webhook delivery requires an HTTPS callback URL without embedded credentials."
                );
            }
        } else if (!callbackUrl.isEmpty()) {
            return Validation.invalid(
                "SMART_BRAIN_DELIVERY_INVALID",
                "POLL delivery must not declare a callback URL."
            );
        }
        return Validation.valid(config.deepCopy());
    }

    public RuntimeRequirements releaseRequirements(JsonNode behaviorConfig) {
        DeploymentBehaviorType behaviorType = DeploymentBehaviorType.require(
            behaviorConfig == null ? null : behaviorConfig.path("type").asText("")
        );
        BehaviorContract behavior = requireContract(behaviorType);
        LinkedHashSet<String> capabilities = new LinkedHashSet<>(behavior.requiredRuntimeCapabilities());
        LinkedHashSet<String> endpointClasses = new LinkedHashSet<>(behavior.requiredRuntimeEndpointClasses());
        LinkedHashSet<String> migrationIds = new LinkedHashSet<>(behavior.requiredRuntimeMigrationIds());
        LinkedHashSet<String> verificationPackIds = new LinkedHashSet<>(behavior.baselineVerificationPackIds());
        LinkedHashSet<String> executionExtensions = stringSet(behaviorConfig.path("executionExtensions"));
        for (String extensionCode : executionExtensions) {
            ExecutionExtensionContract extension = requireExecutionExtension(extensionCode);
            if (!extension.compatibleBehaviorTypes().contains(behaviorType)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    extensionCode + " is not compatible with " + behaviorType.name() + "."
                );
            }
            capabilities.addAll(extension.requiredRuntimeCapabilities());
            endpointClasses.addAll(extension.requiredRuntimeEndpointClasses());
            migrationIds.addAll(extension.requiredRuntimeMigrationIds());
            verificationPackIds.addAll(extension.verificationPackIds());
        }
        return new RuntimeRequirements(
            behaviorType.name(),
            List.copyOf(stringSet(behaviorConfig.path("activation").path("sources"))),
            List.copyOf(stringSet(behaviorConfig.path("channelBindings"))),
            List.copyOf(executionExtensions),
            List.copyOf(capabilities),
            List.copyOf(endpointClasses),
            List.copyOf(migrationIds),
            List.copyOf(verificationPackIds),
            selectedSpecialistBundles(
                behaviorConfig.path("specialistBundles"),
                behavior.requiredSpecialistBundles()
            ),
            behavior.releaseRequiresCapabilityManifest() || !executionExtensions.isEmpty()
        );
    }

    private List<DeploymentSpecialistBundleSummary> selectedSpecialistBundles(
        JsonNode configured,
        List<DeploymentSpecialistBundleSummary> required
    ) {
        if (required.isEmpty()) {
            return List.of();
        }
        if (!configured.isArray()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The deployment behavior does not contain its reviewed specialist bundle selection."
            );
        }
        Map<String, JsonNode> selected = new LinkedHashMap<>();
        configured.forEach(item -> selected.put(item.path("bundleId").asText(""), item));
        return required.stream().map(expected -> {
            JsonNode item = selected.get(expected.bundleId());
            if (item == null || !supportedSpecialistBundleHashes(expected.bundleId())
                .contains(item.path("contentHash").asText(""))) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The deployment behavior specialist bundle selection is not a reviewed revision: "
                        + expected.bundleId()
                );
            }
            return new DeploymentSpecialistBundleSummary(
                expected.bundleId(),
                expected.contractVersion(),
                item.path("contentHash").asText(),
                expected.behaviorTypes(),
                expected.specialistRefs(),
                expected.chainRefs(),
                expected.resourceLocations()
            );
        }).toList();
    }

    public BehaviorContract requireContract(DeploymentBehaviorType behaviorType) {
        BehaviorContract contract = contracts.get(behaviorType);
        if (contract == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported behaviorType: " + behaviorType);
        }
        return contract;
    }

    private Validation validateSelection(JsonNode node,
                                         List<String> allowed,
                                         boolean requireNonEmpty,
                                         String code,
                                         String field) {
        if (!node.isArray()) {
            return Validation.invalid(code, field + " must be an array.");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (value.isEmpty() || !allowed.contains(value) || !values.add(value)) {
                return Validation.invalid(code, field + " contains an unsupported or duplicate value: " + value);
            }
        }
        if (requireNonEmpty && values.isEmpty()) {
            return Validation.invalid(code, field + " must select at least one value.");
        }
        return Validation.valid(node.deepCopy());
    }

    private Validation requireExactValues(JsonNode node,
                                          List<String> expected,
                                          String code,
                                          String field) {
        Validation selection = validateSelection(node, expected, !expected.isEmpty(), code, field);
        if (!selection.valid()) {
            return selection;
        }
        Set<String> actual = new LinkedHashSet<>();
        node.forEach(item -> actual.add(item.asText()));
        if (!actual.equals(new LinkedHashSet<>(expected))) {
            return Validation.invalid(code, field + " is server-owned and must match the selected behavior contract.");
        }
        return selection;
    }

    private DeploymentBehaviorSummary toSummary(BehaviorContract contract) {
        return new DeploymentBehaviorSummary(
            contract.type().name(),
            contract.name(),
            contract.description(),
            SCHEMA_VERSION,
            CONTRACT_VERSION,
            contract.maturity(),
            true,
            contract.releaseRequiresCapabilityManifest(),
            contract.availabilityMessage(),
            contract.activationSources(),
            contract.channelBindings(),
            contract.allowedExecutionExtensions(),
            contract.requiredRuntimeCapabilities(),
            contract.requiredRuntimeEndpointClasses(),
            contract.requiredRuntimeMigrationIds(),
            contract.baselineVerificationPackIds(),
            contract.requiredSpecialistBundles(),
            defaultConfig(contract.type())
        );
    }

    private DeploymentExecutionExtensionSummary toSummary(ExecutionExtensionContract contract) {
        return new DeploymentExecutionExtensionSummary(
            contract.code(),
            contract.name(),
            contract.description(),
            contract.maturity(),
            contract.availabilityMessage(),
            contract.compatibleBehaviorTypes().stream().map(Enum::name).toList(),
            contract.requiredRuntimeCapabilities(),
            contract.requiredRuntimeEndpointClasses(),
            contract.requiredRuntimeMigrationIds(),
            contract.verificationPackIds()
        );
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode out = objectMapper.createArrayNode();
        values.forEach(out::add);
        return out;
    }

    private Map<DeploymentBehaviorType, BehaviorContract> buildContracts() {
        Map<DeploymentBehaviorType, BehaviorContract> result = new LinkedHashMap<>();
        result.put(DeploymentBehaviorType.CONVERSATIONAL, new BehaviorContract(
            DeploymentBehaviorType.CONVERSATIONAL,
            "Conversational Assistant",
            "A person asks and receives a bounded answer, clarification, structured result, or governed next step.",
            "HOSTED_PROVEN",
            false,
            "Ready on the current verified LoomAI runtime.",
            List.of("AUTHENTICATED_INTERACTIVE"),
            List.of("BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT", "QUERY_ONCE"),
            List.of("GOVERNED_RESOLVER", "HUMAN_REVIEW"),
            "OPTIONAL_BACKEND_SESSION",
            List.of("ai-fabric-core", "ai-fabric-chat-session"),
            List.of("chat-query", "query-once", "session-management"),
            List.of(),
            List.of("conversational-behavior-v1"),
            List.of()
        ));
        result.put(DeploymentBehaviorType.AGENTIC_SPECIALIST_TEAM, new BehaviorContract(
            DeploymentBehaviorType.AGENTIC_SPECIALIST_TEAM,
            "Agentic Specialist Team",
            "A bounded manager coordinates exact-version, read-only specialists for a larger task.",
            "PLATFORM_SELECTABLE",
            true,
            "Authoring is available. Apply requires a reviewed source artifact that proves durable specialist-chain support.",
            List.of("AUTHENTICATED_INTERACTIVE", "TRUSTED_APPLICATION"),
            List.of("BACKEND_API", "MAX_MODE"),
            List.of("HUMAN_REVIEW"),
            "JDBC_CHECKPOINTED",
            List.of("ai-fabric-execution", "specialist-chains", "jdbc-specialist-chain-state"),
            List.of("chain-execute", "chain-submit", "chain-status-result", "chain-cancel"),
            List.of("ai-specialist-chain-execution-v1"),
            List.of("agentic-specialist-team-v1"),
            List.of(agenticSpecialistBundle())
        ));
        result.put(DeploymentBehaviorType.SMART_BRAIN, new BehaviorContract(
            DeploymentBehaviorType.SMART_BRAIN,
            "Smart Brain",
            "Trusted events or schedules initiate durable, proactive read-only analysis without fabricating a chat turn.",
            "PLATFORM_SELECTABLE",
            true,
            "Authoring is available. Apply requires a reviewed source artifact that proves deployment-local event ingress and durable result delivery.",
            List.of("TRUSTED_APPLICATION", "CLOUD_EVENT", "SCHEDULED"),
            List.of("BACKEND_API", "SIGNED_WEBHOOK"),
            List.of(),
            "DURABLE_JOB",
            List.of("ai-fabric-execution", "smart-brain-durable-operations", "cloudevents-ingress", "deployment-result-store"),
            List.of("trigger-submit", "operation-status-result", "operation-cancel"),
            List.of(
                "ai-specialist-execution-v1",
                "loomai-smart-brain-operation-v1",
                "loomai-smart-brain-delivery-v1",
                "loomai-smart-brain-scheduler-v1"
            ),
            List.of("smart-brain-behavior-v1"),
            List.of(smartBrainSpecialistBundle())
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private DeploymentSpecialistBundleSummary agenticSpecialistBundle() {
        return new DeploymentSpecialistBundleSummary(
            AGENTIC_SPECIALIST_BUNDLE_ID,
            SPECIALIST_BUNDLE_CONTRACT_VERSION,
            AGENTIC_SPECIALIST_BUNDLE_HASH_V101,
            List.of(DeploymentBehaviorType.AGENTIC_SPECIALIST_TEAM.name()),
            List.of(
                "deployment-intelligence-manager@1",
                "deployment-knowledge-specialist@1",
                "deployment-runtime-state-specialist@1"
            ),
            List.of("deployment-intelligence-team@1"),
            List.of(
                "classpath:ai-chains/deployment-intelligence-team.yml",
                "classpath:ai-specialists/deployment-intelligence-team.yml",
                "classpath:ai-specialists/deployment-knowledge-specialist.yml"
            )
        );
    }

    private DeploymentSpecialistBundleSummary smartBrainSpecialistBundle() {
        return new DeploymentSpecialistBundleSummary(
            SMART_BRAIN_SPECIALIST_BUNDLE_ID,
            SPECIALIST_BUNDLE_CONTRACT_VERSION,
            SMART_BRAIN_SPECIALIST_BUNDLE_HASH_V100,
            List.of(DeploymentBehaviorType.SMART_BRAIN.name()),
            List.of("smart-brain-event-analyst@1"),
            List.of(),
            List.of("classpath:ai-specialists/smart-brain-event-analyst.yml")
        );
    }

    private Map<String, ExecutionExtensionContract> buildExtensionContracts() {
        Map<String, ExecutionExtensionContract> result = new LinkedHashMap<>();
        result.put("GOVERNED_RESOLVER", new ExecutionExtensionContract(
            "GOVERNED_RESOLVER",
            "Governed Resolver",
            "Turns one approved proposal into a registered, revalidated application action with a durable receipt.",
            "PLATFORM_SELECTABLE",
            "Apply requires a reviewed runtime artifact that exposes the governed proposal and receipt lifecycle.",
            List.of(DeploymentBehaviorType.CONVERSATIONAL),
            List.of("ai-fabric-execution", "governed-action-proposals", "jdbc-action-receipts"),
            List.of("action-proposal-submit", "action-proposal-confirm", "action-receipt-status"),
            List.of("ai-action-proposal-receipt-v1"),
            List.of("governed-resolver-v1")
        ));
        result.put("HUMAN_REVIEW", new ExecutionExtensionContract(
            "HUMAN_REVIEW",
            "Human Review",
            "Routes an action proposal into a durable, separately authorized reviewer decision lifecycle.",
            "PLATFORM_SELECTABLE",
            "Apply requires durable review tables, customer-owned reviewer authorization, safe review APIs, and a reviewed source artifact.",
            List.of(DeploymentBehaviorType.CONVERSATIONAL, DeploymentBehaviorType.AGENTIC_SPECIALIST_TEAM),
            List.of("ai-fabric-execution", "action-proposal-human-review", "jdbc-review-state"),
            List.of("review-inbox", "review-detail", "review-decision", "review-outcome"),
            List.of("ai-action-proposal-receipt-v1", "ai-review-task-v1", "ai-review-dispatch-v1"),
            List.of("human-review-action-proposal-v1")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private ExecutionExtensionContract requireExecutionExtension(String code) {
        ExecutionExtensionContract contract = extensionContracts.get(code);
        if (contract == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported execution extension: " + code);
        }
        return contract;
    }

    private LinkedHashSet<String> stringSet(JsonNode values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            values.forEach(value -> {
                String normalized = value.asText("").trim();
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            });
        }
        return result;
    }

    public record BehaviorContract(
        DeploymentBehaviorType type,
        String name,
        String description,
        String maturity,
        boolean releaseRequiresCapabilityManifest,
        String availabilityMessage,
        List<String> activationSources,
        List<String> channelBindings,
        List<String> allowedExecutionExtensions,
        String durabilityMode,
        List<String> requiredRuntimeCapabilities,
        List<String> requiredRuntimeEndpointClasses,
        List<String> requiredRuntimeMigrationIds,
        List<String> baselineVerificationPackIds,
        List<DeploymentSpecialistBundleSummary> requiredSpecialistBundles
    ) {
    }

    public record RuntimeRequirements(
        String behaviorType,
        List<String> activationSources,
        List<String> channelBindings,
        List<String> executionExtensions,
        List<String> capabilities,
        List<String> endpointClasses,
        List<String> migrationIds,
        List<String> verificationPackIds,
        List<DeploymentSpecialistBundleSummary> specialistBundles,
        boolean capabilityManifestRequired
    ) {
    }

    private record ExecutionExtensionContract(
        String code,
        String name,
        String description,
        String maturity,
        String availabilityMessage,
        List<DeploymentBehaviorType> compatibleBehaviorTypes,
        List<String> requiredRuntimeCapabilities,
        List<String> requiredRuntimeEndpointClasses,
        List<String> requiredRuntimeMigrationIds,
        List<String> verificationPackIds
    ) {
    }

    public record Validation(boolean valid, String code, String message, JsonNode normalizedConfig) {
        static Validation valid(JsonNode normalizedConfig) {
            return new Validation(true, null, null, normalizedConfig);
        }

        static Validation invalid(String code, String message) {
            return new Validation(false, code, message, null);
        }
    }
}
