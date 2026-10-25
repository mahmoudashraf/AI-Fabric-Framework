package com.loomai.operations.lifecycle;

import com.loomai.operations.OperationsTestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY
})
@Import({OperationsTestConfiguration.class, LifecycleEngineTests.GatewayConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class LifecycleEnginePostgresTest extends LifecycleEngineTests {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
}
