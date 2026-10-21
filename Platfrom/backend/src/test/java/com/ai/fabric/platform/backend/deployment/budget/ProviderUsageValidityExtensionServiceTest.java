package com.ai.fabric.platform.backend.deployment.budget;

import javax.sql.DataSource;
import java.util.UUID;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProviderUsageValidityExtensionServiceTest extends ProviderUsageValidityExtensionContract {
    @Override DataSource freshDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:validity_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000", "sa", "");
    }
}
