package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateErrorMessage;
import io.weaviate.client.base.WeaviateErrorResponse;
import io.weaviate.client.v1.schema.Schema;
import io.weaviate.client.v1.schema.api.ClassGetter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeaviateVectorDatabaseServiceTest {

    @Test
    void getVectorCountReturnsZeroWhenWeaviateClassDoesNotExistYet() {
        AIProviderConfig config = new AIProviderConfig();
        AIProviderConfig.WeaviateConfig weaviate = config.getWeaviate();
        weaviate.setEnabled(true);
        weaviate.setScheme("https");
        weaviate.setHost("example.weaviate.cloud");
        weaviate.setPort(443);
        weaviate.setApiKey("test-key");

        WeaviateClient client = mock(WeaviateClient.class);
        Schema schema = mock(Schema.class);
        ClassGetter classGetter = mock(ClassGetter.class);
        when(client.schema()).thenReturn(schema);
        when(schema.classGetter()).thenReturn(classGetter);
        when(classGetter.withClassName(anyString())).thenReturn(classGetter);
        when(classGetter.run()).thenReturn(new Result<>(
            404,
            null,
            WeaviateErrorResponse.builder()
                .code(404)
                .message("class not found")
                .error(List.of(WeaviateErrorMessage.builder().message("class not found").build()))
                .build()
        ));

        WeaviateVectorDatabaseService service = new WeaviateVectorDatabaseService(config, null, client);

        assertThat(service.getVectorCountByEntityType("product")).isZero();
    }
}
