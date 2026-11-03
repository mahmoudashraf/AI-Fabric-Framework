package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;

class SupportIntegrationContextStoreTest {
    @Test void storesOnlyScopedResourceConsentAndRevokesOnResourceChanges() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var jdbc = new JdbcTemplate(data);
        // Keep one connection open because this fixture deliberately uses no persistent in-memory database.
        try (var ignored = data.getConnection()) {
            jdbc.execute("create table platform_customers(id varchar(64) primary key)");
            jdbc.execute("create table platform_users(id varchar(64) primary key)");
            jdbc.update("insert into platform_customers values ('customer-a')");
            jdbc.update("insert into platform_users values ('alice')");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V226__console_support_integration_context.sql")).execute(data);
            var store = new SupportIntegrationContextStore(jdbc, new ObjectMapper());
            var owner = new Requester("customer-a", "alice", "https://identity.example.test", "subject-a");
            var now = Instant.now();
            store.insert("space-a", "request-a", owner, "support-ticket:request-a", now);
            assertThat(store.find("space-b", "request-a", false)).isEmpty();
            assertThat(store.byPrincipal("space-b", "support-ticket:request-a", false)).isEmpty();
            var initial = store.find("space-a", "request-a", false).orElseThrow();
            assertThat(initial.requester()).isEqualTo(owner);
            assertThat(initial.consent().granted()).isFalse();
            store.resources(initial, List.of(new ResourceRef(ResourceType.DEPLOYMENT, "owned-deployment")), now);
            var selected = store.find("space-a", "request-a", false).orElseThrow();
            store.consent(selected, true, now);
            var consented = store.find("space-a", "request-a", false).orElseThrow();
            assertThat(consented.consent().granted()).isTrue();
            store.resources(consented, List.of(), now);
            var changed = store.byPrincipal("space-a", "support-ticket:request-a", false).orElseThrow();
            assertThat(changed.consent().granted()).isFalse();
            assertThat(changed.consent().version()).isEqualTo(3);
            assertThat(changed.revision()).isEqualTo(3);
            assertThatThrownBy(() -> store.consent(initial, true, now))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("SUPPORT_CONTEXT_REVISION_CONFLICT");
            assertThat(jdbc.queryForList("select * from platform_support_integration_context").getFirst())
                .doesNotContainKeys("SUMMARY", "DETAILS", "STATUS", "INITIAL_MESSAGE");
        } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
    }
}
