package com.ai.fabric.runtime.web;

import ai.fabric.intent.orchestration.OrchestrationResult;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

final class WorkspaceAuthenticationRequirementSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final String FRAMEWORK_SCHEMA = "ai-fabric-authentication-requirement-v1";
    static final String WORKSPACE_SCHEMA = "loomai-workspace-authentication-requirement-v1";
    static final String ERROR_CODE = "AUTHENTICATION_REQUIRED";
    static final String FRAMEWORK_REASON = "ACTION_REQUIRES_AUTHENTICATED_END_USER";
    static final String REQUIRED_SUBJECT_TYPE = "END_USER";
    static final String RETRY_POLICY = "EXPLICIT_NEW_REQUEST";

    private WorkspaceAuthenticationRequirementSupport() {
    }

    static Map<String, Object> resolve(OrchestrationResult result, RuntimeResolvedIdentity identity) {
        if (!isAnonymous(identity)
            || result == null
            || !ERROR_CODE.equals(result.getErrorCode())) {
            return Map.of();
        }

        Map<String, Object> frameworkRequirement = objectMap(
            result.getData() == null ? null : result.getData().get("authenticationRequirement")
        );
        if (!FRAMEWORK_SCHEMA.equals(text(frameworkRequirement.get("schemaVersion")))
            || !FRAMEWORK_REASON.equals(text(frameworkRequirement.get("reason")))
            || !REQUIRED_SUBJECT_TYPE.equals(text(frameworkRequirement.get("requiredSubjectType")))) {
            return Map.of();
        }

        String capability = firstText(
            frameworkRequirement.get("action"),
            result.getData() == null ? null : result.getData().get("action")
        );
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("schemaVersion", WORKSPACE_SCHEMA);
        requirement.put("reasonCode", ERROR_CODE);
        requirement.put("retryPolicy", RETRY_POLICY);
        if (StringUtils.hasText(capability)) {
            requirement.put("capability", capability);
        }
        return Map.copyOf(requirement);
    }

    private static boolean isAnonymous(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext context = identity == null ? null : identity.getAuthContext();
        return context != null && context.isAnonymousSession();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<?, ?> map;
        if (value instanceof Map<?, ?> existing) {
            map = existing;
        } else {
            try {
                map = OBJECT_MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() { });
            } catch (IllegalArgumentException ignored) {
                return Map.of();
            }
        }
        if (map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, entry) -> {
            if (key != null) {
                normalized.put(String.valueOf(key), entry);
            }
        });
        return normalized;
    }

    private static String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }
}
