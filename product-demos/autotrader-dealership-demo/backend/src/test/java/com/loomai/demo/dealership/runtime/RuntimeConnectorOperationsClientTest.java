package com.loomai.demo.dealership.runtime;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.time.Duration;

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
