package com.ai.fabric.vectorization.runner.config;

import com.ai.fabric.vectorization.adapter.source.FileVectorizationSourceAdapter;
import com.ai.fabric.vectorization.adapter.source.RestApiVectorizationSourceAdapter;
import com.ai.fabric.vectorization.adapter.source.SqlVectorizationSourceAdapter;
import com.ai.fabric.vectorization.adapter.source.VectorizationSourceAdapter;
import com.ai.fabric.vectorization.mapping.VectorizationRecordMapper;
import com.ai.fabric.vectorization.runner.service.VectorizationSourceAdapterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class VectorizationRunnerConfiguration {

    @Bean
    public VectorizationRecordMapper vectorizationRecordMapper(ObjectMapper objectMapper) {
        return new VectorizationRecordMapper(objectMapper);
    }

    @Bean
    public VectorizationSourceAdapterRegistry vectorizationSourceAdapterRegistry(ObjectMapper objectMapper) {
        List<VectorizationSourceAdapter> adapters = List.of(
            new RestApiVectorizationSourceAdapter(objectMapper),
            new FileVectorizationSourceAdapter(objectMapper),
            new SqlVectorizationSourceAdapter(objectMapper)
        );
        return new VectorizationSourceAdapterRegistry(adapters);
    }
}
