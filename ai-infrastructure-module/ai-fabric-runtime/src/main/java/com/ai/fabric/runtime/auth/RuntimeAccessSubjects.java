package com.ai.fabric.runtime.auth;

import ai.fabric.dto.AIAccessSubjectContext;
import java.util.List;

/** Carries an already verified host identity into framework APIs; this does not authenticate or grant scopes. */
public final class RuntimeAccessSubjects {
    private RuntimeAccessSubjects() { }

    public static AIAccessSubjectContext from(RuntimeResolvedIdentity identity) {
        if (identity == null || identity.getAuthContext() == null) throw new IllegalArgumentException("Verified identity is required.");
        var context = identity.getAuthContext();
        return AIAccessSubjectContext.builder().subjectId(context.getSubjectId()).sessionId(context.getSessionId())
            .subjectType(context.getSubjectType() == null ? null : context.getSubjectType().name())
            .authMode(context.getAuthMode() == null ? null : context.getAuthMode().name())
            .callerType(context.getCallerType() == null ? null : context.getCallerType().name())
            .deploymentId(context.getDeploymentId()).customerId(context.getCustomerId()).tenantId(context.getTenantId())
            .issuer(context.getIssuer()).expiresAt(context.getExpiresAt() == null ? null : context.getExpiresAt().toString())
            .audiences(context.getAudiences() == null ? List.of() : List.copyOf(context.getAudiences()))
            .grantedScopes(context.getGrantedScopes() == null ? List.of() : List.copyOf(context.getGrantedScopes())).build();
    }
}
