package com.ai.fabric.platform.backend.support.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SupportRuntimeClientTest {
    @Test void springSelectsTheProductionConstructorWithoutAProvidedHttpClientBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(SupportRuntimeClient.class);
            context.refresh();
            assertThat(context.getBean(SupportRuntimeClient.class)).isNotNull();
            assertThat(context.getBeansOfType(java.net.http.HttpClient.class)).isEmpty();
        }
    }
}
