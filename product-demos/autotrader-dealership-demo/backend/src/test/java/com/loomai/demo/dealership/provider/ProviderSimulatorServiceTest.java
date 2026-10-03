package com.loomai.demo.dealership.provider;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.runtime.RuntimeConnectorOperationsClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderSimulatorServiceTest {

    @Test
    void updatePriceMutatesOneStockRecordThenEmitsTheConfiguredNotification() {
        DealershipDemoProperties configuration = configuredProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RuntimeConnectorOperationsClient connectorOperations = mock(RuntimeConnectorOperationsClient.class);
        when(connectorOperations.awaitWebhookEvent(eq("stock-update-1"), any()))
            .thenReturn(new RuntimeConnectorOperationsClient.WebhookEventEvidence("COMPLETED", 1, null));
        ProviderSimulatorService service = new ProviderSimulatorService(configuration, builder, connectorOperations);

        server.expect(requestTo("https://simulator.example/internal/control/accounts/autotrader/10020030/vehicles/DEMO-1001"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header(ProviderSimulatorService.CONTROL_HEADER, "operator-control-key"))
            .andExpect(content().json("""
                {
                  "id":"DEMO-1001",
                  "make":"Aster",
                  "model":"E1",
                  "derivative":"Motion Long Range",
                  "year":2025,
                  "priceMinor":3095000,
                  "currency":"GBP",
                  "fuelType":"Electric",
                  "bodyStyle":"SUV",
                  "transmission":"Automatic",
                  "mileage":4850,
                  "state":"active"
                }
                """))
            .andRespond(withSuccess("""
                {"profile":"autotrader","accountId":"10020030","vehicleId":"DEMO-1001","operation":"UPSERT","sourceVersion":2}
                """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://simulator.example/internal/control/accounts/autotrader/10020030/events"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(ProviderSimulatorService.CONTROL_HEADER, "operator-control-key"))
            .andExpect(content().json("""
                {
                  "variant":"VALID",
                  "targetUrl":"https://connector.example/integrations/webhooks/autotrader-stock-events",
                  "vehicleId":"DEMO-1001"
                }
                """))
            .andRespond(withSuccess("""
                {"eventId":"stock-update-1","variant":"VALID","profile":"autotrader","accountId":"10020030","targetHost":"connector.example","sequence":7,"attempts":[{"attempt":1,"status":202,"responseClass":"ACCEPTED"}]}
                """, MediaType.APPLICATION_JSON));

        ProviderSimulatorService.ScenarioReceipt receipt = service.run("update-price");

        assertThat(receipt.scenario()).isEqualTo("update-price");
        assertThat(receipt.stockId()).isEqualTo("DEMO-1001");
        assertThat(receipt.mutationOperation()).isEqualTo("UPSERT");
        assertThat(receipt.eventId()).isEqualTo("stock-update-1");
        assertThat(receipt.eventSequence()).isEqualTo(7);
        assertThat(receipt.deliveryStatus()).isEqualTo(202);
        assertThat(receipt.reconciliationStatus()).isEqualTo("COMPLETED");
        assertThat(receipt.reconciliationAttempts()).isEqualTo(1);
        assertThat(receipt.reconciliationErrorClass()).isNull();
        server.verify();
    }

    @Test
    void disabledSimulatorExposesNoScenariosAndRejectsMutation() {
        DealershipDemoProperties configuration = new DealershipDemoProperties();
        ProviderSimulatorService service = new ProviderSimulatorService(
            configuration,
            RestClient.builder(),
            mock(RuntimeConnectorOperationsClient.class)
        );

        assertThat(service.status().enabled()).isFalse();
        assertThat(service.status().availableScenarios()).isEmpty();
        assertThatThrownBy(() -> service.run("add-stock"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled");
    }

    private DealershipDemoProperties configuredProperties() {
        DealershipDemoProperties configuration = new DealershipDemoProperties();
        var simulator = configuration.getProviderSimulator();
        simulator.setEnabled(true);
        simulator.setBaseUrl("https://simulator.example");
        simulator.setControlApiKey("operator-control-key");
        simulator.setAdvertiserId("10020030");
        simulator.setWebhookTargetUrl("https://connector.example/integrations/webhooks/autotrader-stock-events");
        return configuration;
    }
}
