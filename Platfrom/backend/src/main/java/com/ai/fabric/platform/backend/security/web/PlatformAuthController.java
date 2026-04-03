package com.ai.fabric.platform.backend.security.web;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.model.PlatformLoginRequest;
import com.ai.fabric.platform.backend.security.model.PlatformAuthSessionSummary;
import com.ai.fabric.platform.backend.security.service.PlatformAuthenticatedSession;
import com.ai.fabric.platform.backend.security.service.PlatformIdentityService;
import com.ai.fabric.platform.backend.security.service.PlatformLoginRateLimiter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthProperties properties;
    private final PlatformIdentityService platformIdentityService;
    private final PlatformLoginRateLimiter platformLoginRateLimiter;

    public PlatformAuthController(PlatformAuthProperties properties,
                                  PlatformIdentityService platformIdentityService,
                                  PlatformLoginRateLimiter platformLoginRateLimiter) {
        this.properties = properties;
        this.platformIdentityService = platformIdentityService;
        this.platformLoginRateLimiter = platformLoginRateLimiter;
    }

    @GetMapping("/session")
    public PlatformAuthSessionSummary session() {
        if (!properties.enabled()) {
            return new PlatformAuthSessionSummary(
                false,
                properties.headerName(),
                false,
                null,
                null,
                null,
                null,
                properties.sessionEnabled(),
                properties.apiKeyEnabled(),
                true,
                true,
                true
            );
        }

        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        boolean authenticated = principal != null;
        boolean canManageUsers = principal != null && principal.role() == PlatformRole.PLATFORM_ADMIN;
        boolean canManageSecrets = principal != null && principal.role() == PlatformRole.PLATFORM_ADMIN;
        boolean canOperateDeployments = principal != null;
        return new PlatformAuthSessionSummary(
            properties.enabled(),
            properties.headerName(),
            authenticated,
            authenticated ? principal.actorId() : null,
            authenticated ? principal.displayName() : null,
            authenticated ? principal.role().name() : null,
            authenticated ? principal.authenticationMode() : null,
            properties.sessionEnabled(),
            properties.apiKeyEnabled(),
            canManageUsers,
            canManageSecrets,
            canOperateDeployments
        );
    }

    @PostMapping("/login")
    public PlatformAuthSessionSummary login(@RequestBody PlatformLoginRequest request,
                                            HttpServletRequest httpServletRequest,
                                            HttpServletResponse response) {
        if (!properties.sessionEnabled()) {
            throw new ResponseStatusException(BAD_REQUEST, "Session-based login is disabled.");
        }
        platformLoginRateLimiter.checkAllowed(request.email(), clientAddress(httpServletRequest));
        PlatformAuthenticatedSession session;
        try {
            session = platformIdentityService.login(request.email(), request.password());
        } catch (RuntimeException ex) {
            platformLoginRateLimiter.recordFailure(request.email(), clientAddress(httpServletRequest));
            throw ex;
        }
        platformLoginRateLimiter.recordSuccess(request.email(), clientAddress(httpServletRequest));
        writeSessionCookie(response, session.rawToken(), session.expiresAt().toEpochMilli() - System.currentTimeMillis());
        PlatformPrincipal principal = session.principal();
        return new PlatformAuthSessionSummary(
            properties.enabled(),
            properties.headerName(),
            true,
            principal.actorId(),
            principal.displayName(),
            principal.role().name(),
            principal.authenticationMode(),
            properties.sessionEnabled(),
            properties.apiKeyEnabled(),
            principal.role() == PlatformRole.PLATFORM_ADMIN,
            principal.role() == PlatformRole.PLATFORM_ADMIN,
            true
        );
    }

    @PostMapping("/logout")
    public PlatformAuthSessionSummary logout(HttpServletRequest request,
                                             HttpServletResponse response) {
        platformIdentityService.logout(readCookie(request, properties.sessionCookieName()));
        clearSessionCookie(response);
        return unauthenticatedSummary();
    }

    private PlatformAuthSessionSummary unauthenticatedSummary() {
        return new PlatformAuthSessionSummary(
            properties.enabled(),
            properties.headerName(),
            false,
            null,
            null,
            null,
            null,
            properties.sessionEnabled(),
            properties.apiKeyEnabled(),
            false,
            false,
            false
        );
    }

    private void writeSessionCookie(HttpServletResponse response, String token, long ttlMillis) {
        ResponseCookie cookie = ResponseCookie.from(properties.sessionCookieName(), token)
            .httpOnly(true)
            .secure(properties.sessionCookieSecure())
            .path("/")
            .sameSite(cookieSameSite())
            .maxAge(Duration.ofMillis(Math.max(ttlMillis, 0)))
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(properties.sessionCookieName(), "")
            .httpOnly(true)
            .secure(properties.sessionCookieSecure())
            .path("/")
            .sameSite(cookieSameSite())
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String cookieSameSite() {
        String configured = properties.sessionCookieSameSite();
        return (configured == null || configured.isBlank()) ? "Strict" : configured.trim();
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> cookieName.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return (commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor).trim();
        }
        return request.getRemoteAddr();
    }
}
