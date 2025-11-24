package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.service.DynamicJPAQueryBuilder;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.relationship.service.JpaRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.LLMDrivenJPAQueryService;
import com.ai.infrastructure.relationship.service.MetadataRelationshipTraversalService;
import com.ai.infrastructure.relationship.service.RelationshipQueryPlanner;
import com.ai.infrastructure.relationship.service.RelationshipSchemaProvider;
import com.ai.infrastructure.relationship.service.RelationshipTraversalService;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Test configuration that mirrors real application behavior by loading the backend
 * {@code .env} file so the OpenAI credentials used in production are available during
 * the integration tests. It also ensures Jackson can deserialize offset timestamps that
 * appear in {@link com.ai.infrastructure.dto.RAGResponse}.
 */
@Slf4j
@TestConfiguration
public class BackendEnvTestConfiguration {

    @Value("${relationship-test.backend-env-path:../../backend/.env}")
    private String backendEnvPath;

    @PostConstruct
    void loadBackendEnv() {
        Path path = Paths.get(backendEnvPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.warn("Backend .env file not found at {}", path);
            return;
        }

        try (Stream<String> lines = Files.lines(path)) {
            lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(this::applyEnvEntry);
        } catch (IOException ex) {
            log.warn("Failed to read backend .env file at {}", path, ex);
        }
    }

    private void applyEnvEntry(String entry) {
        int idx = entry.indexOf('=');
        if (idx <= 0) {
            return;
        }
        String key = entry.substring(0, idx).trim();
        String value = entry.substring(idx + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            return;
        }

        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
            log.debug("Loaded backend env var {}", key);
        }
    }

    @Bean
    RelationshipSchemaProvider testRelationshipSchemaProvider(EntityManagerFactory entityManagerFactory,
                                                              @Nullable AIEntityConfigurationLoader configurationLoader,
                                                              RelationshipQueryProperties properties,
                                                              EntityRelationshipMapper mapper) {
        return new RelationshipSchemaProvider(entityManagerFactory.createEntityManager(), configurationLoader, properties, mapper);
    }

    @Bean(name = "jpaRelationshipTraversalService")
    RelationshipTraversalService testJpaRelationshipTraversalService(EntityManagerFactory entityManagerFactory) {
        return new JpaRelationshipTraversalService(entityManagerFactory.createEntityManager());
    }

    @Bean(name = "metadataRelationshipTraversalService")
    RelationshipTraversalService testMetadataRelationshipTraversalService(AISearchableEntityRepository repository,
                                                                          ObjectMapper objectMapper) {
        return new MetadataRelationshipTraversalService(repository, objectMapper);
    }

    @Bean
    @Primary
    LLMDrivenJPAQueryService testLLMDrivenJPAQueryService(RelationshipQueryPlanner planner,
                                                          DynamicJPAQueryBuilder queryBuilder,
                                                          RelationshipQueryValidator validator,
                                                          RelationshipQueryProperties properties,
                                                          RelationshipModuleMetadata metadata,
                                                          @Qualifier("jpaRelationshipTraversalService") RelationshipTraversalService jpaTraversalService,
                                                          @Qualifier("metadataRelationshipTraversalService") RelationshipTraversalService metadataTraversalService,
                                                          AISearchableEntityRepository repository,
                                                          @Nullable VectorDatabaseService vectorDatabaseService,
                                                          @Nullable AIEmbeddingService embeddingService,
                                                          QueryCache queryCache,
                                                          QueryMetrics queryMetrics) {
        return new LLMDrivenJPAQueryService(
            planner,
            queryBuilder,
            validator,
            properties,
            metadata,
            jpaTraversalService,
            metadataTraversalService,
            repository,
            vectorDatabaseService,
            embeddingService,
            queryCache,
            queryMetrics
        );
    }

    @Bean
    @Primary
    ReliableRelationshipQueryService testReliableRelationshipQueryService(LLMDrivenJPAQueryService llmDrivenJPAQueryService,
                                                                          RelationshipQueryPlanner planner,
                                                                          @Qualifier("metadataRelationshipTraversalService") RelationshipTraversalService metadataTraversalService,
                                                                          @Nullable VectorDatabaseService vectorDatabaseService,
                                                                          @Nullable AIEmbeddingService embeddingService,
                                                                          AISearchableEntityRepository repository,
                                                                          RelationshipQueryValidator validator,
                                                                          RelationshipQueryProperties properties,
                                                                          RelationshipModuleMetadata metadata,
                                                                          QueryCache queryCache,
                                                                          QueryMetrics queryMetrics) {
        return new ReliableRelationshipQueryService(
            llmDrivenJPAQueryService,
            planner,
            metadataTraversalService,
            vectorDatabaseService,
            embeddingService,
            repository,
            validator,
            properties,
            metadata,
            queryCache,
            queryMetrics
        );
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer relationshipTestJacksonCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            builder.modulesToInstall(module);
        };
    }
}
