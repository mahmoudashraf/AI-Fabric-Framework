package com.ai.infrastructure.connector.rest.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public record TraceContextDto(
    String requestId,
    String conversationId,
    VerifiedAuthContextDto authContext,
    String userId,
    String sessionId,
    String shopDomain,
    Map<String, Object> actionConfig,
    Map<String, String> mcpSecretValues
) {

    public TraceContextDto(String requestId,
                           String conversationId,
                           VerifiedAuthContextDto authContext) {
        this(requestId, conversationId, authContext, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "TraceContextDto[requestId=" + requestId
            + ", conversationId=" + conversationId
            + ", authContext=" + authContext
            + ", userId=" + userId
            + ", sessionId=" + sessionId
            + ", shopDomain=" + shopDomain
            + ", actionConfigPresent=" + (actionConfig != null && !actionConfig.isEmpty())
            + ", mcpSecretValueRefs=" + (mcpSecretValues != null ? mcpSecretValues.keySet() : java.util.Set.of())
            + "]";
    }
}
