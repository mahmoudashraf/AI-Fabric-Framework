package com.ai.fabric.runtime;

import ai.fabric.provider.onnx.ONNXAutoConfiguration;
import ai.fabric.provider.springai.SpringAiProviderAutoConfiguration;
import ai.fabric.vector.lucene.LuceneVectorAutoConfiguration;
import ai.fabric.vector.memory.MemoryVectorAutoConfiguration;
import ai.fabric.vector.milvus.MilvusVectorAutoConfiguration;
import ai.fabric.vector.pinecone.PineconeVectorAutoConfiguration;
import ai.fabric.vector.qdrant.QdrantVectorAutoConfiguration;
import ai.fabric.vector.weaviate.WeaviateVectorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePackagingCoverageTest {

    @Test
    void runtimeJarIncludesSpringAiProviderAndVectorModules() {
        assertThat(SpringAiProviderAutoConfiguration.class).isNotNull();
        assertThat(ONNXAutoConfiguration.class).isNotNull();
        assertThat(LuceneVectorAutoConfiguration.class).isNotNull();
        assertThat(MemoryVectorAutoConfiguration.class).isNotNull();
        assertThat(MilvusVectorAutoConfiguration.class).isNotNull();
        assertThat(PineconeVectorAutoConfiguration.class).isNotNull();
        assertThat(QdrantVectorAutoConfiguration.class).isNotNull();
        assertThat(WeaviateVectorAutoConfiguration.class).isNotNull();
    }

    @Test
    void runtimeJarAutoConfigurationImportsIncludeProviderAndVectorModules() {
        var candidates = ImportCandidates.load(
            AutoConfiguration.class,
            RuntimePackagingCoverageTest.class.getClassLoader()
        );

        assertThat(candidates)
            .contains(SpringAiProviderAutoConfiguration.class.getName())
            .contains(ONNXAutoConfiguration.class.getName())
            .contains(LuceneVectorAutoConfiguration.class.getName())
            .contains(MemoryVectorAutoConfiguration.class.getName())
            .contains(MilvusVectorAutoConfiguration.class.getName())
            .contains(PineconeVectorAutoConfiguration.class.getName())
            .contains(QdrantVectorAutoConfiguration.class.getName())
            .contains(WeaviateVectorAutoConfiguration.class.getName());
    }

    @Test
    void runtimeClasspathIncludesSupportCuratedPack() {
        assertThat(RuntimePackagingCoverageTest.class.getClassLoader().getResource("ai-curated/packs/default.yml"))
            .isNotNull();
        assertThat(RuntimePackagingCoverageTest.class.getClassLoader().getResource("ai-curated/packs/support.yml"))
            .isNotNull();
    }
}
