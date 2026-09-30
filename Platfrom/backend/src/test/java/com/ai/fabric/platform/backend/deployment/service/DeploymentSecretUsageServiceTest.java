package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.DeploymentSecretPurposeCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentSecretUsageServiceTest {

    @Test
    void reportsReferencedManagedIntegrationSecretAsPresent() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.listSecrets()).thenReturn(List.of());
        when(secretService.isManagedSecretName("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(true);
        when(secretService.describeSecret("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(new PlatformSecretSummary(
            "MANAGED_CUSTOMER_BACKEND_API_KEY",
            "Customer backend API key",
            "Deployment-managed integration credential.",
            false,
            true,
            "DATABASE",
            "2026-09-30T00:00:00Z",
            "DEPLOYMENT_MANAGED",
            "PLATFORM_MANAGED",
            null,
            "dep-test",
            true,
            "DELETE_WITH_DEPLOYMENT"
        ));

        DeploymentSecretUsageService service = new DeploymentSecretUsageService(
            secretService,
            mock(DeploymentProviderSecretResolutionService.class),
            mock(DeploymentSecretPurposeCatalog.class),
            new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)),
            new ObjectMapper()
        );
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson("{}");
        draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false,\"connectorApiKeyEnabled\":true}");
        draft.setMarketplaceDatasetConfigJson(DeploymentDraftEntity.DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON);
        draft.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {
                  "api-key": {"value": "${CONNECTOR_API_KEY}"}
                },
                "upstream": {
                  "auth": {"value": "${MANAGED_CUSTOMER_BACKEND_API_KEY}"}
                }
              }
            }
            """);

        var result = service.build("dep-test", draft);

        assertThat(result.literalRiskCount()).isZero();
        assertThat(result.secrets())
            .filteredOn(item -> item.secretName().equals("MANAGED_CUSTOMER_BACKEND_API_KEY"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.required()).isTrue();
                assertThat(item.present()).isTrue();
                assertThat(item.status()).isEqualTo("READY");
                assertThat(item.source()).isEqualTo("DATABASE");
            });
    }
}
