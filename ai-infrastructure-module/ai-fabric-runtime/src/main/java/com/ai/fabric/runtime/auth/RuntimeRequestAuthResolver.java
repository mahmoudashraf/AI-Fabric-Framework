package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public class RuntimeRequestAuthResolver {

    public static final String WARNING_LEGACY_REQUEST_IDENTITY = "LEGACY_REQUEST_IDENTITY_COMPATIBILITY";
    public static final String WARNING_REQUEST_USER_ID_CONFLICT = "REQUEST_USER_ID_CONFLICT";
    public static final String WARNING_REQUEST_OWNER_ID_CONFLICT = "REQUEST_OWNER_ID_CONFLICT";
    public static final String WARNING_REQUEST_SESSION_ID_CONFLICT = "REQUEST_SESSION_ID_CONFLICT";
    public static final String WARNING_REQUEST_IDENTITY_ALIAS_PRESENT = "REQUEST_IDENTITY_ALIAS_PRESENT";

    private final RuntimeAuthProperties properties;
    private final RuntimePublicTokenService runtimePublicTokenService;

    public RuntimeRequestAuthResolver(RuntimeAuthProperties properties) {
        this(properties, null);
    }

    public RuntimeRequestAuthResolver(RuntimeAuthProperties properties,
                                      RuntimePublicTokenService runtimePublicTokenService) {
        this.properties = properties != null ? properties : new RuntimeAuthProperties();
        this.runtimePublicTokenService = runtimePublicTokenService;
    }

    public RuntimeResolvedIdentity resolveForChat(HttpServletRequest request,
                                                  String requestUserId,
                                                  String requestSessionId) {
        RuntimeResolvedIdentity verified = resolveVerifiedContext(request, requestUserId, requestSessionId, null);
        if (verified != null) {
            return verified;
        }
        return resolveLegacyChatIdentity(requestUserId, requestSessionId);
    }

    public RuntimeResolvedIdentity resolveVerifiedForChat(HttpServletRequest request,
                                                          String requestUserId,
                                                          String requestSessionId) {
        RuntimeResolvedIdentity verified = resolveVerifiedContext(request, requestUserId, requestSessionId, null);
        if (verified != null) {
            return verified;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified runtime auth context is required.");
    }

    public RuntimeResolvedIdentity resolveForConversation(HttpServletRequest request,
                                                          String requestUserId,
                                                          String requestOwnerId) {
        RuntimeResolvedIdentity verified = resolveVerifiedContext(request, requestUserId, null, requestOwnerId);
        if (verified != null) {
            return verified;
        }
        return resolveLegacyConversationIdentity(requestUserId, requestOwnerId);
    }

    public RuntimeResolvedIdentity resolveVerifiedForConversation(HttpServletRequest request,
                                                                  String requestUserId,
                                                                  String requestOwnerId) {
        RuntimeResolvedIdentity verified = resolveVerifiedContext(request, requestUserId, null, requestOwnerId);
        if (verified != null) {
            return verified;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified runtime auth context is required.");
    }

    public void requireTrustedBackendIngress(HttpServletRequest request, String surface) {
        if (properties.getIngress().getMode() != RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED) {
            return;
        }
        requireTrustedBackendAuthentication(request);
        if (StringUtils.hasText(surface)) {
            log.debug("Runtime trusted-backend ingress authorized for surface={}", surface.trim());
        }
    }

    public void requireScope(RuntimeResolvedIdentity identity, String requiredScope, String surface) {
        if (!StringUtils.hasText(requiredScope) || identity == null || identity.getAuthContext() == null) {
            return;
        }
        RuntimeAuthMode authMode = identity.getAuthContext().getAuthMode();
        if (authMode != RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS
            && authMode != RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED) {
            return;
        }
        List<String> grantedScopes = identity.getAuthContext().getGrantedScopes() != null
            ? identity.getAuthContext().getGrantedScopes()
            : List.of();
        boolean allowed = grantedScopes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .anyMatch(requiredScope::equals);
        if (!allowed) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Runtime auth context is missing required scope " + requiredScope
                    + (StringUtils.hasText(surface) ? " for " + surface.trim() : "") + "."
            );
        }
    }

    private RuntimeResolvedIdentity resolveVerifiedContext(HttpServletRequest request,
                                                           String requestUserId,
                                                           String requestSessionId,
                                                           String requestOwnerId) {
        RuntimeResolvedIdentity publicBearerIdentity = resolvePublicBearerToken(request, requestUserId, requestSessionId, requestOwnerId);
        if (publicBearerIdentity != null) {
            return publicBearerIdentity;
        }

        RuntimeAuthProperties.Headers headers = properties.getIngress().getHeaders();
        String subjectId = trimHeader(request, headers.getSubjectId());
        String subjectTypeText = trimHeader(request, headers.getSubjectType());
        String authModeText = trimHeader(request, headers.getAuthMode());
        String callerTypeText = trimHeader(request, headers.getCallerType());
        boolean anyContextHeaderPresent = StringUtils.hasText(subjectId)
            || StringUtils.hasText(subjectTypeText)
            || StringUtils.hasText(authModeText)
            || StringUtils.hasText(callerTypeText);

        if (!anyContextHeaderPresent) {
            return null;
        }

        requireTrustedBackendAuthentication(request);

        if (!StringUtils.hasText(subjectId) || !StringUtils.hasText(subjectTypeText) || !StringUtils.hasText(authModeText)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incomplete runtime auth context headers.");
        }

        RuntimeAuthSubjectType subjectType = parseEnum(subjectTypeText, RuntimeAuthSubjectType.class, "subjectType");
        RuntimeAuthMode authMode = parseEnum(authModeText, RuntimeAuthMode.class, "authMode");
        RuntimeAuthCallerType callerType = StringUtils.hasText(callerTypeText)
            ? parseEnum(callerTypeText, RuntimeAuthCallerType.class, "callerType")
            : defaultCallerType(authMode);
        String sessionId = trimHeader(request, headers.getSessionId());
        if (subjectType == RuntimeAuthSubjectType.ANONYMOUS_SESSION && !StringUtils.hasText(sessionId)) {
            sessionId = subjectId.trim();
        }

        List<String> warnings = new ArrayList<>();
        handleVerifiedContextRequestIdentityPresence(requestUserId, requestOwnerId, requestSessionId, warnings);
        handleRequestIdentityConflict("userId", requestUserId, subjectId, warnings);
        handleRequestIdentityConflict("ownerId", requestOwnerId, subjectId, warnings);
        if (StringUtils.hasText(requestSessionId) && StringUtils.hasText(sessionId) && !requestSessionId.trim().equals(sessionId)) {
            if (properties.getIngress().isRejectConflictingRequestIdentity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request sessionId conflicts with verified runtime auth context.");
            }
            log.warn("Runtime request sessionId differs from verified auth context sessionId. requestSessionId={}, authSessionId={}",
                requestSessionId.trim(), sessionId);
            addWarning(warnings, WARNING_REQUEST_SESSION_ID_CONFLICT);
        }

        Instant expiresAt = parseInstantHeader(request, headers.getExpiresAt());
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Runtime auth context is expired.");
        }
        String issuer = trimHeader(request, headers.getIssuer());
        List<String> audiences = parseCsvValues(trimHeader(request, headers.getAudiences()));
        validateConfiguredVerifiedIssuer(issuer);
        validateConfiguredVerifiedAudiences(audiences);

        RuntimeAuthContext authContext = RuntimeAuthContext.builder()
            .subjectId(subjectId.trim())
            .subjectType(subjectType)
            .authMode(authMode)
            .callerType(callerType)
            .deploymentId(trimHeader(request, headers.getDeploymentId()))
            .customerId(trimHeader(request, headers.getCustomerId()))
            .tenantId(trimHeader(request, headers.getTenantId()))
            .sessionId(sessionId)
            .issuer(issuer)
            .audiences(audiences)
            .expiresAt(expiresAt)
            .grantedScopes(parseScopes(trimHeader(request, headers.getScopes())))
            .build();

        return RuntimeResolvedIdentity.builder()
            .authContext(authContext)
            .compatibilityIdentity(false)
            .warnings(List.copyOf(warnings))
            .build();
    }

    private RuntimeResolvedIdentity resolvePublicBearerToken(HttpServletRequest request,
                                                             String requestUserId,
                                                             String requestSessionId,
                                                             String requestOwnerId) {
        if (runtimePublicTokenService == null || request == null || !runtimePublicTokenService.isConfigured()) {
            return null;
        }
        String headerName = properties.getPublicTokens().getAuthorizationHeader();
        String authorizationHeader = trimHeader(request, headerName);
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        RuntimeAuthContext authContext = runtimePublicTokenService.validateBearerToken(authorizationHeader);
        List<String> warnings = new ArrayList<>();
        handleVerifiedContextRequestIdentityPresence(requestUserId, requestOwnerId, requestSessionId, warnings);
        handleRequestIdentityConflict("userId", requestUserId, authContext.getSubjectId(), warnings);
        handleRequestIdentityConflict("ownerId", requestOwnerId, authContext.getSubjectId(), warnings);
        if (StringUtils.hasText(requestSessionId)
            && StringUtils.hasText(authContext.getSessionId())
            && !requestSessionId.trim().equals(authContext.getSessionId())) {
            if (properties.getIngress().isRejectConflictingRequestIdentity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request sessionId conflicts with verified runtime auth context.");
            }
            log.warn("Runtime request sessionId differs from verified public token sessionId. requestSessionId={}, authSessionId={}",
                requestSessionId.trim(), authContext.getSessionId());
            addWarning(warnings, WARNING_REQUEST_SESSION_ID_CONFLICT);
        }
        return RuntimeResolvedIdentity.builder()
            .authContext(authContext)
            .compatibilityIdentity(false)
            .warnings(List.copyOf(warnings))
            .build();
    }

    private void handleVerifiedContextRequestIdentityPresence(String requestUserId,
                                                              String requestOwnerId,
                                                              String requestSessionId,
                                                              List<String> warnings) {
        List<String> presentFields = new ArrayList<>(3);
        if (StringUtils.hasText(requestUserId)) {
            presentFields.add("userId");
        }
        if (StringUtils.hasText(requestOwnerId)) {
            presentFields.add("ownerId");
        }
        if (StringUtils.hasText(requestSessionId)) {
            presentFields.add("sessionId");
        }
        if (presentFields.isEmpty()) {
            return;
        }
        if (properties.getIngress().isRejectRequestIdentityWhenVerifiedContextPresent()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Legacy request identity fields are not allowed when verified runtime auth context is present: "
                    + String.join(", ", presentFields) + ". Use verified runtime auth context instead."
            );
        }
        if (properties.getIngress().isLogLegacyRequestIdentity()) {
            log.warn("Runtime request included legacy identity aliases alongside verified auth context. fields={}", presentFields);
        }
        addWarning(warnings, WARNING_REQUEST_IDENTITY_ALIAS_PRESENT);
    }

    private RuntimeResolvedIdentity resolveLegacyChatIdentity(String requestUserId, String requestSessionId) {
        requireLegacyCompatibility();

        String userId = trimToNull(requestUserId);
        String sessionId = trimToNull(requestSessionId);
        RuntimeAuthContext authContext;
        if (StringUtils.hasText(userId)) {
            authContext = RuntimeAuthContext.builder()
                .subjectId(userId)
                .subjectType(RuntimeAuthSubjectType.END_USER)
                .authMode(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY)
                .callerType(RuntimeAuthCallerType.LEGACY_REQUEST)
                .sessionId(StringUtils.hasText(sessionId) ? sessionId : "legacy-session-" + UUID.randomUUID())
                .issuer("legacy-request")
                .build();
        } else {
            String anonymousSessionId = StringUtils.hasText(sessionId) ? sessionId : "anon-" + UUID.randomUUID();
            authContext = RuntimeAuthContext.builder()
                .subjectId(anonymousSessionId)
                .subjectType(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
                .authMode(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY)
                .callerType(RuntimeAuthCallerType.LEGACY_REQUEST)
                .sessionId(anonymousSessionId)
                .issuer("legacy-request")
                .build();
        }
        logLegacyCompatibilityUse("chat");
        return RuntimeResolvedIdentity.builder()
            .authContext(authContext)
            .compatibilityIdentity(true)
            .warnings(List.of(WARNING_LEGACY_REQUEST_IDENTITY))
            .build();
    }

    private RuntimeResolvedIdentity resolveLegacyConversationIdentity(String requestUserId, String requestOwnerId) {
        requireLegacyCompatibility();

        String ownerId = trimToNull(requestUserId);
        if (!StringUtils.hasText(ownerId)) {
            ownerId = trimToNull(requestOwnerId);
        }
        if (!StringUtils.hasText(ownerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation owner identity is required.");
        }
        logLegacyCompatibilityUse("conversation");
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectId(ownerId)
                .subjectType(RuntimeAuthSubjectType.END_USER)
                .authMode(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY)
                .callerType(RuntimeAuthCallerType.LEGACY_REQUEST)
                .issuer("legacy-request")
                .build())
            .compatibilityIdentity(true)
            .warnings(List.of(WARNING_LEGACY_REQUEST_IDENTITY))
            .build();
    }

    private void requireLegacyCompatibility() {
        if (properties.getIngress().getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            || !properties.getIngress().isLegacyRequestIdentityEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified runtime auth context is required.");
        }
    }

    private void requireTrustedBackendAuthentication(HttpServletRequest request) {
        RuntimeAuthProperties.TrustedBackend trustedBackend = properties.getIngress().getTrustedBackend();
        String expectedValue = trimToNull(trustedBackend.getApiKeyValue());
        if (!StringUtils.hasText(expectedValue)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Trusted backend authentication is not configured for runtime auth context headers."
            );
        }
        String provided = trimHeader(request, trustedBackend.getApiKeyHeader());
        if (!StringUtils.hasText(provided) || !constantTimeEquals(expectedValue, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Trusted backend authentication failed.");
        }
    }

    private RuntimeAuthCallerType defaultCallerType(RuntimeAuthMode authMode) {
        return switch (authMode) {
            case PLATFORM_PROXY_SESSION -> RuntimeAuthCallerType.PLATFORM_PROXY;
            case PRIVATE_RUNTIME_BACKEND_MEDIATED -> RuntimeAuthCallerType.TRUSTED_BACKEND;
            case PUBLIC_RUNTIME_ANONYMOUS, PUBLIC_RUNTIME_AUTHENTICATED -> RuntimeAuthCallerType.PUBLIC_BROWSER;
            case LEGACY_REQUEST_IDENTITY -> RuntimeAuthCallerType.LEGACY_REQUEST;
        };
    }

    private Instant parseInstantHeader(HttpServletRequest request, String headerName) {
        String raw = trimHeader(request, headerName);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid runtime auth expiry header.");
        }
    }

    private List<String> parseScopes(String rawScopes) {
        return parseCsvValues(rawScopes);
    }

    private List<String> parseCsvValues(String rawValues) {
        if (!StringUtils.hasText(rawValues)) {
            return List.of();
        }
        return Arrays.stream(rawValues.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private void validateConfiguredVerifiedIssuer(String issuer) {
        List<String> acceptedIssuers = parseConfiguredValues(properties.getIngress().getAcceptedIssuers());
        if (acceptedIssuers.isEmpty()) {
            return;
        }
        String normalizedIssuer = trimToNull(issuer);
        if (!StringUtils.hasText(normalizedIssuer) || !acceptedIssuers.contains(normalizedIssuer)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Runtime verified auth issuer is not allowed.");
        }
    }

    private void validateConfiguredVerifiedAudiences(List<String> audiences) {
        List<String> acceptedAudiences = parseConfiguredValues(properties.getIngress().getAcceptedAudiences());
        if (acceptedAudiences.isEmpty()) {
            return;
        }
        List<String> normalizedAudiences = parseConfiguredValues(audiences);
        if (normalizedAudiences.isEmpty() || normalizedAudiences.stream().noneMatch(acceptedAudiences::contains)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Runtime verified auth audience is not allowed.");
        }
    }

    private List<String> parseConfiguredValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(this::trimToNull)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private void logLegacyCompatibilityUse(String surface) {
        if (properties.getIngress().isLogLegacyRequestIdentity()) {
            log.warn("Runtime {} request is using legacy request identity compatibility. "
                + "Configure verified auth context headers and trusted backend auth for production use.", surface);
        }
    }

    private void handleRequestIdentityConflict(String fieldName,
                                               String requestValue,
                                               String resolvedSubjectId,
                                               List<String> warnings) {
        if (!StringUtils.hasText(requestValue) || !StringUtils.hasText(resolvedSubjectId)) {
            return;
        }
        String trimmedRequest = requestValue.trim();
        if (!trimmedRequest.equals(resolvedSubjectId)) {
            if (properties.getIngress().isRejectConflictingRequestIdentity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request " + fieldName + " conflicts with verified runtime auth context.");
            }
            log.warn("Runtime request {} differs from verified auth context subject. request{}={}, authSubject={}",
                fieldName, Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1), trimmedRequest, resolvedSubjectId);
            switch (fieldName) {
                case "userId" -> addWarning(warnings, WARNING_REQUEST_USER_ID_CONFLICT);
                case "ownerId" -> addWarning(warnings, WARNING_REQUEST_OWNER_ID_CONFLICT);
                default -> { }
            }
        }
    }

    private void addWarning(List<String> warnings, String warningCode) {
        if (warnings == null || !StringUtils.hasText(warningCode) || warnings.contains(warningCode)) {
            return;
        }
        warnings.add(warningCode);
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, String fieldName) {
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid runtime auth " + fieldName + " value.");
        }
    }

    private String trimHeader(HttpServletRequest request, String headerName) {
        if (request == null || !StringUtils.hasText(headerName)) {
            return null;
        }
        return trimToNull(request.getHeader(headerName.trim()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
