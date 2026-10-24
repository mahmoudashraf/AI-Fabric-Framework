package com.loomai.operations.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OperationsConfiguration {

    @Bean
    Clock operationsClock() {
        return Clock.systemUTC();
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJson() {
        return builder -> builder
            .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS, JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT, MapperFeature.ALLOW_COERCION_OF_SCALARS,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .deserializerByType(Instant.class, new IsoInstantDeserializer())
            .postConfigurer(mapper -> {
                mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32).maxStringLength(4096).maxNumberLength(20).build());
                mapper.coercionConfigFor(LogicalType.Textual)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
    }

    @Bean
    TargetCapacityPolicy targetCapacityPolicy(OperationsProperties properties, ObjectMapper mapper, Validator validator) {
        requireDuration(properties.holdTtl(), Duration.ofSeconds(5), Duration.ofMinutes(30), "hold TTL");
        requireDuration(properties.maxRequestAge(), Duration.ofSeconds(5), Duration.ofMinutes(30), "request age");
        requireDuration(properties.maxClockSkew(), Duration.ZERO, Duration.ofMinutes(2), "clock skew");
        if (properties.maxBodyBytes() < 4096 || properties.maxBodyBytes() > 65536) {
            throw new IllegalStateException("Deployment operations body limit must be between 4096 and 65536 bytes.");
        }
        String json = properties.capacityPolicyJson();
        if (json == null || json.length() > 65536) {
            throw new IllegalStateException("Deployment operations capacity policy JSON is required and bounded.");
        }
        TargetCapacityPolicy policy;
        try {
            policy = mapper.readValue(json, TargetCapacityPolicy.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Deployment operations capacity policy JSON is invalid.");
        }
        if (policy == null || !validator.validate(policy).isEmpty()) {
            throw new IllegalStateException("Deployment operations capacity policy fields are invalid.");
        }
        return policy;
    }

    private void requireDuration(Duration value, Duration min, Duration max, String name) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalStateException("Deployment operations " + name + " is outside its permitted range.");
        }
    }
}
