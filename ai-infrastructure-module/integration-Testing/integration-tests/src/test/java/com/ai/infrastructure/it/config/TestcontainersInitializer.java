package com.ai.infrastructure.it.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Initializer that enables Testcontainers when the 'testcontainers' profile is active
 * and a container-supported vector database type is specified.
 *
 * <p>This initializer checks if:
 * <ul>
 *   <li>The {@code testcontainers} Spring profile is active</li>
 *   <li>The {@code ai.vector-db.type} is set to a container-supported type
 *       (milvus, qdrant, weaviate, chroma, pgvector)</li>
 * </ul>
 * If both conditions are met, it sets {@code testcontainers.enabled=true}.
 * This property is then used by {@link VectorDatabaseContainerAutoConfiguration}
 * to conditionally enable container auto-configuration.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * // Unit tests - defaults to Lucene (fast, no containers)
 * mvn test
 *
 * // Use Testcontainers with Milvus
 * mvn test -Dspring.profiles.active=testcontainers -Dai.vector-db.type=milvus
 *
 * // Use Testcontainers with Qdrant
 * mvn test -Dspring.profiles.active=testcontainers -Dai.vector-db.type=qdrant
 * </pre>
 *
 * <p><strong>Supported Container Types:</strong> milvus, qdrant, weaviate, chroma, pgvector</p>
 *
 * <p><strong>Default Behavior:</strong> If no container type is specified or type is
 * lucene/memory, Testcontainers will not activate, and tests will use Lucene (fast).</p>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and thread-safe.</p>
 *
 * @author AI Infrastructure Team
 * @version 1.1.0
 * @see VectorDatabaseContainerAutoConfiguration
 */
public class TestcontainersInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROFILE_TESTCONTAINERS = "testcontainers";
    private static final String PROP_TESTCONTAINERS_ENABLED = "testcontainers.enabled";
    private static final String PROP_VECTOR_DB_TYPE = "ai.vector-db.type";
    private static final String PROP_SOURCE_TESTCONTAINERS_ENABLED = "testcontainersEnabled";

    // Container-supported vector database types
    private static final String TYPE_MILVUS = "milvus";
    private static final String TYPE_QDRANT = "qdrant";
    private static final String TYPE_WEAVIATE = "weaviate";
    private static final String TYPE_CHROMA = "chroma";
    private static final String TYPE_PGVECTOR = "pgvector";

    /**
     * Initializes the application context by enabling Testcontainers if:
     * 1. The testcontainers profile is active
     * 2. A container-supported vector database type is specified
     *
     * @param context The application context to initialize
     */
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        // Check if testcontainers profile is active
        boolean testcontainersActive = false;
        for (String profile : env.getActiveProfiles()) {
            if (PROFILE_TESTCONTAINERS.equals(profile)) {
                testcontainersActive = true;
                break;
            }
        }

        if (!testcontainersActive) {
            return; // Profile not active, don't enable Testcontainers
        }

        // Check if a container-supported vector database type is specified
        String vectorDbType = env.getProperty(PROP_VECTOR_DB_TYPE, String.class, "lucene");
        boolean isContainerType = isContainerSupportedType(vectorDbType);

        if (isContainerType) {
            Map<String, Object> props = new HashMap<>();
            props.put(PROP_TESTCONTAINERS_ENABLED, "true");
            env.getPropertySources().addFirst(
                new MapPropertySource(PROP_SOURCE_TESTCONTAINERS_ENABLED, props)
            );
        }
        // If not a container type (e.g., lucene, memory), Testcontainers stays disabled
        // Tests will use the specified type (Lucene by default)
    }

    /**
     * Checks if the specified vector database type is supported by Testcontainers.
     *
     * @param type Vector database type to check
     * @return true if the type is container-supported, false otherwise
     */
    private boolean isContainerSupportedType(String type) {
        if (type == null) {
            return false;
        }
        String normalizedType = type.toLowerCase().trim();
        return TYPE_MILVUS.equals(normalizedType)
            || TYPE_QDRANT.equals(normalizedType)
            || TYPE_WEAVIATE.equals(normalizedType)
            || TYPE_CHROMA.equals(normalizedType)
            || TYPE_PGVECTOR.equals(normalizedType);
    }
}
