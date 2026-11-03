package com.ai.fabric.platform.backend.support;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SupportRequesterKeysController {
    private final SupportRequesterAssertionService assertions;
    public SupportRequesterKeysController(SupportRequesterAssertionService assertions) { this.assertions = assertions; }

    @GetMapping("/api/public/support/requester-jwks")
    public ResponseEntity<Map<String, Object>> keys() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assertions.publicKeys());
    }
}
