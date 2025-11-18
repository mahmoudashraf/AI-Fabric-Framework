package com.ai.behavior.api;

import com.ai.behavior.schema.BehaviorSignalDefinition;
import com.ai.behavior.schema.SchemaCatalogCache;
import com.ai.behavior.schema.SchemaCatalogCache.SchemaSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-behavior/schemas")
@RequiredArgsConstructor
public class BehaviorSchemaController {

    private final SchemaCatalogCache schemaCatalogCache;

    @GetMapping
    public ResponseEntity<List<BehaviorSignalDefinition>> list(
        @RequestParam(name = "domain", required = false) String domain,
        @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {

        SchemaSnapshot snapshot = schemaCatalogCache.snapshot();
        CacheControl cacheControl = CacheControl.maxAge(snapshot.maxAgeSeconds(), TimeUnit.SECONDS).cachePublic();

        List<BehaviorSignalDefinition> definitions = snapshot.definitions().stream()
            .filter(def -> domain == null || (def.getDomain() != null && def.getDomain().equalsIgnoreCase(domain)))
            .collect(Collectors.toList());
        if (ifNoneMatch != null && ifNoneMatch.equals(snapshot.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(cacheControl)
                .eTag(snapshot.etag())
                .header("X-Schema-Cache-Ttl", String.valueOf(snapshot.maxAgeSeconds()))
                .build();
        }
        return ResponseEntity.ok()
            .cacheControl(cacheControl)
            .eTag(snapshot.etag())
            .header("X-Schema-Cache-Ttl", String.valueOf(snapshot.maxAgeSeconds()))
            .body(definitions);
    }
}
