package com.loomai.operations.telemetry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.operations.config.OperationsProperties;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(TelemetryProperties.class)
public class TelemetryRegistry {
    public record RegisteredSource(
        @NotNull @Pattern(regexp = TelemetryContracts.ID) String serverId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[^\\p{Cntrl}]+") String displayName,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,31}") String environment,
        @NotNull String ingestKey
    ) {
        @Override public String toString() { return "RegisteredSource[credentials redacted]"; }
    }
    public record Credential(boolean reader, String serverId) {}
    private record SourceKey(RegisteredSource source, byte[] hash) {}

    private final boolean enabled;
    private final byte[] readHash;
    private final List<SourceKey> keys;

    public TelemetryRegistry(TelemetryProperties properties, OperationsProperties operations,
                             ObjectMapper mapper, Validator validator) {
        enabled = properties.enabled();
        if (!enabled) {
            readHash = sha256("");
            keys = List.of();
            return;
        }
        requireKey(properties.readKey());
        readHash = sha256(properties.readKey());
        List<RegisteredSource> sources;
        try {
            if (properties.sourcesJson() == null || properties.sourcesJson().length() > 32768) {
                throw new IllegalArgumentException();
            }
            sources = mapper.readValue(properties.sourcesJson(), new TypeReference<List<RegisteredSource>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("TELEMETRY_SOURCES_JSON must be a bounded, strict JSON source array.");
        }
        if (sources == null || sources.isEmpty() || sources.size() > 32) {
            throw new IllegalStateException("Telemetry requires between 1 and 32 registered sources.");
        }
        var ids = new HashSet<String>();
        var credentials = new HashSet<String>();
        credentials.add(operations.apiKey());
        if (!credentials.add(properties.readKey())) throw keyConflict();
        var sourceKeys = new ArrayList<SourceKey>();
        for (RegisteredSource source : sources) {
            if (source == null || !validator.validate(source).isEmpty() || !ids.add(source.serverId())) {
                throw new IllegalStateException("Telemetry source fields must be valid and server IDs unique.");
            }
            requireKey(source.ingestKey());
            if (!credentials.add(source.ingestKey())) throw keyConflict();
            sourceKeys.add(new SourceKey(source, sha256(source.ingestKey())));
        }
        keys = List.copyOf(sourceKeys);
    }

    public boolean enabled() { return enabled; }
    public List<RegisteredSource> sources() { return keys.stream().map(SourceKey::source).toList(); }

    public RegisteredSource source(String id) {
        return sources().stream().filter(source -> source.serverId().equals(id)).findFirst()
            .orElseThrow(() -> new TelemetryException(404, "SERVER_NOT_FOUND", "Server is not registered."));
    }

    public Credential authenticate(String token) {
        if (!enabled) return null;
        byte[] supplied = sha256(token);
        boolean reader = MessageDigest.isEqual(readHash, supplied);
        String serverId = null;
        for (SourceKey key : keys) {
            if (MessageDigest.isEqual(key.hash(), supplied)) serverId = key.source().serverId();
        }
        return reader ? new Credential(true, null) : serverId == null ? null : new Credential(false, serverId);
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.length() < 32 || key.length() > 512
            || key.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalStateException("Telemetry keys must be 32-512 printable non-space ASCII characters.");
        }
    }

    private static IllegalStateException keyConflict() {
        return new IllegalStateException("Telemetry read, per-host ingest and Operations keys must all be distinct.");
    }
}
