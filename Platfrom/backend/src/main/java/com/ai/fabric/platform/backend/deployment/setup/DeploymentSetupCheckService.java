package com.ai.fabric.platform.backend.deployment.setup;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentConfigCompiler;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

import static com.ai.fabric.platform.backend.deployment.setup.SetupCheckContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Advisory control-plane checks, never action execution or release verification. No response bodies are retained. */
@Service
public class DeploymentSetupCheckService {
    private static final Duration FRESHNESS = Duration.ofMinutes(5);
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]{0,255})}");
    private final DeploymentRepository deployments;
    private final DeploymentDraftRepository drafts;
    private final DeploymentAccessService access;
    private final DeploymentConfigCompiler compiler;
    private final PlatformSecretService secrets;
    private final PlatformSecretRepository secretRows;
    private final DeploymentProviderSecretResolutionService providerSecrets;
    private final SetupProbeHttpClient http;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ai.fabric.platform.backend.mcp.runtime.McpConnectionReadiness mcpReadiness;
    private final Semaphore running = new Semaphore(2);
    private final Map<String, Map<String, Cached>> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, Cached>> entry) { return size() > 256; }
    });

    public DeploymentSetupCheckService(DeploymentRepository deployments, DeploymentDraftRepository drafts,
            DeploymentAccessService access, PlatformSecretService secrets, PlatformSecretRepository secretRows, DeploymentProviderSecretResolutionService providerSecrets,
            SetupProbeHttpClient http, ObjectMapper mapper, DeploymentConfigCompiler compiler) {
        this.deployments = deployments; this.drafts = drafts; this.access = access; this.secrets = secrets; this.secretRows = secretRows;
        this.providerSecrets = providerSecrets; this.http = http; this.mapper = mapper; this.compiler = compiler;
    }

    public Summary inspect(String deploymentId) { return evaluate(deploymentId, null); }
    public Summary run(String deploymentId, Mode mode) {
        if (!running.tryAcquire()) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Setup checks are busy. Try again shortly.");
        try { return evaluate(deploymentId, Objects.requireNonNull(mode)); }
        finally { running.release(); }
    }

    private Summary evaluate(String deploymentId, Mode mode) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        // Sending stored credentials to an upstream is an editor operation, not a viewer/page-load side effect.
        if (mode == null) access.requireDeploymentAccess(deployment); else access.requireDeploymentEditorAccess(deployment);
        var draft = drafts.findTopByDeploymentIdOrderByRevisionNumberDesc(deploymentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No saved draft."));
        List<Check> checks = catalogue(deploymentId, draft);
        Map<String, Cached> previous;
        synchronized (cache) { previous = new HashMap<>(cache.getOrDefault(deploymentId, Map.of())); }
        Map<String, Cached> next = new HashMap<>();
        List<ServiceResult> results = new ArrayList<>();
        Instant now = Instant.now();
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        for (Check check : checks) {
            String fingerprint = fingerprint(check);
            Cached old = previous.get(check.id);
            boolean fresh = old != null && old.fingerprint.equals(fingerprint) && old.result.checkedAt() != null
                    && old.result.checkedAt().plus(FRESHNESS).isAfter(now);
            boolean selected = mode == Mode.ALL || mode == Mode.CHANGED && !fresh
                    || mode == Mode.FAILED && old != null && "FAILED".equals(old.result.status());
            ServiceResult result;
            if (check.reason != null) {
                result = result(check, check.failure ? "FAILED" : "NOT_TESTED", check.reason,
                        check.failure ? mode != null ? Instant.now() : fresh ? old.result.checkedAt() : null : null, null);
                if (check.failure && mode != null) next.put(check.id, new Cached(fingerprint, result));
                else if (check.failure && fresh) next.put(check.id, old);
            } else if (selected && System.nanoTime() < deadline) {
                var outcome = http.probe(check.url, check.headers);
                result = result(check, outcome.status(), outcome.message(), Instant.now(), outcome.httpStatus());
                next.put(check.id, new Cached(fingerprint, result));
            } else if (fresh) {
                result = result(check, old.result.status(), old.result.message(), old.result.checkedAt(), old.result.httpStatus());
                next.put(check.id, new Cached(fingerprint, result));
            } else {
                result = result(check, "NOT_TESTED", selected ? "This run reached its time limit. Check setup again for remaining services."
                        : old == null ? "Not checked yet." : "Settings or credentials changed, or the last check expired. Check again.", null, null);
            }
            results.add(result);
        }
        if (mode != null) synchronized (cache) { cache.put(deploymentId, next); }
        if (mcpReadiness != null) results.addAll(mcpReadiness.inspect(deploymentId, json(draft.getActionsConfigJson())));
        return new Summary(deploymentId, draft.getId(), draft.getRevisionNumber(),
                results.stream().map(ServiceResult::checkedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null),
                List.copyOf(results), count(results, "VERIFIED"), count(results, "FAILED"), count(results, "NOT_TESTED"));
    }

    private List<Check> catalogue(String deploymentId, DeploymentDraftEntity draft) {
        List<Check> out = new ArrayList<>();
        JsonNode provider = json(draft.getProviderConfigJson());
        String fallback = ManagedDeploymentProfileCatalog.resolveLlmProvider(provider);
        addModel(out, deploymentId, "orchestration", fallback(ManagedDeploymentProfileCatalog.orchestrationLlmProvider(provider), fallback),
                fallback(ManagedDeploymentProfileCatalog.orchestrationBaseUrl(provider), ManagedDeploymentProfileCatalog.openAiBaseUrl(provider)),
                ManagedDeploymentProfileCatalog.orchestrationApiKeySecretRef(provider), ManagedDeploymentProfileCatalog.orchestrationManagedServiceRef(provider));
        addModel(out, deploymentId, "generation", fallback(ManagedDeploymentProfileCatalog.generationLlmProvider(provider), fallback),
                fallback(ManagedDeploymentProfileCatalog.generationBaseUrl(provider), ManagedDeploymentProfileCatalog.openAiBaseUrl(provider)),
                ManagedDeploymentProfileCatalog.generationApiKeySecretRef(provider), ManagedDeploymentProfileCatalog.generationManagedServiceRef(provider));
        addModel(out, deploymentId, "embeddings", ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(provider),
                fallback(ManagedDeploymentProfileCatalog.embeddingBaseUrl(provider), ManagedDeploymentProfileCatalog.openAiBaseUrl(provider)),
                ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(provider), ManagedDeploymentProfileCatalog.embeddingManagedServiceRef(provider));
        addVector(out, deploymentId, provider);
        JsonNode routing = compiler.resolveActionRouting(json(draft.getActionsConfigJson()), json(draft.getRoutingConfigJson()));
        JsonNode upstream = routing.path("connector").path("upstream");
        Map<String, String> actionLabels = new HashMap<>();
        for (JsonNode action : json(draft.getActionsConfigJson()).path("actions"))
            actionLabels.put(action.path("name").asText(), fallback(text(action, "displayName"), action.path("name").asText()));
        var actions = routing.path("actions").fields();
        while (actions.hasNext()) {
            var action = actions.next(); JsonNode route = action.getValue();
            String usage = actionLabels.getOrDefault(action.getKey(), action.getKey());
            if (route.has("source-projection") || route.has("sourceProjection")) continue;
            if (!text(route, "connection-profile-ref", "connectionProfileRef").isEmpty()) {
                String ref = text(route, "connection-profile-ref", "connectionProfileRef");
                JsonNode profile = routing.path("connection-profiles").path(ref).isObject()
                        ? routing.path("connection-profiles").path(ref) : routing.path("connectionProfiles").path(ref);
                if (!profile.isObject()) {
                    for (JsonNode dataset : json(draft.getMarketplaceDatasetConfigJson()).path("datasets")) {
                        JsonNode candidate = dataset.path("syncConnector").path("connectionProfile");
                        if (ref.equals(candidate.path("profileId").asText())) { profile = candidate; break; }
                    }
                }
                addProfile(out, deploymentId, profile, usage);
                continue;
            }
            Map<String, String> headers = headers(route.path("headers"));
            JsonNode auth = upstream.path("auth");
            String type = auth.path("type").asText("NONE");
            if ("API_KEY".equals(type)) headers.put(auth.path("header").asText("Authorization"), auth.path("value").asText());
            String base = text(route, "url").isEmpty() ? text(upstream, "base-url", "baseUrl") : origin(text(route, "url"));
            String path = text(route, "setup-check-path");
            if (path.isEmpty() && (text(route, "url").isEmpty() || origin(base).equals(origin(text(upstream, "base-url", "baseUrl")))))
                path = text(upstream, "setup-check-path");
            addHttp(out, deploymentId, "Action connection", base, path, headers, usage,
                    !Set.of("NONE", "API_KEY").contains(type) ? "This authentication method needs its normal runtime verification." : null);
        }
        for (JsonNode dataset : json(draft.getMarketplaceDatasetConfigJson()).path("datasets")) {
            JsonNode connector = dataset.path("syncConnector");
            String label = dataset.path("datasetId").asText("Knowledge source");
            if ("HTTP_JSON".equals(connector.path("connectorType").asText())) {
                addProfile(out, deploymentId, connector.path("connectionProfile"), label);
            } else {
                out.add(unavailable("source-" + label, "Knowledge source", label,
                        "Use the source's preview/indexing workflow to verify document access. This check does not index or read files."));
            }
        }
        LinkedHashMap<String, Check> grouped = new LinkedHashMap<>();
        for (Check check : out) {
            String key = fingerprint(check);
            Check existing = grouped.get(key);
            if (existing == null) grouped.put(key, check);
            else { var uses = new LinkedHashSet<>(existing.usedBy); uses.addAll(check.usedBy); existing.usedBy = List.copyOf(uses); }
        }
        if (grouped.size() > 40) throw new ResponseStatusException(BAD_REQUEST, "Setup checks support up to 40 distinct service connections.");
        List<Check> result = new ArrayList<>(grouped.values());
        for (Check check : result) check.id = digest(check.identity).substring(0, 24);
        return result;
    }

    private void addProfile(List<Check> out, String deploymentId, JsonNode profile, String usage) {
        JsonNode auth = profile.path("auth"); String strategy = auth.path("strategy").asText("NONE");
        if (profile.path("allowedHosts").isArray() && !profile.path("allowedHosts").isEmpty()) {
            try {
                String host = URI.create(resolve(deploymentId, text(profile, "baseUrl", "base-url"))).getHost();
                boolean allowed = false;
                for (JsonNode value : profile.path("allowedHosts"))
                    if (Objects.equals(host, resolve(deploymentId, value.asText()))) allowed = true;
                if (!allowed) throw new IllegalArgumentException();
            } catch (RuntimeException ex) {
                out.add(new Check("profile:" + usage, "Data/API connection", null, Map.of(), List.of(usage),
                        "The connection endpoint does not match its configured allowed hosts, or its scoped references are unavailable.", true));
                return;
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if ("API_KEY".equals(strategy)) headers.put(auth.path("apiKeyHeader").asText("Authorization"), auth.path("apiKeyValue").asText());
        addHttp(out, deploymentId, "Data/API connection", text(profile, "baseUrl", "base-url"),
                text(profile, "setupCheckPath", "setup-check-path"), headers, usage,
                !Set.of("API_KEY", "NONE").contains(strategy) ? "Token-exchange authentication must be checked through the runtime. No token request was executed." : null);
    }

    private void addHttp(List<Check> out, String deploymentId, String label, String base, String path,
                         Map<String, String> rawHeaders, String usage, String unsupported) {
        String identity = label + ":" + base + ":" + path + ":" + new TreeMap<>(rawHeaders);
        if (unsupported != null) { out.add(unavailable(identity, label, usage, unsupported)); return; }
        if (path.isEmpty()) { out.add(unavailable(identity, label, usage,
                "No safe GET check path is configured. Business actions were not executed.")); return; }
        // Only secret references are expanded; request parameters, user tokens and environment variables are never guessed.
        try {
            Map<String, String> resolved = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (var entry : rawHeaders.entrySet()) {
                String value = resolve(deploymentId, entry.getValue());
                if (value.isBlank()) throw new IllegalArgumentException();
                resolved.put(entry.getKey(), value);
            }
            String resolvedBase = resolve(deploymentId, base);
            if (!path.startsWith("/") || path.startsWith("//") || path.contains("?") || path.contains("#") || path.contains("{")
                    || path.contains("..") || !path.matches("/[A-Za-z0-9/_~.%-]*")) throw new IllegalArgumentException();
            String url = resolvedBase.replaceAll("/+$", "") + path;
            URI baseUri = URI.create(resolvedBase); URI target = URI.create(url);
            if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null || !Objects.equals(baseUri.getHost(), target.getHost()))
                throw new IllegalArgumentException();
            out.add(new Check(identity, label, url, resolved, List.of(usage), null, false));
        } catch (RuntimeException ex) {
            out.add(new Check(identity, label, null, Map.of(), List.of(usage),
                    "The endpoint or its credential reference is missing, unavailable for this Brain, or cannot be safely tested from Console.", true));
        }
    }

    private void addModel(List<Check> out, String id, String usage, String provider, String base, String ref, String managedRef) {
        if (!"openai".equalsIgnoreCase(provider) || managedRef != null && !managedRef.isBlank()) {
            out.add(unavailable("model-" + usage + ":" + provider + ":" + base, "AI provider: " + provider, usage,
                    "No safe authenticated metadata check is available for this provider. Verify inference through Try your AI; no paid query was sent."));
            return;
        }
        String root = base == null || base.isBlank() ? "https://api.openai.com/v1" : base.replaceAll("/+$", "");
        for (String suffix : List.of("/chat/completions", "/embeddings", "/responses"))
            if (root.endsWith(suffix)) { root = root.substring(0, root.length() - suffix.length()); break; }
        if (!root.endsWith("/v1")) root += "/v1";
        provider(out, id, "OpenAI", root,
                "/models", "OPENAI_API_KEY", ref, "Authorization", "Bearer ", usage);
    }

    private void addVector(List<Check> out, String id, JsonNode config) {
        String strategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(config);
        switch (strategy) {
            case "pinecone" -> provider(out, id, "Pinecone vector search", fallback(ManagedDeploymentProfileCatalog.pineconeApiHost(config), "https://api.pinecone.io"), "/indexes", "PINECONE_API_KEY",
                    ManagedDeploymentProfileCatalog.pineconeRuntimeApiKeySecretName(config), "Api-Key", "", "Vector retrieval");
            case "qdrant" -> provider(out, id, "Qdrant vector search", qdrantUrl(config),
                    "/collections", "QDRANT_API_KEY", ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(config), "api-key", "", "Vector retrieval");
            case "weaviate" -> provider(out, id, "Weaviate vector search", ManagedDeploymentProfileCatalog.weaviateScheme(config) + "://"
                            + ManagedDeploymentProfileCatalog.weaviateHost(config) + ":" + ManagedDeploymentProfileCatalog.weaviatePort(config),
                    "/v1/schema", "WEAVIATE_API_KEY", null, "Authorization", "Bearer ", "Vector retrieval");
            default -> out.add(unavailable("vector:" + strategy, "Vector search", "Vector retrieval",
                    "This vector service is verified by its existing binding/apply workflow, not a public Console HTTP probe."));
        }
    }

    private void provider(List<Check> out, String id, String label, String base, String path, String purpose,
                          String ref, String header, String prefix, String usage) {
        try {
            var key = providerSecrets.resolve(id, purpose, ref == null || ref.isBlank() ? null : ref);
            if (key.value() == null || key.value().isBlank()) throw new IllegalArgumentException();
            String host = URI.create(base).getHost();
            boolean official = "OPENAI_API_KEY".equals(purpose) && "api.openai.com".equals(host)
                    || "PINECONE_API_KEY".equals(purpose) && "api.pinecone.io".equals(host);
            // Shared Platform credentials may only go to their fixed official vendor, never a caller-selected host.
            if (!official) requireOwnReference(id, key.summary().secretName());
            String url = base.replaceAll("/+$", "") + path;
            out.add(new Check(label + ":" + base + ":" + purpose + ":" + Objects.toString(ref, ""), label, url, Map.of(header, prefix + key.value()), List.of(usage), null, false));
        } catch (RuntimeException ex) {
            out.add(new Check(label + ":" + base + ":" + purpose + ":" + Objects.toString(ref, ""), label, null, Map.of(), List.of(usage),
                    "The configured provider credential is missing or unavailable for this Brain. Check Keys & secrets or the target binding.", true));
        }
    }

    private String resolve(String id, String value) {
        var matcher = REFERENCE.matcher(value); StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            requireOwnReference(id, matcher.group(1));
            String key = secrets.resolveSecretForDeployment(matcher.group(1), id, null);
            if (key == null || key.isBlank()) throw new IllegalArgumentException();
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(key));
        }
        matcher.appendTail(out);
        if (out.indexOf("${") >= 0 || out.indexOf("{{") >= 0) throw new IllegalArgumentException();
        return out.toString();
    }
    private void requireOwnReference(String deploymentId, String reference) {
        var row = reference == null ? null : secretRows.findById(reference).orElse(null);
        if (row == null || !deploymentId.equals(row.getDeploymentId()) || row.getTargetProfileId() != null)
            throw new IllegalArgumentException("Credential is not owned by this Brain's saved connection.");
    }
    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String qdrantUrl(JsonNode config) {
        String host = ManagedDeploymentProfileCatalog.qdrantHost(config);
        return host == null ? "" : host.contains("://") ? host : "http://" + host + ":" + ManagedDeploymentProfileCatalog.qdrantPort(config);
    }
    private static String origin(String value) {
        try { URI uri = URI.create(value); return uri.getScheme() + "://" + uri.getRawAuthority(); }
        catch (RuntimeException ex) { return ""; }
    }
    private JsonNode json(String value) {
        try { return value == null ? mapper.createObjectNode() : mapper.readTree(value); }
        catch (Exception ex) { throw new ResponseStatusException(BAD_REQUEST, "Saved configuration cannot be read. Repair the draft before checking setup."); }
    }
    private static String text(JsonNode value, String... keys) {
        for (String key : keys) if (value.path(key).isTextual() && !value.path(key).asText().isBlank()) return value.path(key).asText().trim();
        return "";
    }
    private static Map<String, String> headers(JsonNode value) {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        value.fields().forEachRemaining(entry -> { if (entry.getValue().isTextual()) out.put(entry.getKey(), entry.getValue().asText()); });
        return out;
    }
    private static Check unavailable(String identity, String label, String usage, String reason) {
        return new Check(identity, label, null, Map.of(), List.of(usage), reason, false);
    }
    private static ServiceResult result(Check check, String status, String message, Instant time, Integer code) {
        return new ServiceResult(check.id, check.label, status, message, time, check.usedBy, code);
    }
    private static long count(List<ServiceResult> results, String status) { return results.stream().filter(r -> status.equals(r.status())).count(); }
    private String fingerprint(Check check) {
        try { return digest(mapper.writeValueAsString(List.of(check.identity, Objects.toString(check.url, ""), new TreeMap<>(check.headers), Objects.toString(check.reason, "")))); }
        catch (Exception ex) { throw new IllegalStateException("Cannot identify setup check."); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Cannot identify setup check."); }
    }
    private record Cached(String fingerprint, ServiceResult result) { }
    private static final class Check {
        String id; final String identity, label, url, reason; final Map<String, String> headers; List<String> usedBy; final boolean failure;
        Check(String identity, String label, String url, Map<String, String> headers, List<String> usedBy, String reason, boolean failure) {
            this.identity = identity; this.label = label; this.url = url; this.headers = headers; this.usedBy = usedBy; this.reason = reason; this.failure = failure;
        }
    }
}
