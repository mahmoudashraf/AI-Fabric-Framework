package com.ai.fabric.runtime.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class RuntimeResolvedIdentity {
    RuntimeAuthContext authContext;
    boolean compatibilityIdentity;

    public String ownerId() {
        return authContext != null ? authContext.ownerId() : null;
    }

    public String orchestrationUserId() {
        return authContext != null ? authContext.orchestrationUserId() : null;
    }

    public String orchestrationSessionId() {
        return authContext != null ? authContext.orchestrationSessionId() : null;
    }
}
