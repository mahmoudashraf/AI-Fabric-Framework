package com.ai.fabric.runtime.authz;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import ai.fabric.access.policy.EntityAccessPolicy;
import ai.fabric.dto.AIAccessSubjectContext;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Simple runtime authz policy for platform-managed deployments that only need verified caller identity.
 *
 * <p>This policy stays fail-closed for malformed subjects. Anonymous browser sessions remain denied unless the
 * deployment explicitly opts in, in which case the complete verified public-runtime contract is required.
 * {@code REMOTE_HTTP} remains the default runtime authz mode.</p>
 */
public class AllowVerifiedEntityAccessPolicy implements EntityAccessPolicy {

    private static final String CHAT_QUERY_SCOPE = "chat:query";

    private final boolean allowAnonymousSessions;

    public AllowVerifiedEntityAccessPolicy() {
        this(false);
    }

    public AllowVerifiedEntityAccessPolicy(boolean allowAnonymousSessions) {
        this.allowAnonymousSessions = allowAnonymousSessions;
    }

    @Override
    public boolean canAccess(AIAccessSubjectContext authContext, Map<String, Object> entity) {
        if (authContext == null || entity == null) {
            return false;
        }
        if (!StringUtils.hasText(authContext.getSubjectId())) {
            return false;
        }
        if (!StringUtils.hasText(authContext.getAuthMode())) {
            return false;
        }
        String subjectType = authContext.getSubjectType();
        if (!StringUtils.hasText(subjectType)) {
            return false;
        }
        if (!RuntimeAuthSubjectType.ANONYMOUS_SESSION.name().equalsIgnoreCase(subjectType.trim())) {
            return true;
        }
        return allowAnonymousSessions && hasVerifiedAnonymousContract(authContext);
    }

    private boolean hasVerifiedAnonymousContract(AIAccessSubjectContext authContext) {
        if (!StringUtils.hasText(authContext.getSessionId())
            || !authContext.getSubjectId().equals(authContext.getSessionId())
            || !StringUtils.hasText(authContext.getDeploymentId())
            || !StringUtils.hasText(authContext.getTenantId())
            || authContext.getGrantedScopes() == null
            || !authContext.getGrantedScopes().contains(CHAT_QUERY_SCOPE)) {
            return false;
        }
        // Canonical context comes from the verified runtime principal, not request metadata or a JWT body.
        if ("TRUSTED_INTERACTIVE".equals(authContext.getAuthMode())) {
            return RuntimeAuthSubjectType.ANONYMOUS_SESSION.name().equals(authContext.getCallerType());
        }
        if (!RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name().equals(authContext.getAuthMode())
            || !RuntimeAuthCallerType.PUBLIC_BROWSER.name().equals(authContext.getCallerType())
            || !StringUtils.hasText(authContext.getIssuer())
            || authContext.getAudiences() == null
            || authContext.getAudiences().stream().noneMatch(StringUtils::hasText)) {
            return false;
        }
        try {
            return StringUtils.hasText(authContext.getExpiresAt())
                && Instant.parse(authContext.getExpiresAt()).isAfter(Instant.now());
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
