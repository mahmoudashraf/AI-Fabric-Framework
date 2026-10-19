package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.UUID;

class ProviderUsageAllowanceServiceTest extends ProviderUsageGrantVoidUnusedContract {
    @Override DataSource freshDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:allowance_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000", "sa", "");
    }
}
