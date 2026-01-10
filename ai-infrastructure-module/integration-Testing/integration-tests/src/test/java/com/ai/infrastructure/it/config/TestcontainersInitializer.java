package com.ai.infrastructure.it.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Initializer that enables Testcontainers when the 'testcontainers' profile is active.
 *
 * <p>This initializer checks if the {@code testcontainers} Spring profile is active
 * and, if so, sets the {@code testcontainers.enabled} property to {@code true}.
 * This property is then used by {@link VectorDatabaseContainerAutoConfiguration}
 * to conditionally enable container auto-configuration.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * @SpringBootTest
 * @ActiveProfiles("testcontainers")
 * class MyTest {
 *     // Testcontainers will be enabled automatically
 * }
 * </pre>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and thread-safe.</p>
 *
 * @author AI Infrastructure Team
 * @version 1.0.0
 * @see VectorDatabaseContainerAutoConfiguration
 */
public class TestcontainersInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROFILE_TESTCONTAINERS = "testcontainers";
    private static final String PROP_TESTCONTAINERS_ENABLED = "testcontainers.enabled";
    private static final String PROP_SOURCE_TESTCONTAINERS_ENABLED = "testcontainersEnabled";

    /**
     * Initializes the application context by enabling Testcontainers if the profile is active.
     *
     * @param context The application context to initialize
     */
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        boolean testcontainersActive = false;
        for (String profile : env.getActiveProfiles()) {
            if (PROFILE_TESTCONTAINERS.equals(profile)) {
                testcontainersActive = true;
                break;
            }
        }

        if (testcontainersActive) {
            Map<String, Object> props = new HashMap<>();
            props.put(PROP_TESTCONTAINERS_ENABLED, "true");
            env.getPropertySources().addFirst(
                new MapPropertySource(PROP_SOURCE_TESTCONTAINERS_ENABLED, props)
            );
        }
    }
}
