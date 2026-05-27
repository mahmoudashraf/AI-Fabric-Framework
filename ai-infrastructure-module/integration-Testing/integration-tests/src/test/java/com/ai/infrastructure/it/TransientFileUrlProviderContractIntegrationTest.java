package com.ai.infrastructure.it;

import com.ai.infrastructure.dto.AIGenerationInputPart;
import com.ai.infrastructure.dto.AIGenerationInputType;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.provider.TransientInputSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransientFileUrlProviderContractIntegrationTest {

    @Test
    void transientFileUrlInputsRemainProviderPartsAndUnsupportedProvidersReturnNotUsedEvidence() {
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Analyze this document")
            .inputParts(List.of(AIGenerationInputPart.builder()
                .type(AIGenerationInputType.FILE_URL)
                .documentId("doc-123")
                .fileName("project-brief.pdf")
                .contentType("application/pdf")
                .url("https://produs-api-staging.46.224.145.148.sslip.io/tmp/project-brief.pdf?sig=secret")
                .build()))
            .build();

        assertThat(TransientInputSupport.fileUrlInputParts(request))
            .singleElement()
            .extracting(AIGenerationInputPart::getUrl)
            .asString()
            .contains("produs-api-staging");

        var response = TransientInputSupport.unsupportedFileUrlResponse(
            request,
            "cohere",
            "provider does not support external file URL inputs"
        );

        assertThat(response.getStatus()).isEqualTo("PROVIDER_FILE_URL_INPUT_UNSUPPORTED");
        assertThat(response.getContent()).contains("\"documentId\":\"doc-123\"");
        assertThat(response.getContent()).contains("\"status\":\"NOT_USED\"");
        assertThat(response.getContent()).doesNotContain("produs-api-staging");
        assertThat(response.getMetadata().toString()).contains("[REDACTED_TRANSIENT_FILE_URL]");
    }
}
