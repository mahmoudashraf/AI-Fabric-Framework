package com.ai.fabric.platform.backend.deployment.operations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "platform.auth.enabled=true", "platform.auth.api-key-enabled=false", "platform.auth.session-enabled=true",
    "platform.auth.bootstrap-admin-enabled=true", "platform.auth.bootstrap-admin-email=operations-test@example.invalid",
    "platform.auth.bootstrap-admin-password=TestOperations123!", "platform.auth.session-cookie-secure=false",
    "platform.deployment-operations.mode=ENFORCE",
    "platform.deployment-operations.service-url=http://127.0.0.1:1",
    "platform.deployment-operations.allow-insecure-http=true",
    "platform.deployment-operations.api-key=private-test-service-key-never-expose",
    "platform.deployment-operations.provider-usage-mode=TEAM_AUTHORIZED",
    "platform.deployment-operations.team-authorized-targets=reviewed-staging",
    "platform.deployment-lifecycle.enabled=true",
    "platform.deployment-lifecycle.api-key=only-private-lifecycle-service-credential-123456",
    "platform.deployment.admission.lifecycle.worker-enabled=true",
    "platform.deployment.admission.release.worker-enabled=true",
    "platform.target-retirement.worker-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentOperationsApplicationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test void lifecycleCredentialIsRouteScopedAndCannotBecomeAnAdministrator() throws Exception {
        String key = "Bearer only-private-lifecycle-service-credential-123456";
        mvc.perform(get("/internal/v1/deployment-lifecycle/commands/missing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/deployment-lifecycle/commands/missing").header("Authorization", key))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/deployment-operations/status").header("Authorization", key))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/deployment-lifecycle/commands").header("Authorization", key)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/v1/deployment-lifecycle/commands").header("Authorization", key)
            .contentType(MediaType.APPLICATION_JSON).content("x".repeat(8193)))
            .andExpect(status().isPayloadTooLarge());
    }

    @Test void optionalServiceOutageDoesNotPreventStartupAndConfigurationIsAuthenticatedAndSafe() throws Exception {
        mvc.perform(get("/api/deployment-operations/status")).andExpect(status().isUnauthorized());
        var response = mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"operations-test@example.invalid\",\"password\":\"TestOperations123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotBlank();
        String[] cookie = setCookie.split(";", 2)[0].split("=", 2);
        var result = mvc.perform(get("/api/deployment-operations/status").cookie(new jakarta.servlet.http.Cookie(cookie[0], cookie[1])))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("ENFORCE"))
            .andExpect(jsonPath("$.decisionSource").value("EXTERNAL_SERVICE"))
            .andExpect(jsonPath("$.manualDeploymentsAllowed").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("private-test-service-key", "127.0.0.1", "password");
        assertThat(context.getBeansOfType(com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService.class))
            .hasSize(1);
        assertThat(context.getBeansOfType(com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentReleaseLifecycleService.class))
            .hasSize(1);
        assertThat(context.getBeansOfType(com.ai.fabric.platform.backend.deployment.service.ReleaseArtifactResolver.class))
            .hasSize(1);
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(beanName);
            if (type != null) assertThat(type.getName()).as("bean %s", beanName)
                .doesNotStartWith("com.ai.fabric.platform.backend.deployment.admission.")
                .doesNotStartWith("com.ai.fabric.platform.backend.deployment.retirement.")
                .doesNotStartWith("com.ai.fabric.platform.backend.deployment.generation.");
        }
        for (String beanName : java.util.List.of("admissionLifecycleWorker", "admissionReleaseReconciliationWorker",
            "targetRetirementScheduler", "deploymentReleaseRecoveryService", "unusedVectorTransferService",
            "unusedVectorBindingHandoffService")) {
            assertThat(context.containsBean(beanName)).as("retired bean %s", beanName).isFalse();
        }
    }

    @Test void runtimeClasspathContainsNoRetiredOperationalPackagesOrRecoveryEngine() throws Exception {
        var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver(context.getClassLoader());
        for (String name : java.util.List.of("admission", "retirement", "generation")) {
            assertThat(resources.getResources("classpath*:com/ai/fabric/platform/backend/deployment/" + name + "/**/*.class"))
                .as("retired package %s", name).isEmpty();
        }
        assertThat(context.getClassLoader().getResource(
            "com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseRecoveryService.class")).isNull();
    }
}
