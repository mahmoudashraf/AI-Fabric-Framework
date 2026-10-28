package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:public-template-enrollment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class CustomerPublicEnrollmentIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired CustomerOfferEntitlementService service;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository grants;
    @Autowired CustomerEntitlementCommandRepository commands;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired CustomerOfferCodec codec;
    @MockBean CustomerReviewedCompositionService composition;
    private String customerId, offerId, email;

    @BeforeEach void setup() {
        // Execute the actual new production constraint migration, not an ORM-created approximation.
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V217__public_customer_template_enrollment.sql"))
            .execute(dataSource);
        String suffix = UUID.randomUUID().toString();
        customerId = "customer-" + suffix; offerId = "offer-" + suffix; email = suffix + "@example.test";
        Instant now = Instant.now();
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setName("Public customer");
        customer.setSlug(suffix); customer.setStatus("ACTIVE"); customer.setCreatedAt(now); customer.setUpdatedAt(now);
        customers.saveAndFlush(customer);
        var owner = new PlatformUserEntity(); owner.setId("owner-" + suffix); owner.setEmail(email); owner.setDisplayName("Owner");
        owner.setRole("CUSTOMER_ADMIN"); owner.setCustomerId(customerId); owner.setStatus("ACTIVE");
        owner.setPasswordHash("not-a-usable-password"); owner.setCreatedAt(now); owner.setUpdatedAt(now); users.saveAndFlush(owner);
        var definition = CustomerOfferTestFixtures.definition();
        offers.saveAndFlush(new CustomerOfferEntity(offerId, codec.write(definition), codec.hash(definition), "public-review", "staff", now));
        CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void nonEmptyEnrollmentPersistsOneReceiptAndCannotRestoreRevokedAccess() {
        service.enrollPublicTemplates(List.of(offerId));
        service.enrollPublicTemplates(List.of(offerId));
        var grant = grants.findByCustomerIdAndOfferId(customerId, offerId).orElseThrow();
        assertThat(grant.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(grant.getExpiresAt()).isAfter(Instant.parse("2100-01-01T00:00:00Z"));
        var receipts = commands.findAll().stream().filter(value -> grant.getId().equals(value.getEntitlementId())).toList();
        assertThat(receipts).hasSize(1);
        assertThat(receipts.getFirst().getCommandType()).isEqualTo("ENROLL");
        grant.setStatus(EntitlementStatus.REVOKED); grants.saveAndFlush(grant);
        service.enrollPublicTemplates(List.of(offerId));
        assertThat(grants.findById(grant.getId()).orElseThrow().getStatus()).isEqualTo(EntitlementStatus.REVOKED);
    }

    @Test void concurrentEnrollmentUsesTheExistingOfferLockAndCreatesOneGrant() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> action = () -> {
                CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
                try { service.enrollPublicTemplates(List.of(offerId)); return null; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var first = pool.submit(action); var second = pool.submit(action);
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
            var grant = grants.findByCustomerIdAndOfferId(customerId, offerId).orElseThrow();
            assertThat(commands.findAll().stream().filter(value -> grant.getId().equals(value.getEntitlementId()))).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
}
