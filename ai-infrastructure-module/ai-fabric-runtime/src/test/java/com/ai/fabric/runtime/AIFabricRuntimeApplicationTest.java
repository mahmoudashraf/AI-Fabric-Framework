package com.ai.fabric.runtime;

import ai.fabric.intent.action.connector.AIActionConnectorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml",
    properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "AI_ACTIONS_CONNECTOR_ADMIN_API_KEY=deployment-admin-secret",
    "AI_ACTIONS_CONNECTOR_ADMIN_API_KEY_HEADER=X-DEPLOYMENT-ADMIN-KEY",
    "spring.datasource.url=jdbc:h2:mem:runtime-context-loads;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED",
    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-trusted-backend-secret",
    "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=runtime-private-signing-secret",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=platform-poc:SESSION,platform-poc:API_KEY,platform-poc:SYSTEM",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-runtime"
})
class AIFabricRuntimeApplicationTest {

    @Autowired
    private AIActionConnectorProperties connectorProperties;

    @Test
    void contextLoads() {
        assertThat(connectorProperties.getAdmin().getHeader()).isEqualTo("X-DEPLOYMENT-ADMIN-KEY");
        assertThat(connectorProperties.getAdmin().getValue()).isEqualTo("deployment-admin-secret");
    }
}
