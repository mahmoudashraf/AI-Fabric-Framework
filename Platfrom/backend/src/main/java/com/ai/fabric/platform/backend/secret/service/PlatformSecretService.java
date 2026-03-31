package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformSecretService {

    public static final String MANAGED_SECRET_PREFIX = "MANAGED_";
    private static final Map<String, SecretDefinition> SUPPORTED_SECRETS = createSupportedSecrets();

    private final PlatformSecretRepository platformSecretRepository;
    private final PlatformAuditService platformAuditService;
    private final Environment environment;

    public PlatformSecretService(PlatformSecretRepository platformSecretRepository,
                                 PlatformAuditService platformAuditService,
                                 Environment environment) {
        this.platformSecretRepository = platformSecretRepository;
        this.platformAuditService = platformAuditService;
        this.environment = environment;
    }

    public List<PlatformSecretSummary> listSecrets() {
        Map<String, PlatformSecretEntity> stored = new LinkedHashMap<>();
        platformSecretRepository.findAll().forEach(secret -> stored.put(secret.getName(), secret));
        return SUPPORTED_SECRETS.entrySet().stream()
            .map(entry -> toSummary(entry.getKey(), entry.getValue(), stored.get(entry.getKey())))
            .toList();
    }

    public List<String> requiredSecretNames() {
        return SUPPORTED_SECRETS.entrySet().stream()
            .filter(entry -> entry.getValue().required())
            .map(Map.Entry::getKey)
            .toList();
    }

    public boolean isSecretPresent(String name) {
        return resolveSecret(name) != null;
    }

    public String resolveSecret(String name) {
        SecretDefinition definition = SUPPORTED_SECRETS.get(name);
        if (definition == null) {
            if (isManagedSecretName(name)) {
                PlatformSecretEntity stored = platformSecretRepository.findById(name).orElse(null);
                if (stored != null && stored.getSecretValue() != null && !stored.getSecretValue().isBlank()) {
                    return stored.getSecretValue();
                }
            }
            return null;
        }
        PlatformSecretEntity stored = platformSecretRepository.findById(name).orElse(null);
        if (stored != null && stored.getSecretValue() != null && !stored.getSecretValue().isBlank()) {
            return stored.getSecretValue();
        }
        String envValue = environment.getProperty(name);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        if (definition.required()) {
            return null;
        }
        return null;
    }

    public boolean isManagedSecretName(String name) {
        return name != null
            && name.startsWith(MANAGED_SECRET_PREFIX)
            && name.matches("^[A-Z0-9_]+$");
    }

    @Transactional
    public PlatformSecretSummary updateSecret(String name, String value) {
        SecretDefinition definition = requireSupportedSecret(name);
        if (value == null || value.isBlank()) {
            return clearSecret(name);
        }

        PlatformSecretEntity entity = platformSecretRepository.findById(name).orElseGet(PlatformSecretEntity::new);
        entity.setName(name);
        entity.setSecretValue(value.trim());
        entity.setUpdatedAt(Instant.now());
        platformSecretRepository.save(entity);
        platformAuditService.record(
            "SECRET_UPDATED",
            "PLATFORM_SECRET",
            name,
            Map.of("source", "DATABASE", "required", definition.required())
        );
        return toSummary(name, definition, entity);
    }

    @Transactional
    public void upsertManagedSecret(String name, String value, Map<String, ?> auditDetails) {
        if (!isManagedSecretName(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported managed platform secret name: " + name);
        }
        if (value == null || value.isBlank()) {
            clearManagedSecret(name, auditDetails);
            return;
        }

        PlatformSecretEntity entity = platformSecretRepository.findById(name).orElseGet(PlatformSecretEntity::new);
        entity.setName(name);
        entity.setSecretValue(value.trim());
        entity.setUpdatedAt(Instant.now());
        platformSecretRepository.save(entity);
        platformAuditService.record(
            "MANAGED_SECRET_UPDATED",
            "PLATFORM_SECRET",
            name,
            auditDetails == null ? Map.of() : Map.copyOf(auditDetails)
        );
    }

    @Transactional
    public void clearManagedSecret(String name, Map<String, ?> auditDetails) {
        if (!isManagedSecretName(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported managed platform secret name: " + name);
        }
        platformSecretRepository.deleteById(name);
        platformAuditService.record(
            "MANAGED_SECRET_CLEARED",
            "PLATFORM_SECRET",
            name,
            auditDetails == null ? Map.of() : Map.copyOf(auditDetails)
        );
    }

    @Transactional
    public PlatformSecretSummary clearSecret(String name) {
        SecretDefinition definition = requireSupportedSecret(name);
        platformSecretRepository.deleteById(name);
        platformAuditService.record(
            "SECRET_CLEARED",
            "PLATFORM_SECRET",
            name,
            Map.of("required", definition.required())
        );
        return toSummary(name, definition, null);
    }

    private PlatformSecretSummary toSummary(String name,
                                            SecretDefinition definition,
                                            PlatformSecretEntity stored) {
        boolean databasePresent = stored != null
            && stored.getSecretValue() != null
            && !stored.getSecretValue().isBlank();
        boolean envPresent = false;
        if (!databasePresent) {
            String envValue = environment.getProperty(name);
            envPresent = envValue != null && !envValue.isBlank();
        }

        String source = databasePresent ? "DATABASE" : envPresent ? "ENV" : "MISSING";
        boolean present = databasePresent || envPresent;
        String updatedAt = stored == null || stored.getUpdatedAt() == null
            ? null
            : stored.getUpdatedAt().toString();

        return new PlatformSecretSummary(
            name,
            definition.displayName(),
            definition.description(),
            definition.required(),
            present,
            source,
            updatedAt
        );
    }

    private SecretDefinition requireSupportedSecret(String name) {
        SecretDefinition definition = SUPPORTED_SECRETS.get(name);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported platform secret: " + name);
        }
        return definition;
    }

    private static Map<String, SecretDefinition> createSupportedSecrets() {
        Map<String, SecretDefinition> secrets = new LinkedHashMap<>();
        secrets.put(
            "OPENAI_API_KEY",
            new SecretDefinition(
                "OpenAI API Key",
                "Provider credential used by runtime deployments that target OpenAI.",
                true
            )
        );
        secrets.put(
            "ANTHROPIC_API_KEY",
            new SecretDefinition(
                "Anthropic API Key",
                "Provider credential used by runtime deployments that target Anthropic.",
                false
            )
        );
        secrets.put(
            "AZURE_OPENAI_API_KEY",
            new SecretDefinition(
                "Azure OpenAI API Key",
                "Provider credential used by runtime deployments that target Azure OpenAI.",
                false
            )
        );
        secrets.put(
            "COHERE_API_KEY",
            new SecretDefinition(
                "Cohere API Key",
                "Provider credential used by runtime deployments that target Cohere.",
                false
            )
        );
        secrets.put(
            "GEMINI_API_KEY",
            new SecretDefinition(
                "Gemini API Key",
                "Provider credential used by runtime deployments that target Google Gemini.",
                false
            )
        );
        secrets.put(
            "CONNECTOR_API_KEY",
            new SecretDefinition(
                "Connector API Key",
                "Inbound API key used to protect deployed REST connector instances.",
                true
            )
        );
        secrets.put(
            "ACTIONS_CONNECTOR_API_KEY",
            new SecretDefinition(
                "Runtime To Connector API Key",
                "Shared key used by deployed runtimes when they call the REST connector.",
                true
            )
        );
        secrets.put(
            "APP_ADMIN_API_KEY",
            new SecretDefinition(
                "Admin API Key",
                "Optional key used to protect runtime and REST connector /api/admin/* endpoints in platform-managed deployments.",
                false
            )
        );
        secrets.put(
            "QDRANT_API_KEY",
            new SecretDefinition(
                "Qdrant API Key",
                "Optional credential used when platform-managed deployments target a protected Qdrant cluster.",
                false
            )
        );
        secrets.put(
            "QDRANT_CLOUD_MANAGEMENT_API_KEY",
            new SecretDefinition(
                "Qdrant Cloud Management API Key",
                "Management credential used by the platform when it provisions Qdrant Cloud clusters and deployment-scoped database API keys.",
                false
            )
        );
        secrets.put(
            "PINECONE_API_KEY",
            new SecretDefinition(
                "Pinecone API Key",
                "Provider credential used when platform-managed deployments target Pinecone.",
                false
            )
        );
        secrets.put(
            "WEAVIATE_API_KEY",
            new SecretDefinition(
                "Weaviate API Key",
                "Optional credential used when platform-managed deployments target a protected Weaviate cluster.",
                false
            )
        );
        secrets.put(
            "MILVUS_USERNAME",
            new SecretDefinition(
                "Milvus Username",
                "Optional username used when platform-managed deployments authenticate to Milvus.",
                false
            )
        );
        secrets.put(
            "MILVUS_PASSWORD",
            new SecretDefinition(
                "Milvus Password",
                "Optional password used when platform-managed deployments authenticate to Milvus.",
                false
            )
        );
        secrets.put(
            "PLATFORM_ARTIFACT_SIGNING_KEY",
            new SecretDefinition(
                "Platform Artifact Signing Key",
                "Signing key used to generate deployment artifact URLs for runtime and connector config delivery.",
                true
            )
        );
        return Map.copyOf(secrets);
    }

    private record SecretDefinition(
        String displayName,
        String description,
        boolean required
    ) {
    }
}
