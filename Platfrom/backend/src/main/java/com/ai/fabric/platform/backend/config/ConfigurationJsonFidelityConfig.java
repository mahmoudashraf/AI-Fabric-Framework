package com.ai.fabric.platform.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigurationJsonFidelityConfig {
    @Bean
    SimpleModule exactConfigurationJson() {
        ObjectReader reader = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build().readerFor(JsonNode.class);
        SimpleModule module = new SimpleModule("exact-configuration-json-trees");
        module.addDeserializer(JsonNode.class, new JsonDeserializer<>() {
            @Override
            public JsonNode deserialize(com.fasterxml.jackson.core.JsonParser parser, DeserializationContext context)
                throws java.io.IOException {
                return reader.readValue(parser);
            }
        });
        return module;
    }
}
