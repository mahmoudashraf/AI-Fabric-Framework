package com.ai.behavior.api;

import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-behavior/schemas")
@RequiredArgsConstructor
public class BehaviorSchemaController {

    private final BehaviorSchemaRegistry schemaRegistry;

    @GetMapping
    public ResponseEntity<List<BehaviorSignalDefinition>> list(
        @RequestParam(name = "domain", required = false) String domain) {

        List<BehaviorSignalDefinition> definitions = schemaRegistry.getAll().stream()
            .filter(def -> domain == null || (def.getDomain() != null && def.getDomain().equalsIgnoreCase(domain)))
            .collect(Collectors.toList());
        return ResponseEntity.ok(definitions);
    }
}
