package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseUserVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/notifications")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportNotificationController {
    private final SupportRequesterService requesters;
    private final SupportTicketClient client;
    private final CustomerSupabaseUserVerifier verifier;

    public SupportNotificationController(SupportRequesterService requesters, SupportTicketClient client,
                                         CustomerSupabaseUserVerifier verifier) {
        this.requesters = requesters; this.client = client; this.verifier = verifier;
    }

    public record Preference(boolean enabled, String returnUrl) { }

    @ModelAttribute void headers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
    }

    @GetMapping public JsonNode get() {
        return client.request(requesters.current(), "GET", "/notifications", null, null);
    }

    @PutMapping public JsonNode update(@RequestBody Preference input,
                                      @RequestHeader(value="Authorization", required=false) String authorization) {
        var owner = requesters.current();
        String email = null;
        if (input.enabled()) {
            var principal = PlatformSecurityContext.currentPrincipal();
            if (principal == null || !"SUPABASE".equals(principal.authenticationMode())
                || owner.subject() == null || authorization == null || !authorization.startsWith("Bearer "))
                throw SupportPolicy.bad("SUPPORT_EMAIL_VERIFIED_SIGN_IN_REQUIRED");
            // Only the provider response can attest the delivery address, never intake/model fields.
            email = verifier.requireVerifiedEmail(authorization.substring(7), owner.subject());
        }
        return client.notificationPreference(owner, input, email);
    }
}
