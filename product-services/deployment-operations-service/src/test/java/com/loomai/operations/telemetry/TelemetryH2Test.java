package com.loomai.operations.telemetry;

import com.loomai.operations.OperationsTestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "DEPLOYMENT_OPERATIONS_API_KEY=" + OperationsTestConfiguration.API_KEY,
    "TELEMETRY_ENABLED=true", "TELEMETRY_READ_KEY=" + TelemetryFixtures.READ_KEY,
    "TELEMETRY_SOURCES_JSON=" + TelemetryFixtures.SOURCES,
    "spring.datasource.url=jdbc:h2:mem:telemetry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@AutoConfigureMockMvc
@Import(OperationsTestConfiguration.class)
class TelemetryH2Test extends AbstractTelemetryTest {}
