package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.service.CustomerOnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class CustomerOnboardingController {
    private final CustomerOnboardingService onboarding;
    private final CustomerSupabaseAuthProperties properties;
    private final CustomerOfferEntitlementService offers;
    private final com.ai.fabric.platform.backend.config.CustomerOnboardingProperties registration;

    public CustomerOnboardingController(CustomerOnboardingService onboarding, CustomerSupabaseAuthProperties properties,
        CustomerOfferEntitlementService offers, com.ai.fabric.platform.backend.config.CustomerOnboardingProperties registration) {
        this.onboarding = onboarding; this.properties = properties; this.offers = offers; this.registration = registration;
    }

    @GetMapping("/api/platform/auth/customer-onboarding")
    public CustomerOnboardingService.Summary status(HttpServletRequest request) {
        return onboarding.status(token(request));
    }

    @PostMapping("/api/platform/auth/customer-onboarding")
    public CustomerOnboardingService.Summary create(@RequestBody CreateWorkspace request, HttpServletRequest http) {
        requireConsoleOrigin(http);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace details are required.");
        try {
            return onboarding.create(token(http), request.workspaceName(), request.displayName());
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "An account or workspace was just created for this identity. Refresh to continue.");
        }
    }

    @PostMapping("/api/customer-self-service/enrollment")
    public void enroll(HttpServletRequest request) {
        requireConsoleOrigin(request);
        offers.enrollPublicTemplates(registration.templateOfferIds());
    }

    private void requireConsoleOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !properties.linkOrigins().contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This operation requires a trusted Console origin.");
        }
    }

    private String token(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in with your verified customer identity.");
        }
        return value.substring(7).trim();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("success", false,
            "message", error.getReason() == null ? "Workspace request rejected." : error.getReason()));
    }

    public record CreateWorkspace(String workspaceName, String displayName) { }
}
