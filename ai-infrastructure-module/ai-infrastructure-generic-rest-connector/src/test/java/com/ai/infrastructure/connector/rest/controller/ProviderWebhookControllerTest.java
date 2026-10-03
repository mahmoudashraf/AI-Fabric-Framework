package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderWebhookControllerTest {

    @Test
    void mapsTargetedRecordContractFailuresToStableClientStatuses() {
        assertThat(ProviderWebhookController.rejectionStatus(null, "WEBHOOK_RECORD_KEY_MISSING"))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ProviderWebhookController.rejectionStatus(null, "WEBHOOK_RECORD_KEY_INVALID"))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ProviderWebhookController.rejectionStatus(null, "WEBHOOK_EVENT_ID_CONFLICT"))
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void honorsProviderSpecificResourceMismatchStatusWithoutDomainCoupling() {
        RestRoutingConfig.WebhookSource source = new RestRoutingConfig.WebhookSource();
        source.getResponseStatuses().setResourceMismatch(422);

        assertThat(ProviderWebhookController.rejectionStatus(source, "WEBHOOK_RESOURCE_MISMATCH").value())
            .isEqualTo(422);
    }
}
