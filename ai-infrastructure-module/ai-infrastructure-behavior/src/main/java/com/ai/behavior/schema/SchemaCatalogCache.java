package com.ai.behavior.schema;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCatalogCache {

    private final BehaviorSchemaRegistry registry;
    private final BehaviorModuleProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile SchemaSnapshot snapshot;

    public SchemaSnapshot snapshot() {
        SchemaSnapshot current = snapshot;
        if (current == null || current.expiresAt().isBefore(Instant.now())) {
            synchronized (this) {
                current = snapshot;
                if (current == null || current.expiresAt().isBefore(Instant.now())) {
                    snapshot = buildSnapshot();
                    current = snapshot;
                }
            }
        }
        return current;
    }

    private SchemaSnapshot buildSnapshot() {
        objectMapper.findAndRegisterModules();
        List<BehaviorSignalDefinition> definitions = registry.getAll().stream()
            .sorted(Comparator.comparing(BehaviorSignalDefinition::getId, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
        String etag = computeEtag(definitions);
        Duration ttl = Duration.ofSeconds(Math.max(15, properties.getSchemas().getCacheTtlSeconds()));
        return new SchemaSnapshot(definitions, etag, Instant.now().plus(ttl), ttl);
    }

    private String computeEtag(List<BehaviorSignalDefinition> definitions) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(definitions);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            log.debug("Failed to compute schema catalog etag, falling back to hashCode", ex);
            return Integer.toHexString(definitions.hashCode());
        }
    }

    public record SchemaSnapshot(List<BehaviorSignalDefinition> definitions,
                                 String etag,
                                 Instant expiresAt,
                                 Duration ttl) {
        public long maxAgeSeconds() {
            return Math.max(1, ttl.toSeconds());
        }
    }
}
