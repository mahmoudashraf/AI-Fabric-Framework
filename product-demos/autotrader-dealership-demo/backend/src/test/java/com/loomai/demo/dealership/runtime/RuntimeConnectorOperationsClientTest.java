package com.loomai.demo.dealership.runtime;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RuntimeConnectorOperationsClientTest {

    @Test
    void readsOnlyTheConfiguredDeploymentSourceWithReadScope() {
        DealershipDemoProperties properties = configuredProperties();
        RuntimePrivateAssertionSigner signer = mock(RuntimePrivateAssertionSigner.class);
        when(signer.authorizationValue(List.of(RuntimeConnectorOperationsClient.READ_SCOPE)))
            .thenReturn("Bearer read-assertion");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuntimeConnectorOperationsClient client = new RuntimeConnectorOperationsClient(properties, signer, builder);
        server.expect(requestTo("https://runtime.example/api/admin/connector/integrations/sources/autotrader-dealership-stock-source"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-AIFABRIC-RUNTIME-API-KEY", "trusted-key"))
            .andExpect(header("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer read-assertion"))
            .andRespond(withSuccess("{\"sourceId\":\"autotrader-dealership-stock-source\",\"freshnessState\":\"CURRENT\"}", MediaType.APPLICATION_JSON));

        assertThat(client.sourceStatus().path("freshnessState").asText()).isEqualTo("CURRENT");
        verify(signer).authorizationValue(List.of(RuntimeConnectorOperationsClient.READ_SCOPE));
        server.verify();
    }

    @Test
    void reconcilesOnlyTheConfiguredDeploymentSourceWithWriteScope() {
        DealershipDemoProperties properties = configuredProperties();
        RuntimePrivateAssertionSigner signer = mock(RuntimePrivateAssertionSigner.class);
        when(signer.authorizationValue(List.of(RuntimeConnectorOperationsClient.WRITE_SCOPE)))
            .thenReturn("Bearer write-assertion");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuntimeConnectorOperationsClient client = new RuntimeConnectorOperationsClient(properties, signer, builder);
        server.expect(requestTo("https://runtime.example/api/admin/connector/integrations/sources/autotrader-dealership-stock-source/reconcile"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-AIFABRIC-RUNTIME-API-KEY", "trusted-key"))
            .andExpect(header("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer write-assertion"))
            .andExpect(request -> assertThat(request.getHeaders().getFirst("Idempotency-Key")).startsWith("dealership-reconcile-"))
            .andRespond(withSuccess("{\"sourceId\":\"autotrader-dealership-stock-source\",\"status\":\"COMPLETED\"}", MediaType.APPLICATION_JSON));

        assertThat(client.reconcileSource().path("status").asText()).isEqualTo("COMPLETED");
        verify(signer).authorizationValue(List.of(RuntimeConnectorOperationsClient.WRITE_SCOPE));
        server.verify();
    }

    @Test
    void rejectsAnUnboundedSourceIdentifierBeforeMakingARequest() {
        DealershipDemoProperties properties = configuredProperties();
        properties.getRuntime().setIntegrationSourceId("../../other-source");
        RuntimeConnectorOperationsClient client = new RuntimeConnectorOperationsClient(
            properties,
            mock(RuntimePrivateAssertionSigner.class),
            RestClient.builder()
        );

        assertThatThrownBy(client::sourceStatus)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("integration source");
    }

    @Test
    void waitsForTheExactWebhookEventToReachTerminalConnectorEvidence() {
        DealershipDemoProperties properties = configuredProperties();
        RuntimePrivateAssertionSigner signer = mock(RuntimePrivateAssertionSigner.class);
        when(signer.authorizationValue(List.of(RuntimeConnectorOperationsClient.READ_SCOPE)))
            .thenReturn("Bearer read-assertion");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuntimeConnectorOperationsClient client = new RuntimeConnectorOperationsClient(properties, signer, builder);
        server.expect(requestTo("https://runtime.example/api/admin/connector/integrations/webhooks/autotrader-stock-events/events"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"eventId\":\"stock-update-1\",\"status\":\"COMPLETED\",\"attemptCount\":1}]", MediaType.APPLICATION_JSON));

        RuntimeConnectorOperationsClient.WebhookEventEvidence evidence = client.awaitWebhookEvent(
            "stock-update-1",
            Duration.ofSeconds(1)
        );

        assertThat(evidence.status()).isEqualTo("COMPLETED");
        assertThat(evidence.attemptCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    void initializationVerifiesTheActualScopedSourceCountsFreshnessAndWork() {
        var fixture = readinessFixture();
        ObjectNode source = readySource();
        fixture.server().expect(requestTo(sourceUrl() + "/reconcile"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "receiver-init-" + "a".repeat(64)))
            .andRespond(withSuccess(source.path("state").toString(), MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(sourceUrl()))
            .andRespond(withSuccess(source.toString(), MediaType.APPLICATION_JSON));

        var evidence = fixture.client().initializeSource("a".repeat(64));

        assertThat(evidence.resourceBindingRef()).isEqualTo("advertiser-binding");
        assertThat(evidence.indexedCount()).isEqualTo(3);
        assertThat(evidence.completedWorkCount()).isEqualTo(3);
        assertThat(evidence.observedAt()).isEqualTo(Instant.parse(source.path("state").path("lastSuccessAt").asText()));
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "wrong-source", "disabled", "wrong-profile", "wrong-binding", "wrong-vector",
        "wrong-state-source", "wrong-version", "not-synced", "stale", "old-timestamp", "future-timestamp",
        "missing-counts", "string-count", "negative-count", "pending", "failed", "partial", "zero-indexed",
        "bad-completed-count", "bad-deleted-count", "newer-error", "error-class", "missing-work", "pending-work",
        "failed-work", "wrong-work-source", "auth-failed"})
    void incompleteOrForeignEvidenceCannotBecomeReady(String mutation) {
        var fixture = readinessFixture();
        ObjectNode source = readySource();
        ObjectNode state = (ObjectNode) source.path("state");
        ObjectNode counts = (ObjectNode) state.path("counts");
        switch (mutation) {
            case "empty" -> source.removeAll();
            case "wrong-source" -> source.put("sourceId", "foreign");
            case "disabled" -> source.put("enabled", false);
            case "wrong-profile" -> source.put("connectionProfileRef", "foreign");
            case "wrong-binding" -> source.put("protectedResourceBindingRef", "foreign");
            case "wrong-vector" -> source.put("vectorSpace", "foreign");
            case "wrong-state-source" -> state.put("sourceId", "foreign");
            case "wrong-version" -> state.put("sourceVersion", "old");
            case "not-synced" -> source.put("freshnessState", "NOT_SYNCED");
            case "stale" -> source.put("freshnessState", "STALE");
            case "old-timestamp" -> state.put("lastSuccessAt", Instant.now().minusSeconds(3600).toString());
            case "future-timestamp" -> state.put("updatedAt", Instant.now().plusSeconds(3600).toString());
            case "missing-counts" -> state.remove("counts");
            case "string-count" -> counts.put("indexedCount", "3");
            case "negative-count" -> counts.put("deletedCount", -1);
            case "pending" -> counts.put("completedWorkCount", 2);
            case "failed" -> counts.put("failedWorkCount", 1);
            case "partial" -> state.put("status", "PARTIAL");
            case "zero-indexed" -> counts.put("indexedCount", 0);
            case "bad-completed-count" -> counts.put("completedWorkCount", 10);
            case "bad-deleted-count" -> counts.put("deletedCount", 2);
            case "newer-error" -> state.put("lastErrorAt", Instant.now().toString());
            case "error-class" -> state.put("errorClass", "INDEXING_WORK_FAILED");
            case "missing-work" -> source.remove("work");
            case "pending-work" -> ((ObjectNode) source.path("work").get(0)).put("status", "PROCESSING");
            case "failed-work" -> ((ObjectNode) source.path("work").get(0)).put("status", "FAILED");
            case "wrong-work-source" -> ((ObjectNode) source.path("work").get(0)).put("sourceId", "foreign");
            case "auth-failed" -> source.put("preflightState", "FAILED");
            default -> throw new AssertionError(mutation);
        }
        fixture.server().expect(requestTo(sourceUrl())).andRespond(withSuccess(source.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(fixture.client()::currentSourceEvidence).hasMessageContaining("CONNECTOR_SCOPED_INDEXING_NOT_READY");
        fixture.server().verify();
    }

    @Test
    void recoveredHistoricalWorkDoesNotOverrideANewSuccessfulBaseline() {
        var fixture = readinessFixture();
        ObjectNode source = readySource();
        ((ObjectNode) source.path("work").get(0)).put("status", "FAILED")
            .put("errorCode", "OLD_FAILURE").put("updatedAt", Instant.now().minusSeconds(60).toString());
        fixture.server().expect(requestTo(sourceUrl())).andRespond(withSuccess(source.toString(), MediaType.APPLICATION_JSON));
        assertThat(fixture.client().currentSourceEvidence().indexedCount()).isEqualTo(3);
        fixture.server().verify();
    }

    @Test
    void failedReconciliationCannotBorrowAPreviousSuccessfulReadback() {
        var fixture = readinessFixture();
        ObjectNode state = (ObjectNode) readySource().path("state");
        state.put("status", "FAILED");
        fixture.server().expect(requestTo(sourceUrl() + "/reconcile"))
            .andRespond(withSuccess(state.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> fixture.client().initializeSource("a".repeat(64)))
            .hasMessageContaining("CONNECTOR_SCOPED_INDEXING_NOT_READY");
        fixture.server().verify();
    }

    @Test
    void nonNullStatusWithoutConfiguredBindingExpectationsCannotProveReadiness() {
        var client = new RuntimeConnectorOperationsClient(configuredProperties(), mock(RuntimePrivateAssertionSigner.class), RestClient.builder());
        assertThatThrownBy(client::currentSourceEvidence).hasMessageContaining("readiness requirements are not configured");
    }

    private Fixture readinessFixture() {
        var signer = mock(RuntimePrivateAssertionSigner.class);
        when(signer.authorizationValue(List.of(RuntimeConnectorOperationsClient.READ_SCOPE))).thenReturn("Bearer read-assertion");
        when(signer.authorizationValue(List.of(RuntimeConnectorOperationsClient.WRITE_SCOPE))).thenReturn("Bearer write-assertion");
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RuntimeConnectorOperationsClient(configuredProperties(), signer, builder);
        ReflectionTestUtils.setField(client, "expectedConnectionProfileRef", "provider-profile");
        ReflectionTestUtils.setField(client, "expectedResourceBindingRef", "advertiser-binding");
        ReflectionTestUtils.setField(client, "expectedSourceVersion", "v1");
        return new Fixture(client, server);
    }

    private ObjectNode readySource() {
        var mapper = new ObjectMapper();
        String success = Instant.now().minusSeconds(5).toString();
        ObjectNode source = mapper.createObjectNode().put("sourceId", "autotrader-dealership-stock-source")
            .put("enabled", true).put("connectionProfileRef", "provider-profile")
            .put("protectedResourceBindingRef", "advertiser-binding").put("vectorSpace", "dealer-vehicle")
            .put("preflightState", "READY").put("freshnessState", "CURRENT");
        ObjectNode state = source.putObject("state").put("sourceId", "autotrader-dealership-stock-source")
            .put("sourceVersion", "v1").put("status", "COMPLETED")
            .put("lastStartedAt", Instant.now().minusSeconds(10).toString())
            .put("lastSuccessAt", success).put("updatedAt", success);
        state.putObject("counts").put("sourceCount", 3).put("normalizedCount", 3).put("indexedCount", 3)
            .put("deletedCount", 0).put("acceptedWorkCount", 3).put("completedWorkCount", 3).put("failedWorkCount", 0);
        source.putArray("work").addObject().put("sourceId", "autotrader-dealership-stock-source")
            .put("status", "COMPLETED").put("updatedAt", success);
        return source;
    }

    private String sourceUrl() {
        return "https://runtime.example/api/admin/connector/integrations/sources/autotrader-dealership-stock-source";
    }

    private record Fixture(RuntimeConnectorOperationsClient client, MockRestServiceServer server) { }

    private DealershipDemoProperties configuredProperties() {
        DealershipDemoProperties properties = new DealershipDemoProperties();
        properties.getRuntime().setEnabled(true);
        properties.getRuntime().setBaseUrl("https://runtime.example/");
        properties.getRuntime().setIntegrationSourceId("autotrader-dealership-stock-source");
        properties.getRuntime().setIntegrationWebhookSourceId("autotrader-stock-events");
        properties.getRuntime().getPrivateAccess().setTrustedApiKey("trusted-key");
        return properties;
    }
}
