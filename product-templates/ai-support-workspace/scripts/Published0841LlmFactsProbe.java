import ai.fabric.intent.IntentExtractionJsonSupport;
import ai.fabric.intent.action.AIActionHandler;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionObjectPayload;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.http.OutboundHttpExecutionResponse;
import ai.fabric.http.OutboundHttpExecutor;
import ai.fabric.intent.action.connector.AIActionCatalogProperties;
import ai.fabric.intent.action.connector.AIActionConnectorProperties;
import ai.fabric.intent.action.connector.ActionConnectorExecutor;
import ai.fabric.intent.action.connector.ConnectorAIActionHandler;
import ai.fabric.intent.action.connector.ConnectorActionCatalogLoader;
import ai.fabric.intent.action.connector.ConnectorActionDefinition;
import ai.fabric.intent.action.connector.ConnectorActionLlmFactsDefinition;
import ai.fabric.intent.action.connector.ConnectorActionMetadataMapper;
import ai.fabric.intent.orchestration.information.ReadActionResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Offline sidecar: real connector execution with fake HTTP, no network, providers or product classes. */
public final class Published0841LlmFactsProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CHARS = 2400;
    private static final String AT = "2026-10-30T22:30:00Z";
    private static final String SECRET = "PROBE_SECRET_MUST_NOT_REACH_MODEL";
    private static final String RAW_ERROR = "PROBE_RAW_ERROR_MUST_NOT_REACH_MODEL";
    private static final String DEPLOYMENT = "support_get_deployment_status";
    private static final String READINESS = "support_get_engage_readiness";
    private static final String CASE = "support_get_case_summary";
    private static final String SYNC = "support_get_connection_sync_status";
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private final List<Map<String, Object>> knownLimitations = new ArrayList<>();
    private final ReadActionResolutionService resolution = new ReadActionResolutionService(
        null, null, new IntentExtractionJsonSupport(JSON), null, null);
    private final Method evidenceMethod;

    private Published0841LlmFactsProbe() throws Exception {
        // Reflection is limited to this standalone probe, invoking the actual private evidence path.
        evidenceMethod = ReadActionResolutionService.class.getDeclaredMethod("buildEvidenceSnippet",
            AIActionHandler.class, ActionResult.class, ActionContext.class, int.class);
        evidenceMethod.setAccessible(true);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("Expected current/legacy/wrong-root manifest paths and optional diagnostic proof");
        var artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("connector", publishedJar(ConnectorActionCatalogLoader.class, "ai-fabric-actions-connector"));
        artifacts.put("handler", publishedJar(ConnectorAIActionHandler.class, "ai-fabric-actions-connector"));
        artifacts.put("executor", publishedJar(ActionConnectorExecutor.class, "ai-fabric-actions-connector"));
        artifacts.put("factsRecord", publishedJar(ConnectorActionLlmFactsDefinition.class, "ai-fabric-actions-connector"));
        artifacts.put("core", publishedJar(ReadActionResolutionService.class, "ai-fabric-core"));
        Path currentPath = Path.of(args[0]);
        Path legacyPath = Path.of(args[1]);
        Path wrongRootPath = Path.of(args[2]);
        var probe = new Published0841LlmFactsProbe();
        Path diagnosticPath = args.length == 4 ? Path.of(args[3]) : null;
        probe.run(JSON.readTree(currentPath.toFile()), JSON.readTree(legacyPath.toFile()),
            JSON.readTree(wrongRootPath.toFile()), diagnosticPath);
        long failures = probe.checks.stream().filter(c -> "FAIL".equals(c.get("status"))).count();
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failures == 0 ? "PASS" : "FAIL");
        report.put("contract", "Required facts and visible truncation, not full checklist preservation or live readiness");
        report.put("executionPath", "ConnectorAIActionHandler.executeAction -> ActionConnectorExecutor.execute -> published response parser -> ActionObjectPayload -> published LLM facts and bounded evidence");
        report.put("httpTransport", "Fixture-only OutboundHttpExecutor; no network calls or text-based routing");
        report.put("passed", probe.checks.size() - failures);
        report.put("failed", failures);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("maxEvidenceChars", MAX_CHARS);
        report.put("artifacts", artifacts);
        report.put("manifests", List.of(fileIdentity(currentPath), fileIdentity(legacyPath), fileIdentity(wrongRootPath)));
        if (diagnosticPath != null) report.put("diagnosticProof", fileIdentity(diagnosticPath));
        report.put("checks", probe.checks);
        report.put("observations", probe.observations);
        report.put("knownLimitations", probe.knownLimitations);
        System.out.println("LLM_FACTS_PROBE_RESULT=" + JSON.writeValueAsString(report));
        if (failures != 0) System.exit(1);
    }

    private void run(JsonNode currentManifest, JsonNode legacyManifest, JsonNode wrongRootManifest, Path diagnosticPath) throws Exception {
        require("1.0.3".equals(currentManifest.path("version").asText()), "Expected manifest 1.0.3");
        require("1.0.1".equals(legacyManifest.path("version").asText()), "Expected legacy manifest 1.0.1");
        require("1.0.2".equals(wrongRootManifest.path("version").asText()), "Expected wrong-root manifest 1.0.2");
        var current = load(currentManifest, "READ");
        var legacy = load(legacyManifest, "READ");
        var wrongRoot = load(wrongRootManifest, "READ");
        check("loader.current-four-public-facts-records", () -> {
            require(current.size() == 4, "Expected four loaded actions, got " + current.size());
            for (var definition : current.values()) {
                require(definition.accessMode().isReadOnly(), definition.name() + " is not READ");
                require(definition.readActionResolutionEligible(), definition.name() + " is not planner eligible");
                require(definition.llmFacts() != null && definition.llmFacts().getClass() == ConnectorActionLlmFactsDefinition.class,
                    definition.name() + " did not load the real SDK facts record");
                require(definition.llmFacts().rootPath() == null || definition.llmFacts().rootPath().isBlank(),
                    "Current facts must use the already-unwrapped payload");
            }
        });
        check("loader.current-projections-have-no-query-routing-or-ranking-rules", () -> {
            for (var action : currentManifest.path("contributions").path("actions")) {
                require(action.path("params").isArray() && action.path("params").isEmpty(),
                    "Case-selected diagnostics should not require query-derived parameters");
                require(action.path("llmFacts").findValues("rankRules").isEmpty()
                    && action.path("llmFacts").findValues("constraints").isEmpty(),
                    "This probe must exercise structural evidence projection, not query-based rules");
            }
        });
        check("loader.literal-READ_ONLY-is-rejected", () -> {
            try {
                load(currentManifest, "READ_ONLY");
                throw new AssertionError("Unexpected READ_ONLY acceptance");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("accessMode must be one of READ, READ_WRITE, WRITE_ONLY"),
                    "Unexpected loader rejection: " + expected.getMessage());
            }
        });

        if (diagnosticPath != null) {
            var proof = JSON.readTree(diagnosticPath.toFile());
            require(proof.path("status").asInt() == 200 && proof.path("value").isObject(), "Expected a saved {status:200,value:{...}} proof");
            require(DEPLOYMENT.equals(proof.path("value").path("action").asText()), "Not a deployment diagnostic proof");
            // The saved value is the raw facade projection, not an ActionResult or connector envelope.
            var actual = connectorEnvelope((ObjectNode) proof.path("value"));
            var observed = observe("saved-service-authenticated-deployment-result", current.get(DEPLOYMENT), actual);
            var expected = proof.at("/value/resources/0/facts/documentIndexing/0");
            check("saved-proof.direct-exact-index-facts-and-release", () -> savedProof(observed.facts(), expected));
            check("saved-proof.bounded-exact-index-facts-and-release", () -> savedProof(observed.evidence(), expected));
            check("saved-proof.bounded-three-release-history", () -> {
                expect(observed.evidence().path("deploymentObservations").path(0), "id",
                    proof.at("/value/resources/0/resource/id").asText());
                require(observed.evidence().path("releaseObservations").size() == 3, "Real release history not retained to declared cap");
                expect(observed.evidence().path("releaseObservations").path(0), "releaseId", expected.path("releaseId").asText());
            });
        }

        var single = observe("deployment-current-four", current.get(DEPLOYMENT), deployment(false, false, false));
        check("deployment.direct-four-active-current-no-newer", () -> currentFour(single.facts()));
        check("deployment.bounded-four-active-current-no-newer", () -> currentFour(single.evidence()));
        check("deployment.bounded-release-and-resource", () -> releaseAndResource(single.evidence(), false));

        var unavailable = observe("deployment-index-unavailable", current.get(DEPLOYMENT), deployment(false, true, false));
        check("unavailable.direct-incomplete-without-invented-zero", () -> unavailable(unavailable.facts()));
        check("unavailable.bounded-incomplete-without-invented-zero", () -> unavailable(unavailable.evidence()));

        var empty = observe("deployment-explicit-empty-index", current.get(DEPLOYMENT), deployment(false, false, true));
        check("empty-index.direct-explicit-zero-distinct-from-unavailable", () -> emptyIndex(empty.facts()));
        check("empty-index.bounded-explicit-zero-distinct-from-unavailable", () -> emptyIndex(empty.evidence()));

        var multiple = observe("deployment-two-targets-first-selected", current.get(DEPLOYMENT), deployment(true, false, false));
        check("targets.direct-both-first-deployment-target-observations", () -> targetObservations(multiple.facts()));
        check("targets.bounded-both-first-deployment-target-observations", () -> targetObservations(multiple.evidence()));
        check("targets.direct-release-and-resource-identities", () -> releaseAndResource(multiple.facts(), true));
        check("targets.bounded-release-and-resource-identities", () -> releaseAndResource(multiple.evidence(), true));
        check("targets.direct-single-selected-deployment-scope", () ->
            require(multiple.facts().path("deploymentObservations").size() == 1, "Expected exactly the one selected deployment"));

        readinessScenario(current.get(READINESS), "app-one-late-readiness-blocker", List.of("RUNTIME_CORS"));
        readinessScenario(current.get(READINESS), "app-all-readiness-checks-passed", List.of());
        readinessScenario(current.get(READINESS), "app-three-late-readiness-blockers",
            List.of("RUNTIME_URL", "PRIVATE_ASSERTION_KEY", "RUNTIME_CORS"));
        readinessScenario(current.get(READINESS), "app-five-late-readiness-blockers",
            List.of("APP_DOMAIN", "APP_DELIVERY", "RUNTIME_URL", "PRIVATE_ASSERTION_KEY", "RUNTIME_CORS"));

        var summary = observe("case-selected-resource-references", current.get(CASE), caseSummary());
        check("case.direct-selected-refs-and-consent", () -> caseReferences(summary.facts()));
        check("case.bounded-selected-refs-and-consent", () -> caseReferences(summary.evidence()));

        var sync = observe("connection-recorded-sync-status", current.get(SYNC), sync());
        check("sync.direct-recorded-status-and-no-live-provider-claim", () -> syncStatus(sync.facts()));
        check("sync.bounded-recorded-status-and-no-live-provider-claim", () -> syncStatus(sync.evidence()));

        var capped = deployment(false, false, false);
        var releaseList = (ArrayNode) capped.at("/data/resources/0/facts/releases");
        for (int i = 1; i <= 5; i++) releaseList.add(release("rel-older-" + i, "staging", "FAILED"));
        var cap = observe("deployment-three-release-cap", current.get(DEPLOYMENT), capped);
        check("limits.direct-manifest-three-release-cap", () -> {
            require(cap.facts().path("releaseObservations").size() == 3, "Manifest maxItems=3 not respected");
            expect(cap.facts(), "releaseObservationsCount", 3);
        });

        var legacyPayload = deployment(false, false, false);
        removeCanaries(legacyPayload);
        var old = observe("legacy-1.0.1-scalar-envelope-only", legacy.get(DEPLOYMENT), legacyPayload);
        check("legacy.no-explicit-facts-record", () -> {
            for (var definition : legacy.values()) require(definition.llmFacts() == null, "Legacy has explicit mapping");
        });
        check("legacy.direct-reproduces-nested-index-and-release-omission", () -> legacyOmission(old.facts()));
        check("legacy.bounded-reproduces-nested-index-and-release-omission", () -> legacyOmission(old.evidence()));

        var wrong = observe("wrong-root-1.0.2-envelope-only", wrongRoot.get(DEPLOYMENT), deployment(false, false, false), false);
        check("wrong-root.loader-explicit-data-root", () -> {
            for (var definition : wrongRoot.values())
                require(definition.llmFacts() != null && "data".equals(definition.llmFacts().rootPath()),
                    "Negative control must retain the 1.0.2 wrong root");
        });
        check("wrong-root.direct-data-present-but-facts-omitted", () -> wrongRootOmission(wrong.facts()));
        check("wrong-root.bounded-data-present-but-facts-omitted", () -> wrongRootOmission(wrong.evidence()));
    }

    private Observation observe(String name, ConnectorActionDefinition definition, ObjectNode envelope) throws Exception {
        return observe(name, definition, envelope, true);
    }

    private Observation observe(String name, ConnectorActionDefinition definition, ObjectNode envelope,
                                boolean expectedGroundingUsable) throws Exception {
        require(envelope.path("success").asBoolean() && envelope.path("data").isObject(),
            "Fixture must be a successful connector response envelope");
        String responseJson = JSON.writeValueAsString(envelope);
        var calls = new ArrayList<JsonNode>();
        OutboundHttpExecutor transport = request -> {
            require("https://offline.invalid/actions/execute".equals(request.url()), "Unexpected offline URL");
            require(org.springframework.http.HttpMethod.POST.equals(request.method()), "Expected connector POST");
            try { calls.add(JSON.readTree(request.body())); }
            catch (Exception failure) { throw new IllegalStateException("Invalid connector request JSON", failure); }
            return new OutboundHttpExecutionResponse(200, responseJson, Map.of());
        };
        var properties = new AIActionConnectorProperties();
        properties.setBaseUrl("https://offline.invalid");
        properties.setMaxAttempts(1);
        var executor = new ActionConnectorExecutor(properties, transport, null,
            Clock.fixed(Instant.parse(AT), ZoneOffset.UTC));
        var handler = new ConnectorAIActionHandler(ConnectorActionMetadataMapper.toMetadata(definition),
            definition.requiresConfirmation(), definition.confirmationMessage(),
            ConnectorActionMetadataMapper.extractSensitiveParams(definition), executor, definition.llmFacts());
        var result = handler.executeAction(Map.of(), null);
        JsonNode parsedPayload = JSON.valueToTree(result.getData());
        check(name + ".published-parser-unwraps-exactly-one-envelope", () -> {
            require(calls.size() == 1, "Expected exactly one fake HTTP invocation");
            expect(calls.getFirst(), "actionId", definition.name());
            require(calls.getFirst().path("params").isObject() && calls.getFirst().path("params").isEmpty(),
                "Unexpected action parameters");
            require(result.isSuccess() && result.getData() instanceof ActionObjectPayload,
                "Published executor did not return a successful object payload");
            require(parsedPayload.equals(envelope.path("data")), "Parser changed or double-wrapped the facade projection");
            require(!parsedPayload.has("data") && !parsedPayload.has("success") && !parsedPayload.has("httpStatus"),
                "Connector transport envelope remained in ActionObjectPayload");
        });
        JsonNode facts = JSON.valueToTree(handler.buildPostActionLlmFacts(result, null).orElseThrow());
        Object snippet = evidenceMethod.invoke(resolution, handler, result, null, MAX_CHARS);
        Method summary = snippet.getClass().getDeclaredMethod("summary");
        Method usable = snippet.getClass().getDeclaredMethod("groundingUsable");
        summary.setAccessible(true);
        usable.setAccessible(true);
        String evidenceJson = (String) summary.invoke(snippet);
        JsonNode evidence = JSON.readTree(evidenceJson);
        var observation = new LinkedHashMap<String, Object>();
        observation.put("fixture", name);
        observation.put("directChars", JSON.writeValueAsString(facts).length());
        observation.put("boundedChars", evidenceJson.length());
        observation.put("groundingUsable", usable.invoke(snippet));
        observation.put("parserPayloadType", result.getData().getClass().getSimpleName());
        observation.put("parserUnwrapped", parsedPayload.equals(envelope.path("data")));
        observation.put("fakeHttpCalls", calls.size());
        observation.put("parsedTopLevelFields", parsedPayload.properties().stream().map(Map.Entry::getKey).toList());
        observation.put("facts", facts);
        observation.put("evidence", evidence);
        observations.add(observation);
        check(name + ".bounded-valid-json-max2400", () -> {
            require(evidence.isObject() && evidenceJson.length() <= MAX_CHARS, "Invalid or oversized bounded JSON");
            require(Boolean.valueOf(expectedGroundingUsable).equals(usable.invoke(snippet)),
                "Expected groundingUsable=" + expectedGroundingUsable + " for this fixture");
        });
        if (!name.startsWith("legacy-")) {
            check(name + ".no-unselected-secrets-or-raw-errors", () -> {
                for (JsonNode projection : List.of(facts, evidence)) {
                    require(!projection.toString().contains(SECRET), "Secret canary leaked: " + projection);
                    require(!projection.toString().contains(RAW_ERROR), "Raw error canary leaked: " + projection);
                    require(projection.findValues("secret").isEmpty() && projection.findValues("rawError").isEmpty(),
                        "Unselected fields projected: " + projection);
                }
            });
        }
        return new Observation(facts, evidence);
    }

    private static Map<String, ConnectorActionDefinition> load(JsonNode manifest, String accessMode) throws Exception {
        var actions = JSON.createArrayNode();
        for (var entry : manifest.path("contributions").path("actions")) {
            ObjectNode action = entry.deepCopy();
            action.put("name", action.remove("actionId").asText());
            require(action.path("readOnly").asBoolean(), "Probe is limited to declared read-only actions");
            action.remove("readOnly");
            // Platform compileActionContribution emits READ, not the unsupported literal READ_ONLY.
            action.put("accessMode", accessMode);
            action.put("requiresConfirmation", action.path("confirmationRequired").asBoolean());
            action.remove("confirmationRequired");
            actions.add(action);
        }
        byte[] bytes = JSON.writeValueAsBytes(JSON.createObjectNode().set("actions", actions));
        var loader = new ConnectorActionCatalogLoader(new ResourceLoader() {
            public Resource getResource(String location) { return new ByteArrayResource(bytes, location); }
            public ClassLoader getClassLoader() { return Published0841LlmFactsProbe.class.getClassLoader(); }
        });
        var source = new AIActionCatalogProperties.ActionSourceProperties();
        source.setPath("memory:manifest-" + manifest.path("version").asText() + ".json");
        var definitions = new LinkedHashMap<String, ConnectorActionDefinition>();
        for (var definition : loader.loadActions(List.of(source))) definitions.put(definition.name(), definition);
        return definitions;
    }

    private static ObjectNode envelope(String action, String availability) {
        var data = JSON.createObjectNode();
        data.put("action", action).put("observedAt", AT).put("availability", availability);
        taint(data);
        return connectorEnvelope(data);
    }

    private static ObjectNode connectorEnvelope(ObjectNode projection) {
        var root = JSON.createObjectNode();
        root.put("success", true);
        root.set("data", projection);
        taint(root);
        return root;
    }

    private static ObjectNode deployment(boolean multiple, boolean unavailable, boolean empty) {
        var root = envelope(DEPLOYMENT, "CURRENT");
        var resources = ((ObjectNode) root.path("data")).putArray("resources");
        var first = resource(resources, "DEPLOYMENT", "dep-probe-first", "CURRENT");
        first.put("deploymentStatus", "READY").put("liveHealthProbed", false).put("stateUpdatedAt", AT);
        var releases = first.putArray("releases");
        if (multiple) releases.add(release("rel-prod-newer-failed", "production", "FAILED"));
        releases.add(release("rel-stage-good", "staging", "APPLIED_VERIFIED"));
        if (multiple) releases.add(release("rel-prod-good", "production", "APPLIED_VERIFIED"));
        var indexes = first.putArray("documentIndexing");
        if (unavailable) {
            var index = indexes.addObject();
            index.put("availability", "INCOMPLETE").put("code", "RUNTIME_STATUS_UNAVAILABLE")
                .put("observedAt", AT).put("upstreamStorageProbed", false);
            taint(index);
        } else {
            indexes.add(index("staging", "rel-stage-good", empty ? 0 : 4, 0));
            if (multiple) indexes.add(index("production", "rel-prod-good", 2, 1));
        }
        return root;
    }

    private static ObjectNode resource(ArrayNode resources, String type, String id, String availability) {
        var resource = resources.addObject();
        resource.putObject("resource").put("type", type).put("id", id);
        resource.put("availability", availability);
        taint(resource);
        var facts = resource.putObject("facts");
        taint(facts);
        return facts;
    }

    private static ObjectNode release(String id, String target, String status) {
        var release = JSON.createObjectNode();
        release.put("releaseId", id).put("targetProfileId", target).put("status", status)
            .put("verificationStatus", "FAILED".equals(status) ? "FAILED" : "PASSED")
            .put("appliedAt", AT).put("observedAt", AT);
        taint(release);
        return release;
    }

    private static ObjectNode index(String target, String release, int count, int newer) {
        var index = JSON.createObjectNode();
        index.put("availability", "CURRENT").put("targetProfileId", target).put("releaseId", release)
            .put("registeredSourceCount", count).put("sourcesWithActiveVersion", count)
            .put("sourcesWithNewerCandidate", newer).put("latestSourceStateUpdatedAt", AT)
            .put("observedAt", AT).put("observationBasis", "RUNTIME_REGISTERED_SOURCE_STATE")
            .put("upstreamStorageProbed", false)
            .put("guidance", "Registered index state does not prove that source storage has no newer content.");
        var statuses = index.putObject("sourcesByStatus");
        if (count > 0) statuses.put("ACTIVE", count);
        taint(index);
        return index;
    }

    private static final List<String> READINESS_CODES = List.of("CONSUMER", "ASSIGNMENT", "ASSETS",
        "EXPERIENCE_PACK", "CONNECTION_PROFILE", "ORIGINS", "AUTHENTICATION_PROFILE", "APP_DOMAIN",
        "APP_DELIVERY", "RUNTIME_URL", "PRIVATE_ASSERTION_KEY", "RUNTIME_CORS");

    private static ObjectNode readiness(List<String> blockedCodes) {
        var root = envelope(READINESS, "INCOMPLETE");
        var facts = resource(((ObjectNode) root.path("data")).putArray("resources"), "ENGAGE_APP", "awi-probe", "INCOMPLETE");
        facts.put("installationStatus", "ACTIVE").put("localReadinessPassed", blockedCodes.isEmpty()).put("deliveryProbed", false)
            .put("guidance", "Use the app readiness screen for explicit route and HTTPS verification.");
        var checks = facts.putArray("checks");
        for (var code : READINESS_CODES) {
            var check = checks.addObject().put("code", code).put("status", blockedCodes.contains(code) ? "BLOCKED" : "PASSED");
            taint(check);
        }
        return root;
    }

    private static ObjectNode caseSummary() {
        var root = envelope(CASE, "CURRENT");
        var summary = ((ObjectNode) root.path("data")).putObject("supportCase");
        summary.put("reference", "SUP-PROBE").put("summary", "Check selected deployment indexing and app readiness")
            .put("status", "OPEN").put("topic", "KNOWLEDGE_FRESHNESS");
        summary.putObject("consent").put("granted", true).put("grantedAt", AT);
        var refs = summary.putArray("resources");
        refs.addObject().put("type", "DEPLOYMENT").put("id", "dep-probe-first");
        refs.addObject().put("type", "ENGAGE_APP").put("id", "awi-probe");
        refs.addObject().put("type", "CONNECTION").put("id", "conn-probe");
        taint(summary);
        return root;
    }

    private static ObjectNode sync() {
        var root = envelope(SYNC, "CURRENT");
        var facts = resource(((ObjectNode) root.path("data")).putArray("resources"), "CONNECTION", "conn-probe", "CURRENT");
        facts.put("connectionStatus", "ACTIVE").put("connectionUpdatedAt", AT).put("syncState", "IN_SYNC")
            .put("stateUpdatedAt", AT).put("lastRunStatus", "COMPLETED").put("lastRunCompletedAt", AT)
            .put("lastSuccessfulSyncAt", AT).put("liveProviderProbed", false);
        return root;
    }

    private static void currentFour(JsonNode facts) {
        var index = facts.path("documentIndexSummary").path(0);
        expect(index, "availability", "CURRENT");
        expect(index, "registeredSourceCount", 4);
        expect(index.path("sourcesByStatus"), "ACTIVE", 4);
        expect(index, "sourcesWithActiveVersion", 4);
        expect(index, "sourcesWithNewerCandidate", 0);
        expect(index, "upstreamStorageProbed", false);
        expect(index, "observationBasis", "RUNTIME_REGISTERED_SOURCE_STATE");
        expect(index, "targetProfileId", "staging");
        expect(index, "releaseId", "rel-stage-good");
        require(index.path("guidance").asText().contains("does not prove"), "Storage freshness limitation lost");
    }

    private static void savedProof(JsonNode facts, JsonNode expected) {
        var index = facts.path("documentIndexSummary").path(0);
        expect(index, "availability", "CURRENT");
        expect(index, "registeredSourceCount", 4);
        expect(index.path("sourcesByStatus"), "ACTIVE", 4);
        expect(index, "sourcesWithActiveVersion", 4);
        expect(index, "sourcesWithNewerCandidate", 0);
        expect(index, "upstreamStorageProbed", false);
        for (var field : List.of("targetProfileId", "releaseId", "observedAt", "latestSourceStateUpdatedAt", "observationBasis", "guidance")) {
            require(expected.hasNonNull(field), "Saved proof missing " + field);
            expect(index, field, expected.path(field).asText());
        }
    }

    private static void unavailable(JsonNode facts) {
        var index = facts.path("documentIndexSummary").path(0);
        expect(index, "availability", "INCOMPLETE");
        expect(index, "code", "RUNTIME_STATUS_UNAVAILABLE");
        expect(index, "upstreamStorageProbed", false);
        for (var field : List.of("registeredSourceCount", "sourcesByStatus", "sourcesWithActiveVersion", "sourcesWithNewerCandidate"))
            require(!index.has(field), "Unavailable fabricated " + field + ": " + index);
    }

    private static void emptyIndex(JsonNode facts) {
        var index = facts.path("documentIndexSummary").path(0);
        expect(index, "availability", "CURRENT");
        expect(index, "registeredSourceCount", 0);
        expect(index, "sourcesWithActiveVersion", 0);
        expect(index, "sourcesWithNewerCandidate", 0);
        require(index.path("sourcesByStatus").isObject() && index.path("sourcesByStatus").isEmpty(), "Empty status map altered");
    }

    private static void targetObservations(JsonNode facts) {
        require(facts.path("documentIndexSummary").size() == 2, "Expected both target observations: " + facts);
        currentFour(facts);
        var production = facts.path("documentIndexSummary").path(1);
        expect(production, "targetProfileId", "production");
        expect(production, "releaseId", "rel-prod-good");
        expect(production, "registeredSourceCount", 2);
        expect(production, "sourcesWithNewerCandidate", 1);
    }

    private static void releaseAndResource(JsonNode facts, boolean multiple) {
        expect(facts.path("deploymentObservations").path(0), "id", "dep-probe-first");
        var releases = facts.path("releaseObservations");
        require(releases.isArray() && releases.size() == (multiple ? 3 : 1), "Release observations lost: " + facts);
        if (multiple) {
            expect(releases.path(0), "releaseId", "rel-prod-newer-failed");
            expect(releases.path(0), "targetProfileId", "production");
            expect(releases.path(0), "status", "FAILED");
            expect(releases.path(1), "releaseId", "rel-stage-good");
            expect(releases.path(1), "targetProfileId", "staging");
            expect(releases.path(2), "releaseId", "rel-prod-good");
        } else expect(releases.path(0), "releaseId", "rel-stage-good");
    }

    private void readinessScenario(ConnectorActionDefinition definition, String name, List<String> blockedCodes) throws Exception {
        var observed = observe(name, definition, readiness(blockedCodes));
        check(name + ".direct-all-twelve-general-checks", () -> readinessGeneral(observed.facts(), blockedCodes, false));
        check(name + ".bounded-five-general-checks-with-explicit-omissions", () -> readinessGeneral(observed.evidence(), blockedCodes, true));
        check(name + ".direct-overall-state-and-limitations", () -> readinessState(observed.facts(), blockedCodes.isEmpty()));
        check(name + ".bounded-overall-state-and-limitations", () -> readinessState(observed.evidence(), blockedCodes.isEmpty()));
        check(name + ".direct-no-invented-blocker-fields", () -> noInventedBlockerFields(observed.facts()));
        check(name + ".bounded-no-invented-blocker-fields", () -> noInventedBlockerFields(observed.evidence()));
        if (!blockedCodes.isEmpty()) {
            check(name + ".bounded-passed-subset-does-not-override-overall-false", () -> {
                var displayed = observed.evidence().path("readinessCheckSummary");
                for (int i = 0; i < 5; i++) expect(displayed.path(i), "status", "PASSED");
                require(displayed.findValuesAsText("code").stream().noneMatch(blockedCodes::contains),
                    "This late-blocker fixture must demonstrate omitted optional detail");
                readinessState(observed.evidence(), false);
            });
        }
        knownLimitations.add(Map.of(
            "kind", "KNOWN_SDK_BOUNDED_DETAIL_LIMITATION",
            "fixture", name,
            "omittedGeneralCheckCount", READINESS_CODES.size() - 5,
            "fixtureOnlyOmittedBlockerCodes", blockedCodes,
            "detail", "SDK bounded evidence shows five general checks plus _truncatedItems=7, not the full checklist. "
                + (blockedCodes.isEmpty()
                    ? "All-PASSED local state remains true, but delivery is unprobed and availability remains INCOMPLETE."
                    : "The listed fixture-only late blocker codes are omitted from model evidence; overall localReadinessPassed=false, "
                        + "availability=INCOMPLETE and deliveryProbed=false must remain. No blocker count is projected.")
        ));
    }

    private static void readinessGeneral(JsonNode facts, List<String> blockedCodes, boolean bounded) {
        var checks = facts.path("readinessCheckSummary");
        int displayed = bounded ? 5 : READINESS_CODES.size();
        require(checks.isArray() && checks.size() == (bounded ? displayed + 1 : displayed),
            "Expected " + displayed + " structured general checks" + (bounded ? " plus omission marker" : "") + "; got " + checks);
        expect(facts, "readinessCheckSummaryCount", READINESS_CODES.size());
        for (int i = 0; i < displayed; i++) {
            String code = READINESS_CODES.get(i);
            expect(checks.path(i), "code", code);
            expect(checks.path(i), "status", blockedCodes.contains(code) ? "BLOCKED" : "PASSED");
        }
        if (bounded) expect(checks.path(displayed), "_truncatedItems", READINESS_CODES.size() - displayed);
    }

    private static void readinessState(JsonNode facts, boolean localReady) {
        var app = facts.path("readinessObservations").path(0);
        expect(facts, "availability", "INCOMPLETE");
        expect(app, "id", "awi-probe");
        expect(app, "availability", "INCOMPLETE");
        expect(app, "installationStatus", "ACTIVE");
        expect(app, "localReadinessPassed", localReady);
        expect(app, "deliveryProbed", false);
        expect(app, "guidance", "Use the app readiness screen for explicit route and HTTPS verification.");
        require(!app.has("checks"), "Checks should be in the separate top-level summaries, not nested in app observations");
    }

    private static void noInventedBlockerFields(JsonNode facts) {
        require(facts.findValues("blockedCheckSummary").isEmpty() && facts.findValues("blockedCheckCount").isEmpty(),
            "The mapping must not invent specific blocker summaries or counts: " + facts);
    }

    private static void caseReferences(JsonNode facts) {
        var summary = facts.path("caseSummary");
        expect(summary, "reference", "SUP-PROBE");
        expect(summary, "status", "OPEN");
        expect(summary, "granted", true);
        var refs = summary.path("resources");
        require(refs.size() == 3, "Selected case references lost: " + summary);
        for (int i = 0; i < 3; i++) {
            expect(refs.path(i), "type", List.of("DEPLOYMENT", "ENGAGE_APP", "CONNECTION").get(i));
            expect(refs.path(i), "id", List.of("dep-probe-first", "awi-probe", "conn-probe").get(i));
        }
    }

    private static void syncStatus(JsonNode facts) {
        var sync = facts.path("syncObservations").path(0);
        expect(sync, "id", "conn-probe");
        expect(sync, "connectionStatus", "ACTIVE");
        expect(sync, "syncState", "IN_SYNC");
        expect(sync, "lastRunStatus", "COMPLETED");
        expect(sync, "lastSuccessfulSyncAt", AT);
        expect(sync, "liveProviderProbed", false);
    }

    private static void legacyOmission(JsonNode facts) {
        expect(facts.path("record"), "availability", "CURRENT");
        expect(facts.path("record"), "observedAt", AT);
        for (String field : List.of("resources", "releases", "documentIndexing", "registeredSourceCount",
            "documentIndexSummary", "releaseObservations", "deploymentObservations"))
            require(facts.findValues(field).isEmpty(), "Legacy unexpectedly retained " + field + ": " + facts);
    }

    private static void wrongRootOmission(JsonNode facts) {
        expect(facts, "action", DEPLOYMENT);
        expect(facts, "success", true);
        for (String field : List.of("availability", "observedAt", "record", "resources", "documentIndexSummary",
            "registeredSourceCount", "releaseObservations", "deploymentObservations"))
            require(facts.findValues(field).isEmpty(), "Wrong-root negative control unexpectedly retained " + field);
    }

    private static void taint(ObjectNode object) { object.put("secret", SECRET).put("rawError", RAW_ERROR); }

    private static void removeCanaries(JsonNode node) {
        if (node instanceof ObjectNode object) object.remove(List.of("secret", "rawError"));
        for (JsonNode child : node) removeCanaries(child);
    }

    private void check(String name, Checked test) {
        var check = new LinkedHashMap<String, Object>();
        check.put("name", name);
        try {
            test.run();
            check.put("status", "PASS");
        } catch (Exception | AssertionError failure) {
            check.put("status", "FAIL");
            check.put("detail", failure.toString());
        }
        checks.add(check);
    }

    private static void expect(JsonNode node, String field, Object value) {
        require(node.has(field) && String.valueOf(value).equals(node.path(field).asText()),
            "Expected " + field + "=" + value + "; got " + node);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String, Object> publishedJar(Class<?> type, String artifact) throws Exception {
        Path path = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        require(path.getFileName().toString().equals(artifact + "-0.8.41.jar"), "Not the published 0.8.41 JAR: " + path);
        var properties = new Properties();
        try (var jar = new java.util.jar.JarFile(path.toFile())) {
            var entry = jar.getJarEntry("META-INF/maven/io.github.loom-ai-labs/" + artifact + "/pom.properties");
            require(entry != null, "Published Maven metadata missing in " + path);
            try (var input = jar.getInputStream(entry)) { properties.load(input); }
        }
        require("0.8.41".equals(properties.getProperty("version")), "Unexpected published version: " + properties);
        require("io.github.loom-ai-labs".equals(properties.getProperty("groupId")), "Unexpected Maven group");
        require(artifact.equals(properties.getProperty("artifactId")), "Unexpected Maven artifact");
        return fileIdentity(path);
    }

    private static Map<String, Object> fileIdentity(Path path) throws Exception {
        return Map.of("path", path.toAbsolutePath().toString(), "sha256",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
    }

    private record Observation(JsonNode facts, JsonNode evidence) {}
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
