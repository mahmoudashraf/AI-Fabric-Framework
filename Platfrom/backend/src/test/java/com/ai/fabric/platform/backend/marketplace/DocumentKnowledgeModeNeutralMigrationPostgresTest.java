package com.ai.fabric.platform.backend.marketplace;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DocumentKnowledgeModeNeutralMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void productionSchemaAndCurrentDeskGeneratedTemplateIdsSupportAdditiveDocumentRevisions() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("229")).load().migrate();
        try (var connection = POSTGRES.createConnection("")) {
            DocumentKnowledgeModeNeutralMigrationTest.seedApiProducts(connection);
            DocumentKnowledgeModeNeutralMigrationTest.verifyRevisions(connection);
        }
    }

    @Test
    void productionPublicOffersRetainTheirApprovalPolicyWhileReceivingNeutralDocumentPins() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("document_offers").defaultSchema("document_offers")
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("229")).load().migrate();
        try (var connection = POSTGRES.createConnection("")) {
            connection.setSchema("document_offers");
            DocumentKnowledgeModeNeutralMigrationTest.seedApiProducts(connection);
            DocumentBootstrapOfferRevisionMigrationTest.verifyPublicOfferRevisions(connection);
        }
        // Discover and execute the Java migrations through the production Flyway location too.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("document_offers").defaultSchema("document_offers")
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("232")).load().migrate();
    }
}
