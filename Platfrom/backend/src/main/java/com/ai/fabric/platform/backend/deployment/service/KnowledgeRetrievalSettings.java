package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Compiles bounded retrieval policy only; source and customer authority remain elsewhere. */
public final class KnowledgeRetrievalSettings {
    private KnowledgeRetrievalSettings() { }

    public static Map<String, String> runtimeEnvironment(JsonNode provider) {
        return runtimeEnvironment(provider, null);
    }

    public static Map<String, String> runtimeEnvironment(JsonNode provider, JsonNode prompts) {
        if (provider == null || provider.isNull()) return Map.of();
        JsonNode profile = provider.path("knowledgeRetrieval");
        if (profile.isMissingNode() || profile.isNull()) return Map.of();
        if (!profile.isObject()) throw new IllegalArgumentException("Search settings must be an object.");
        if (profile.has("enabled") && !profile.path("enabled").isBoolean()) {
            throw new IllegalArgumentException("Choose whether to use the explicit search profile.");
        }
        if (profile.has("enabled") && !profile.path("enabled").booleanValue()) return Map.of();
        String mode = profile.path("mode").asText("");
        if (!Set.of("DENSE", "HYBRID", "KEYWORD").contains(mode)) {
            throw new IllegalArgumentException("Choose Meaning, Words and meaning, or diagnostic keyword search.");
        }
        if (!"DENSE".equals(mode) && !"weaviate".equalsIgnoreCase(provider.path("vectorStrategy").asText())) {
            throw new IllegalArgumentException("Words search is not implemented for the selected service. Keep Meaning search.");
        }
        String failurePolicy = profile.path("failurePolicy").asText("FAIL");
        if (!Set.of("FAIL", "VISIBLE_DENSE_FALLBACK").contains(failurePolicy)) {
            throw new IllegalArgumentException("Choose a supported search failure policy.");
        }
        int vector = number(profile, "vectorCandidates", 20, 1, 100);
        int lexical = number(profile, "lexicalCandidates", 20, 1, 100);
        int total = number(profile, "maxCandidates", 40, 1, 200);
        int results = number(profile, "maxResults", profile.hasNonNull("maxResults") ? Math.min(6, total)
            : inherited(prompts, "ragMaxDocumentsUsedForContext", Math.min(6, total), 1, total), 1, total);
        int characters = number(profile, "maxContextCharacters", profile.hasNonNull("maxContextCharacters") ? 8000
            : inherited(prompts, "ragMaxContextChars", 8000, 256, 200000), 256, 200000);
        int constant = number(profile, "rankConstant", 60, 1, 1000);
        Map<String, String> environment = new LinkedHashMap<>();
        String prefix = "AI_INFRASTRUCTURE_RAG_RETRIEVALPROFILE_";
        environment.put(prefix + "MODE", mode);
        environment.put(prefix + "VECTORCANDIDATES", Integer.toString(vector));
        environment.put(prefix + "LEXICALCANDIDATES", Integer.toString(lexical));
        environment.put(prefix + "MAXCANDIDATES", Integer.toString(total));
        environment.put(prefix + "MAXRESULTS", Integer.toString(results));
        environment.put(prefix + "MAXCONTEXTCHARACTERS", Integer.toString(characters));
        environment.put(prefix + "RANKCONSTANT", Integer.toString(constant));
        environment.put(prefix + "FAILUREPOLICY", failurePolicy);
        JsonNode reranking = profile.path("reranking");
        if (!reranking.isMissingNode() && !reranking.isNull()) {
            if (!reranking.isObject() || !reranking.path("enabled").isBoolean()) {
                throw new IllegalArgumentException("Choose whether to prioritize results with a reranker.");
            }
            String rankingPrefix = "AI_INFRASTRUCTURE_RAG_RERANKING_";
            environment.put(rankingPrefix + "ENABLED", Boolean.toString(reranking.path("enabled").asBoolean()));
            if (reranking.path("enabled").asBoolean()) {
                if (!"cohere".equals(reranking.path("provider").asText())
                    || !Set.of("rerank-v5.0-fast", "rerank-v5.0-pro").contains(reranking.path("model").asText())) {
                    throw new IllegalArgumentException("Choose a supported reranking provider and model.");
                }
                if (!reranking.path("externalProcessingApproved").isBoolean()
                    || !reranking.path("externalProcessingApproved").booleanValue()) {
                    throw new IllegalArgumentException("Approve sending the selected question and authorized excerpts to Cohere before enabling reranking.");
                }
                environment.put(rankingPrefix + "PROVIDER", "cohere");
                environment.put(rankingPrefix + "MODEL", reranking.path("model").asText());
                String rankingFailure = reranking.path("failurePolicy").asText("FAIL");
                if (!Set.of("FAIL", "VISIBLE_PRE_RERANK_FALLBACK").contains(rankingFailure)) {
                    throw new IllegalArgumentException("Choose a supported reranking failure policy.");
                }
                environment.put(rankingPrefix + "FAILUREPOLICY", rankingFailure);
                environment.put(rankingPrefix + "MAXCANDIDATES", Integer.toString(number(reranking, "maxCandidates", 40, 1, 100)));
                environment.put(rankingPrefix + "MAXCHARACTERSPERCANDIDATE", Integer.toString(number(reranking, "maxCharactersPerCandidate", 4000, 1, 16000)));
                environment.put(rankingPrefix + "MAXTOTALCHARACTERS", Integer.toString(number(reranking, "maxTotalCharacters", 160000, 1, 200000)));
                environment.put(rankingPrefix + "MAXTOKENSPERDOCUMENT", Integer.toString(number(reranking, "maxTokensPerDocument", 1024, 1, 4096)));
                environment.put(rankingPrefix + "TIMEOUTMS", Integer.toString(number(reranking, "timeoutMs", 5000, 1, 30000)));
            }
        }
        return Map.copyOf(environment);
    }

    public static boolean rerankingEnabled(JsonNode provider) {
        if (provider == null) return false;
        JsonNode profile = provider.path("knowledgeRetrieval");
        return (!profile.has("enabled") || profile.path("enabled").asBoolean(false))
            && profile.path("reranking").path("enabled").asBoolean(false);
    }

    public static boolean profileEnabled(JsonNode provider) {
        if (provider == null || !provider.path("knowledgeRetrieval").isObject()) return false;
        JsonNode profile = provider.path("knowledgeRetrieval");
        return !profile.has("enabled") || profile.path("enabled").asBoolean(false);
    }

    /** An immutable serving artifact must implement the requested settings, not silently ignore them. */
    public static void requireRuntimeSupport(JsonNode provider, JsonNode manifest) {
        if (!profileEnabled(provider)) return;
        requireCapability(manifest, "shared-profiled-retrieval-v1");
        if (rerankingEnabled(provider)) {
            requireCapability(manifest, "learned-reranking-v1");
            requireCapability(manifest, "deployment-local-reranking-budget-v1");
        }
    }

    private static void requireCapability(JsonNode manifest, String capability) {
        if (manifest != null && manifest.path("capabilities").isArray()) {
            for (JsonNode value : manifest.path("capabilities")) if (capability.equals(value.asText())) return;
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
            "The selected runtime does not support these search settings (" + capability + "). Choose a compatible runtime update.");
    }

    private static int inherited(JsonNode prompts, String key, int fallback, int minimum, int maximum) {
        return prompts == null ? fallback : number(prompts, key, fallback, minimum, maximum);
    }

    private static int number(JsonNode profile, String key, int inherited, int minimum, int maximum) {
        JsonNode value = profile.path(key);
        if (value.isMissingNode() || value.isNull()) return inherited;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum) {
            throw new IllegalArgumentException(key + " must be a whole number from " + minimum + " to " + maximum + ".");
        }
        return value.intValue();
    }
}
