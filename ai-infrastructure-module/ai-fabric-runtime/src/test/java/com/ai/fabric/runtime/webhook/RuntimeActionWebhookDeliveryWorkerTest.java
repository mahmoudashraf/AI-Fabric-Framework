package com.ai.fabric.runtime.webhook;

import com.ai.infrastructure.http.OutboundHttpExecutionRequest;
import com.ai.infrastructure.http.OutboundHttpExecutionResponse;
import com.ai.infrastructure.http.OutboundHttpExecutor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeActionWebhookDeliveryWorkerTest {

    @Test
    void dispatchReadyDeliveriesMarksSuccessfulResponsesDelivered() {
        RuntimeWebhookSecretResolver secretResolver = mock(RuntimeWebhookSecretResolver.class);
        when(secretResolver.resolveRequired("ZAPIER_URL")).thenReturn("https://hooks.example.test/zapier");
        TestableWorker worker = new TestableWorker(
            new StubOutboundExecutor(new OutboundHttpExecutionResponse(202, "", java.util.Map.of())),
            secretResolver
        );
        worker.deliveries = List.of(delivery("whd-1"));

        worker.dispatchReadyDeliveries();

        assertThat(worker.deliveredId).isEqualTo("whd-1");
        assertThat(worker.failedId).isNull();
        assertThat(worker.retryId).isNull();
    }

    @Test
    void dispatchReadyDeliveriesMarksClientErrorsFailed() {
        RuntimeWebhookSecretResolver secretResolver = mock(RuntimeWebhookSecretResolver.class);
        when(secretResolver.resolveRequired("ZAPIER_URL")).thenReturn("https://hooks.example.test/zapier");
        TestableWorker worker = new TestableWorker(
            new StubOutboundExecutor(new OutboundHttpExecutionResponse(400, "", java.util.Map.of())),
            secretResolver
        );
        worker.deliveries = List.of(delivery("whd-2"));

        worker.dispatchReadyDeliveries();

        assertThat(worker.failedId).isEqualTo("whd-2");
        assertThat(worker.deliveredId).isNull();
    }

    @Test
    void dispatchReadyDeliveriesRejectsWebhookUrlsWithUserInfo() {
        RuntimeWebhookSecretResolver secretResolver = mock(RuntimeWebhookSecretResolver.class);
        when(secretResolver.resolveRequired("ZAPIER_URL")).thenReturn("https://user:pass@hooks.example.test/zapier");
        TestableWorker worker = new TestableWorker(
            new StubOutboundExecutor(new OutboundHttpExecutionResponse(202, "", java.util.Map.of())),
            secretResolver
        );
        worker.deliveries = List.of(delivery("whd-3"));

        worker.dispatchReadyDeliveries();

        assertThat(worker.failedId).isEqualTo("whd-3");
        assertThat(worker.deliveredId).isNull();
        assertThat(worker.retryId).isNull();
    }

    private static ActionWebhookDeliveryEntity delivery(String id) {
        ActionWebhookDeliveryEntity entity = new ActionWebhookDeliveryEntity();
        entity.setId(id);
        entity.setActionName("cancel_order");
        entity.setEventType("order.cancelled");
        entity.setTargetRef("zapier_orders");
        entity.setUrlSecretRef("ZAPIER_URL");
        entity.setTimeoutMs(3000);
        entity.setMaxAttempts(5);
        entity.setAttemptCount(0);
        entity.setStatus(ActionWebhookDeliveryEntity.STATUS_PENDING);
        entity.setPayloadJson("{\"eventType\":\"order.cancelled\"}");
        entity.setNextAttemptAt(Instant.parse("2026-04-17T10:00:00Z"));
        return entity;
    }

    private static final class TestableWorker extends RuntimeActionWebhookDeliveryWorker {
        private List<ActionWebhookDeliveryEntity> deliveries = List.of();
        private String deliveredId;
        private String failedId;
        private String retryId;

        private TestableWorker(OutboundHttpExecutor outboundHttpExecutor,
                               RuntimeWebhookSecretResolver secretResolver) {
            super(
                mock(ActionWebhookDeliveryRepository.class),
                outboundHttpExecutor,
                secretResolver,
                Clock.fixed(Instant.parse("2026-04-17T10:00:00Z"), ZoneOffset.UTC)
            );
        }

        @Override
        protected List<ActionWebhookDeliveryEntity> claimReadyDeliveries() {
            return deliveries;
        }

        @Override
        protected void markDelivered(String deliveryId, Integer statusCode) {
            this.deliveredId = deliveryId;
        }

        @Override
        protected void markRetryPending(String deliveryId, Integer statusCode, String message) {
            this.retryId = deliveryId;
        }

        @Override
        protected void markFailed(String deliveryId, Integer statusCode, String message) {
            this.failedId = deliveryId;
        }
    }

    private record StubOutboundExecutor(OutboundHttpExecutionResponse response) implements OutboundHttpExecutor {
        @Override
        public OutboundHttpExecutionResponse execute(OutboundHttpExecutionRequest request) {
            return response;
        }
    }
}
