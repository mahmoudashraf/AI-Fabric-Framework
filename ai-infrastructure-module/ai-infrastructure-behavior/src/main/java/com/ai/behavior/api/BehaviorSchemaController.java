package com.ai.behavior.api;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-behavior/schemas")
@RequiredArgsConstructor
public class BehaviorSchemaController {

    private final BehaviorSchemaRegistry schemaRegistry;
    private final BehaviorModuleProperties properties;

    @GetMapping
    public ResponseEntity<List<BehaviorSignalDefinition>> list(
        @RequestParam(name = "domain", required = false) String domain,
        WebRequest webRequest) {

        List<BehaviorSignalDefinition> definitions = schemaRegistry.getAll().stream()
            .filter(def -> domain == null || (def.getDomain() != null && def.getDomain().equalsIgnoreCase(domain)))
            .collect(Collectors.toList());

        String etag = buildEtag(domain, definitions);
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(cacheControl())
                .eTag(etag)
                .build();
        }

        return ResponseEntity.ok()
            .cacheControl(cacheControl())
            .eTag(etag)
            .body(definitions);
    }

    private CacheControl cacheControl() {
        Duration ttl = properties.getSchemas().getCacheTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(5);
        }
        return CacheControl.maxAge(ttl).cachePublic();
    }

    private String buildEtag(String domain, List<BehaviorSignalDefinition> definitions) {
        String payload = (domain == null ? "*" : domain.toLowerCase()) + ":" +
            schemaRegistry.getLastLoadedAt().toEpochMilli() + ":" + definitions.size();
        return "\"" + hexSha256(payload.getBytes(StandardCharsets.UTF_8)) + "\"";
    }

    private String hexSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 message digest unavailable", ex);
        }
    }
}
