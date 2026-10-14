package com.ai.fabric.runtime.web;

import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceAuthenticationRequirementSupportTest {

    @Test
    void translatesExactFrameworkRequirementForAnonymousCaller() {
        Map<String, Object> result = WorkspaceAuthenticationRequirementSupport.resolve(
            authenticationRequired("request_test_drive"),
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
        );

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
            "schemaVersion", "loomai-workspace-authentication-requirement-v1",
            "reasonCode", "AUTHENTICATION_REQUIRED",
            "retryPolicy", "EXPLICIT_NEW_REQUEST",
            "capability", "request_test_drive"
        ));
    }

    @Test
    void translatesFrameworkRecordWithoutRuntimeDependingOnItsConcreteType() {
        OrchestrationResult frameworkResult = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_DENIED)
            .success(false)
            .errorCode("AUTHENTICATION_REQUIRED")
            .data(Map.of(
                "action", "request_test_drive",
                "authenticationRequirement", new FrameworkRequirement(
                    "ai-fabric-authentication-requirement-v1",
                    "ACTION_REQUIRES_AUTHENTICATED_END_USER",
                    "END_USER",
                    "request_test_drive"
                )
            ))
            .build();

        assertThat(WorkspaceAuthenticationRequirementSupport.resolve(
            frameworkResult,
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
        )).containsEntry("capability", "request_test_drive");
    }

    @Test
    void doesNotChallengeAuthenticatedCaller() {
        assertThat(WorkspaceAuthenticationRequirementSupport.resolve(
            authenticationRequired("request_test_drive"),
            identity(RuntimeAuthSubjectType.END_USER)
        )).isEmpty();
    }

    @Test
    void doesNotPromoteGenericActionDenial() {
        OrchestrationResult denied = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_DENIED)
            .success(false)
            .errorCode("ACTION_NOT_ALLOWED")
            .data(Map.of("action", "request_test_drive"))
            .build();

        assertThat(WorkspaceAuthenticationRequirementSupport.resolve(
            denied,
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
        )).isEmpty();
    }

    @Test
    void failsClosedForMissingOrUnknownFrameworkContract() {
        OrchestrationResult missing = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_DENIED)
            .success(false)
            .errorCode("AUTHENTICATION_REQUIRED")
            .data(Map.of("action", "request_test_drive"))
            .build();
        OrchestrationResult unknown = OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_DENIED)
            .success(false)
            .errorCode("AUTHENTICATION_REQUIRED")
            .data(Map.of("authenticationRequirement", Map.of(
                "schemaVersion", "unknown-v1",
                "reason", "ACTION_REQUIRES_AUTHENTICATED_END_USER",
                "requiredSubjectType", "END_USER"
            )))
            .build();

        assertThat(WorkspaceAuthenticationRequirementSupport.resolve(
            missing,
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
        )).isEmpty();
        assertThat(WorkspaceAuthenticationRequirementSupport.resolve(
            unknown,
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION)
        )).isEmpty();
    }

    private OrchestrationResult authenticationRequired(String action) {
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_DENIED)
            .success(false)
            .errorCode("AUTHENTICATION_REQUIRED")
            .data(Map.of(
                "action", action,
                "authenticationRequirement", Map.of(
                    "schemaVersion", "ai-fabric-authentication-requirement-v1",
                    "reason", "ACTION_REQUIRES_AUTHENTICATED_END_USER",
                    "requiredSubjectType", "END_USER",
                    "action", action
                )
            ))
            .build();
    }

    private RuntimeResolvedIdentity identity(RuntimeAuthSubjectType type) {
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectType(type)
                .subjectId(type == RuntimeAuthSubjectType.ANONYMOUS_SESSION ? "anon-1" : "user-1")
                .build())
            .build();
    }

    private record FrameworkRequirement(
        String schemaVersion,
        String reason,
        String requiredSubjectType,
        String action
    ) { }
}
