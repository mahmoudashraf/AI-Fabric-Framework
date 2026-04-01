package com.ai.fabric.runtime;

import com.ai.infrastructure.provider.azure.AzureOpenAIAutoConfiguration;
import com.ai.infrastructure.provider.cohere.CohereAutoConfiguration;
import com.ai.infrastructure.provider.gemini.GeminiAutoConfiguration;
import com.ai.infrastructure.provider.rest.RestEmbeddingAutoConfiguration;
import com.ai.infrastructure.vector.memory.MemoryVectorAutoConfiguration;
import com.ai.infrastructure.vector.milvus.MilvusVectorAutoConfiguration;
import com.ai.infrastructure.vector.pinecone.PineconeVectorAutoConfiguration;
import com.ai.infrastructure.vector.weaviate.WeaviateVectorAutoConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePackagingCoverageTest {

    @Test
    void runtimeJarIncludesExpandedProviderAndVectorModules() {
        assertThat(AzureOpenAIAutoConfiguration.class).isNotNull();
        assertThat(CohereAutoConfiguration.class).isNotNull();
        assertThat(GeminiAutoConfiguration.class).isNotNull();
        assertThat(RestEmbeddingAutoConfiguration.class).isNotNull();
        assertThat(MemoryVectorAutoConfiguration.class).isNotNull();
        assertThat(MilvusVectorAutoConfiguration.class).isNotNull();
        assertThat(PineconeVectorAutoConfiguration.class).isNotNull();
        assertThat(WeaviateVectorAutoConfiguration.class).isNotNull();
    }
}
