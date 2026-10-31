package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerAccountRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import java.util.Map;

@RestController
public class CustomerAccountRecoveryController {
    private final CustomerSupabaseAuthProperties properties;
    private final CustomerAccountRecoveryService recovery;

    public CustomerAccountRecoveryController(CustomerSupabaseAuthProperties properties, CustomerAccountRecoveryService recovery) {
        this.properties = properties; this.recovery = recovery;
    }

    @PostMapping("/api/platform/auth/customer-recovery")
    public CustomerAccountRecoveryService.RecoverySession recover(@RequestBody RecoveryRequest request,
        HttpServletRequest servletRequest, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String origin = servletRequest.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank() || "null".equals(origin) || !properties.linkOrigins().contains(origin)) {
            throw new ResponseStatusException(FORBIDDEN, "Account recovery requires an explicitly trusted Console origin.");
        }
        return recovery.recover(request.tokenHash(), request.email());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableRequest() {
        // Parser diagnostics can contain the token itself; do not log or return them.
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(Map.of("errorCode", "BAD_REQUEST", "message", "A recovery token and the existing Console email are required."));
    }

    public record RecoveryRequest(String tokenHash, String email) {
        @Override public String toString() { return "RecoveryRequest[REDACTED]"; }
    }
}
