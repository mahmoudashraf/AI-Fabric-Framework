package com.ai.fabric.budget;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.UUID;

class JdbcProviderBudgetLedgerH2Test extends LedgerContract {
    @Override protected DataSource freshDataSource() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:budget_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000");
        return source;
    }
}
