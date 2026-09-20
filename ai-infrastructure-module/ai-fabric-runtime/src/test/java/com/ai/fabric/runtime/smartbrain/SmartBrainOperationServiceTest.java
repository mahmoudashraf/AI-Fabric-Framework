package com.ai.fabric.runtime.smartbrain;

import ai.fabric.execution.specialist.client.SpecialistClient;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SmartBrainOperationServiceTest {

    @Test
    void exposesStoredResultAsJsonCompatibleValues() {
        SpecialistClientFactory specialistClientFactory = mock(SpecialistClientFactory.class);
        SpecialistClient<JsonNode, JsonNode> specialistClient = mock(SpecialistClient.class);
        doReturn(specialistClient).when(specialistClientFactory).bind(
            SmartBrainOperationService.SPECIALIST_ID,
            JsonNode.class,
            JsonNode.class
        );
        SmartBrainSecurity security = new SmartBrainSecurity(
            "test-encryption-secret-with-sufficient-entropy",
            "test-fingerprint-secret-with-different-entropy"
        );
        SmartBrainOperationService service = new SmartBrainOperationService(
            mock(SmartBrainOperationRepository.class),
            mock(SmartBrainConfigurationService.class),
            security,
            specialistClientFactory,
            mock(SmartBrainDeliveryService.class),
            new ObjectMapper().findAndRegisterModules(),
            Duration.ofDays(30),
            "https://runtime.example",
            "tenant-1",
            "deployment-1"
        );
        Instant now = Instant.parse("2026-09-20T03:44:17Z");
        SmartBrainOperationEntity operation = new SmartBrainOperationEntity();
        operation.setOperationId("sbo-1");
        operation.setTriggerCode("event-analysis");
        operation.setCloudEventId("event-1");
        operation.setCloudEventType("com.loomai.smart-brain.analysis.requested");
        operation.setStatus("SUCCEEDED");
        operation.setProtectedResult(security.encrypt("""
            {"summary":"Release risk is low.","riskLevel":"LOW","signals":{"failed":0}}
            """));
        operation.setAcceptedRuntimeUrl("https://runtime.example");
        operation.setCreatedAt(now.minusSeconds(5));
        operation.setUpdatedAt(now);
        operation.setCompletedAt(now);
        operation.setExpiresAt(now.plus(Duration.ofDays(30)));

        SmartBrainOperationView view = service.toView(operation, false);

        assertThat(view.result()).isEqualTo(Map.of(
            "summary", "Release risk is low.",
            "riskLevel", "LOW",
            "signals", Map.of("failed", 0)
        ));
    }
}
