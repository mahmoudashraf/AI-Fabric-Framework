package com.ai.fabric.platform.backend.secret.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class DeploymentSecretPurposeCatalog {

    private static final Map<String, SecretPurposeDefinition> DEFINITIONS = createDefinitions();

    public Set<String> supportedOverridePurposes() {
        return DEFINITIONS.keySet();
    }

    public boolean isSupportedOverridePurpose(String secretPurpose) {
        return DEFINITIONS.containsKey(normalize(secretPurpose));
    }

    public SecretPurposeDefinition requireDefinition(String secretPurpose) {
        SecretPurposeDefinition definition = definition(secretPurpose).orElse(null);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported deployment-scoped provider secret purpose: " + secretPurpose);
        }
        return definition;
    }

    public Optional<SecretPurposeDefinition> definition(String secretPurpose) {
        return Optional.ofNullable(DEFINITIONS.get(normalize(secretPurpose)));
    }

    public String inferPurposeForSecretName(String secretName) {
        String normalized = normalize(secretName);
        if (DEFINITIONS.containsKey(normalized)) {
            return normalized;
        }
        if (normalized.startsWith("MANAGED_PINECONE_API_KEY_DEP_")) {
            return "PINECONE_API_KEY";
        }
        if (normalized.startsWith("MANAGED_QDRANT_DB_API_KEY_DEP_")) {
            return "QDRANT_API_KEY";
        }
        if (normalized.startsWith("MANAGED_MILVUS_USERNAME_DEP_")
            || normalized.startsWith("MANAGED_MILVUS_PASSWORD_DEP_")) {
            return "MILVUS_RUNTIME_CREDENTIALS";
        }
        if (normalized.startsWith("MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_")) {
            return "VECTORIZATION_RUNNER_REGISTRATION_TOKEN";
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static Map<String, SecretPurposeDefinition> createDefinitions() {
        Map<String, SecretPurposeDefinition> definitions = new LinkedHashMap<>();
        definitions.put("OPENAI_API_KEY", new SecretPurposeDefinition("OPENAI_API_KEY", "OpenAI API key", false, "OPENAI_API_KEY", null));
        definitions.put("AZURE_OPENAI_API_KEY", new SecretPurposeDefinition("AZURE_OPENAI_API_KEY", "Azure OpenAI API key", false, "AZURE_OPENAI_API_KEY", null));
        definitions.put("ANTHROPIC_API_KEY", new SecretPurposeDefinition("ANTHROPIC_API_KEY", "Anthropic API key", false, "ANTHROPIC_API_KEY", null));
        definitions.put("COHERE_API_KEY", new SecretPurposeDefinition("COHERE_API_KEY", "Cohere API key", false, "COHERE_API_KEY", null));
        definitions.put("GEMINI_API_KEY", new SecretPurposeDefinition("GEMINI_API_KEY", "Gemini API key", false, "GEMINI_API_KEY", null));
        definitions.put("PINECONE_API_KEY", new SecretPurposeDefinition("PINECONE_API_KEY", "Pinecone API key", false, "PINECONE_API_KEY", null));
        definitions.put("WEAVIATE_API_KEY", new SecretPurposeDefinition("WEAVIATE_API_KEY", "Weaviate API key", false, "WEAVIATE_API_KEY", null));
        definitions.put("QDRANT_API_KEY", new SecretPurposeDefinition("QDRANT_API_KEY", "Qdrant API key", false, "QDRANT_API_KEY", null));
        definitions.put("MILVUS_RUNTIME_CREDENTIALS", new SecretPurposeDefinition("MILVUS_RUNTIME_CREDENTIALS", "Milvus runtime credentials", true, "MILVUS_USERNAME", "MILVUS_PASSWORD"));
        return Map.copyOf(definitions);
    }

    public record SecretPurposeDefinition(
        String purpose,
        String displayName,
        boolean paired,
        String primaryGlobalSecretName,
        String secondaryGlobalSecretName
    ) {
    }
}
