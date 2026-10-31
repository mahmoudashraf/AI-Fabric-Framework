package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/platform/auth")
public class CustomerSupabaseAuthController {
    private final CustomerSupabaseAuthProperties properties;
    private final CustomerSupabaseIdentityService identities;
    public CustomerSupabaseAuthController(CustomerSupabaseAuthProperties properties, CustomerSupabaseIdentityService identities) {
        this.properties = properties; this.identities = identities;
    }

    @GetMapping("/customer-config")
    public PublicConfig config() {
        return new PublicConfig(properties.enabled(), properties.enabled() ? properties.projectUrl() : null,
            properties.enabled() ? properties.publicKey() : null, properties.enabled() ? properties.deskClients() : List.of(),
            properties.enabled() ? properties.deskSignInUrl() : null);
    }

    @PostMapping("/customer-link")
    public void link(@RequestBody LinkRequest request, HttpServletRequest servletRequest) {
        String origin = servletRequest.getHeader(HttpHeaders.ORIGIN);
        // Cookie-authenticated linking trusts Console origins, not sibling apps or the global CORS policy.
        if (origin == null || origin.isBlank() || "null".equals(origin) || !properties.linkOrigins().contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account linking requires an explicitly trusted Console origin.");
        }
        identities.linkCurrentCustomer(request.accessToken());
    }

    @DeleteMapping("/customer-links/{userId}")
    public void revoke(@PathVariable String userId) { identities.revoke(userId); }

    public record LinkRequest(String accessToken) { @Override public String toString() { return "LinkRequest[REDACTED]"; } }
    public record PublicConfig(boolean enabled, String projectUrl, String publicKey,
        List<CustomerSupabaseAuthProperties.DeskClient> deskClients, String deskSignInUrl) { }
}
