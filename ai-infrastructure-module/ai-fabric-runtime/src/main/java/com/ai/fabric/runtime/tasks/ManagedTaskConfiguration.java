package com.ai.fabric.runtime.tasks;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ManagedTaskConfiguration {
    @Bean(name = "managedTaskExecutor", destroyMethod = "shutdownNow")
    ThreadPoolExecutor managedTaskExecutor(@Value("${loomai.managed-tasks.workers:4}") int workers,
        @Value("${loomai.managed-tasks.queue-capacity:8}") int queue) {
        // The HTTP contract remains synchronous; the bounded worker enforces its wall-clock wait.
        if (workers < 1 || workers > 32 || queue < 1 || queue > 128)
            throw new IllegalArgumentException("Managed task worker and queue bounds are invalid.");
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue),
            Thread.ofPlatform().name("managed-task-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    WebMvcConfigurer managedTaskJsonConfiguration(ObjectMapper mapper) {
        return new WebMvcConfigurer() {
            @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                builder.configureMessageConvertersList(converters -> converters.addFirst(new TaskJsonConverter(mapper)));
            }
        };
    }

    /** Framework schemas use Jackson 2 nodes; leave Boot's converters for unrelated APIs intact. */
    @SuppressWarnings("removal")
    static final class TaskJsonConverter extends MappingJackson2HttpMessageConverter {
        TaskJsonConverter(ObjectMapper mapper) {
            super(mapper.copy().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
        }
        @Override public boolean canRead(Type type, Class<?> context, MediaType media) {
            return type == ManagedTaskRequest.class && super.canRead(type, context, media);
        }
        @Override public boolean canRead(Class<?> type, MediaType media) {
            return type == ManagedTaskRequest.class && super.canRead(type, media);
        }
        @Override public boolean canWrite(Class<?> type, MediaType media) {
            return (type == ManagedTaskResponse.class || type == ManagedTaskService.TaskCatalogue.class || type == ManagedTaskController.Error.class)
                && super.canWrite(type, media);
        }
    }
}
