package com.ai.infrastructure.it.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Logger log = LoggerFactory.getLogger(TestcontainersInitializer.class);

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

    // Container constants (shared with VectorDatabaseContainerAutoConfiguration)
    private static final String DEFAULT_IMAGE_MILVUS = "milvusdb/milvus:v2.4.1-latest";
    private static final int PORT_MILVUS = 19530;
    private static final Duration EXTENDED_STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final String DEFAULT_DATABASE_MILVUS = "default";

    // Shared container storage (accessed by both initializer and bean methods)
    private static final Map<String, GenericContainer<?>> earlyStartedContainers = new ConcurrentHashMap<>();

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

        log.debug("TestcontainersInitializer: Active profiles: {}", java.util.Arrays.toString(env.getActiveProfiles()));
        
        // Check if a container-supported vector database type is specified
        String vectorDbType = env.getProperty(PROP_VECTOR_DB_TYPE, String.class, "lucene");
        log.info("TestcontainersInitializer: Vector DB type: {}", vectorDbType);
        boolean isContainerType = isContainerSupportedType(vectorDbType);

        // Start containers if: (1) testcontainers profile is active, OR (2) a container type is explicitly specified
        // This allows tests to use containers even if they have @ActiveProfiles that don't include testcontainers
        if (isContainerType && (testcontainersActive || isExplicitlySpecified(env, vectorDbType))) {
            log.info("TestcontainersInitializer: Container type detected, starting container early...");
            Map<String, Object> props = new HashMap<>();
            props.put(PROP_TESTCONTAINERS_ENABLED, true);
            
            // Start container early and inject connection properties BEFORE any beans are created
            // This ensures services can connect immediately when they're instantiated
            startContainerEarly(vectorDbType, env, props);
            
            // Use addFirst to ensure highest priority - these properties will override YAML config
            env.getPropertySources().addFirst(
                new MapPropertySource(PROP_SOURCE_TESTCONTAINERS_ENABLED, props)
            );
            
            log.info("Testcontainers enabled for vector DB type: {}. Container started early.", vectorDbType);
        }
        // If not a container type (e.g., lucene, memory), Testcontainers stays disabled
        // Tests will use the specified type (Lucene by default)
    }

    /**
     * Starts the container early (before Spring beans are created) and injects connection properties.
     * This ensures services can connect immediately when instantiated.
     */
    private void startContainerEarly(String vectorDbType, ConfigurableEnvironment env, Map<String, Object> props) {
        String normalizedType = vectorDbType.toLowerCase().trim();
        
        if (TYPE_MILVUS.equals(normalizedType)) {
            startMilvusContainerEarly(env, props);
        }
        // Add other container types as needed
    }

    /**
     * Starts Milvus container early and injects connection properties.
     */
    private void startMilvusContainerEarly(ConfigurableEnvironment env, Map<String, Object> props) {
        if (earlyStartedContainers.containsKey(TYPE_MILVUS)) {
            GenericContainer<?> existing = earlyStartedContainers.get(TYPE_MILVUS);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing Milvus container started early");
                injectMilvusProperties(existing, props);
                return;
            }
        }

        log.info("Starting Milvus container early (before Spring beans are created)...");
        try {
            // Wait a bit for Docker to be fully ready (especially on Windows)
            // This helps avoid connection issues when Docker Desktop is still initializing
            try {
                Thread.sleep(1000); // 1 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse(DEFAULT_IMAGE_MILVUS)
            )
                .withExposedPorts(PORT_MILVUS)
                .withStartupTimeout(EXTENDED_STARTUP_TIMEOUT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(EXTENDED_STARTUP_TIMEOUT));

            log.info("Starting Milvus container (this may take a few minutes on first run)...");
            container.start();
            log.info("Milvus container started successfully");
            
            earlyStartedContainers.put(TYPE_MILVUS, container);
            
            injectMilvusProperties(container, props);
            
            log.info("Milvus container started early at {}:{}. Properties injected: {}", 
                container.getHost(), container.getMappedPort(PORT_MILVUS), props.keySet());
        } catch (Exception e) {
            log.error("Failed to start Milvus container early", e);
            
            // Check if this is a Docker connectivity issue (common on Windows with Docker Desktop 29.x)
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Could not find a valid Docker environment")) {
                log.error("""
                    
                    ========================================================================
                    DOCKER CONNECTIVITY ISSUE DETECTED
                    ========================================================================
                    Testcontainers cannot connect to Docker Desktop. This is a known compatibility
                    issue between Testcontainers 1.19.3 and Docker Desktop 29.1.3 on Windows.
                    
                    SOLUTIONS:
                    1. Use Lucene (no Docker required):
                       mvn test "-Dai.vector-db.type=lucene"
                    
                    2. Update Testcontainers to a newer version (if available)
                    
                    3. Try restarting Docker Desktop and wait until it's fully started
                    
                    4. Check Docker Desktop settings:
                       - Ensure "Use the WSL 2 based engine" is enabled
                       - Try disabling and re-enabling Docker Desktop
                    ========================================================================
                    """);
            }
            
            throw new IllegalStateException("Failed to start Milvus container early: " + e.getMessage() + 
                ". If you see Docker connectivity issues, try using Lucene instead: -Dai.vector-db.type=lucene", e);
        }
    }

    /**
     * Injects Milvus connection properties into the properties map.
     */
    private void injectMilvusProperties(GenericContainer<?> container, Map<String, Object> props) {
        props.put("ai.providers.milvus.host", container.getHost());
        props.put("ai.providers.milvus.port", container.getMappedPort(PORT_MILVUS));
        props.put("ai.providers.milvus.database-name", DEFAULT_DATABASE_MILVUS);
        props.put("ai.providers.milvus.username", "");
        props.put("ai.providers.milvus.password", "");
        props.put("ai.providers.milvus.secure", false);
        props.put("ai.providers.milvus.enabled", true);
    }

    /**
     * Gets an early-started container (used by bean methods to reuse containers).
     */
    public static GenericContainer<?> getEarlyStartedContainer(String type) {
        return earlyStartedContainers.get(type);
    }

    /**
     * Checks if a container type was explicitly specified (via system property or environment variable).
     * This allows containers to start even if the testcontainers profile isn't active.
     */
    private boolean isExplicitlySpecified(ConfigurableEnvironment env, String vectorDbType) {
        // Check if it was set via system property (not from YAML default)
        String systemProp = System.getProperty(PROP_VECTOR_DB_TYPE);
        if (systemProp != null && systemProp.equalsIgnoreCase(vectorDbType)) {
            return true;
        }
        // Check if it was set via environment variable
        String envVar = System.getenv("VECTOR_DB_TYPE");
        if (envVar != null && envVar.equalsIgnoreCase(vectorDbType)) {
            return true;
        }
        return false;
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
