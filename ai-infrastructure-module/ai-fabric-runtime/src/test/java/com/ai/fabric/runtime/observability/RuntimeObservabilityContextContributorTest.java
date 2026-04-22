package com.ai.fabric.runtime.observability;

import com.ai.fabric.observability.logging.LoomaiMdcKeys;
import com.ai.fabric.observability.logging.LoomaiObservabilityContextContributor;
import com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeObservabilityContextContributorTest {

    @Test
    void extractsDeploymentAndTenantFromVerifiedRuntimeContext() {
        RuntimeAuthProperties properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
        RuntimeRequestAuthResolver resolver = new RuntimeRequestAuthResolver(properties);
        LoomaiObservabilityContextContributor contributor =
            new RuntimeObservabilityConfiguration().runtimeAuthContextContributor(resolver);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders(
            request,
            properties,
            RuntimeAuthContext.builder()
                .subjectId("customer-123")
                .subjectType(RuntimeAuthSubjectType.END_USER)
                .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
                .deploymentId("dep-123")
                .customerId("cust-9")
                .tenantId("tenant-7")
                .sessionId("session-123")
                .issuer("platform-poc:SESSION")
                .audiences(List.of("dep-123"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build()
        );

        Map<String, String> context = contributor.contribute(request);

        assertThat(context)
            .containsEntry(LoomaiMdcKeys.DEPLOYMENT_ID, "dep-123")
            .containsEntry(LoomaiMdcKeys.CUSTOMER_ID, "cust-9")
            .containsEntry(LoomaiMdcKeys.TENANT_ID, "tenant-7")
            .containsEntry(LoomaiMdcKeys.ACTOR_ID, "customer-123");
    }
}
