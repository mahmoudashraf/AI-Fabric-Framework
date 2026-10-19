package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentAdmissionServiceTest extends AdmissionFixture {
    DeploymentRepository deployments;
    DeploymentVersionRepository versions;
    DeploymentTargetProfileRepository targets;
    DeploymentAccessService access;
    PlatformCustomerRepository customers;
    PlatformTenantRepository tenants;
    PlatformUserRepository users;
    DefaultListableBeanFactory adapters;
    DeploymentAdmissionService service;
    TargetRetirementGateService retirementGates;
    DeploymentEntity deployment;
    PlatformCustomerEntity customer;
    PlatformTenantEntity tenant;

    @BeforeEach
    void services() {
        deployments = mock(DeploymentRepository.class); versions = mock(DeploymentVersionRepository.class);
        targets = mock(DeploymentTargetProfileRepository.class); access = mock(DeploymentAccessService.class);
        customers = mock(PlatformCustomerRepository.class); tenants = mock(PlatformTenantRepository.class); users = mock(PlatformUserRepository.class);
        adapters = new DefaultListableBeanFactory();
        retirementGates = mock(TargetRetirementGateService.class);
        service = new DeploymentAdmissionService(store, json, mapper, deployments, versions, targets, access,
            new PlatformCustomerAccessService(users, customers), customers, tenants, adapters.getBeanProvider(AdmissionEvidenceSource.class),
            adapters.getBeanProvider(AdmissionResourceReadbackSource.class), adapters.getBeanProvider(AdmissionNoticeDeliverySource.class),
            retirementGates);
        deployment = new DeploymentEntity(); deployment.setId("deployment1"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        deployment.setStatus("ACTIVE"); when(deployments.findById("deployment1")).thenReturn(Optional.of(deployment));
        customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setCustomerId("customer"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        authenticate(PlatformRole.PLATFORM_ADMIN);
    }
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void unusedCancellationRequiresStaffBeforeAnyCommandOrEvidenceRead() {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service.cancelUnusedReservation("deployment1", TARGET, null)).hasMessageContaining("STAFF_REVIEW_REQUIRED");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.cancelUnusedReservation("deployment1", TARGET, null)).hasMessageContaining("ACTIVE_STAFF_REQUIRED");
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        assertThatThrownBy(() -> service.cancelUnusedReservation("deployment1", TARGET, null)).hasMessageContaining("UNUSED_CANCELLATION_COMMAND_REQUIRED");
        verifyNoInteractions(deployments, access);
    }

    @Test
    void anonymousActorAndMissingActiveOwnerFailClosedEvenWithoutMethodProxy() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.status("deployment1", "unknown")).hasMessageContaining("ACTIVE_ACTOR_REQUIRED");
        authenticate(PlatformRole.PLATFORM_ADMIN); customer.setStatus("DISABLED");
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("ACTIVE_CUSTOMER_REQUIRED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void foreignOrDisabledTenantCannotBeUsedByStaff() {
        tenant.setCustomerId("another-customer");
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("ACTIVE_OWNERSHIP_REQUIRED");
        tenant.setCustomerId("customer"); tenant.setStatus("DELETED");
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("ACTIVE_OWNERSHIP_REQUIRED");
    }
    @Test
    void customerNullScopeIsNotGlobalAuthorityAndCannotReviewCapacity() {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("active customer account");
        assertThatThrownBy(() -> service.approvePolicy(policy(2))).hasMessageContaining("STAFF_REVIEW_REQUIRED");
        verify(users).findByEmailIgnoreCase("actor@example.test");
    }
    @Test
    void existingAssignedOperatorAccessIsRequired() {
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_ASSIGNED"))
            .when(access).requireDeploymentOperatorAccess(deployment);
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("NOT_ASSIGNED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void absentRealEvidenceAdapterDeniesInsteadOfInventingReadiness() throws Exception {
        approveLiveInputs();
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("ADMISSION_EVIDENCE_ADAPTER_REQUIRED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void actualReadbackAdmissionHasNoAmountsInCustomerCommandAndReplayNeedsNoProviderCall() throws Exception {
        approveLiveInputs();
        AdmissionEvidenceSource source = mock(AdmissionEvidenceSource.class);
        when(source.read(any())).thenAnswer(invocation -> evidence(invocation.getArgument(0), Instant.now()));
        adapters.registerSingleton("readback", source);
        Reservation r = service.reserve("deployment1", new Reserve("op", "manifest1"));
        assertThat(r.demand()).isEqualTo(vector(1)); assertThat(r.state()).isEqualTo(State.RESERVED);
        assertThat(service.reserve("deployment1", new Reserve("op", "manifest1"))).isEqualTo(r);
        verify(source, times(1)).read(any());
        assertThatThrownBy(() -> service.reconcile("deployment1", r.fence(), true)).hasMessageContaining("RESOURCE_READBACK_ADAPTER_REQUIRED");
        assertThat(service.status("deployment1", r.id()).consumesCapacity()).isTrue();
    }
    @Test
    void customerReservationPersistsTheExactPreparedContextAndReviewedVersion() throws Exception {
        approveLiveInputs();
        AdmissionEvidenceSource source = mock(AdmissionEvidenceSource.class);
        when(source.read(any())).thenAnswer(invocation -> evidence(invocation.getArgument(0), Instant.now()));
        adapters.registerSingleton("readback", source);
        var limits = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits(
            "measurement", 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1);
        var topology = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology(
            1, 1, 1, 1, 0, 0, 0, 1);
        var customerContext = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", null, TARGET,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 1, "placement-proof",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY, "customer-op", limits, topology);

        Reservation reservation = service.reserveCustomer("deployment1", "version",
            new Reserve("customer-op", "manifest1", customerContext));

        assertThat(reservation.customerContextFingerprint()).isNotBlank();
        assertThat(reservation.customerOperation())
            .isEqualTo(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY);
        assertThatThrownBy(() -> service.reserveCustomer("deployment1", "another-version",
            new Reserve("another-op", "manifest1", customerContext))).hasMessageContaining("REVIEWED_VERSION_CHANGED");
    }

    @Test
    void customerReservationRejectsAContextWithoutTheReviewedTopology() throws Exception {
        approveLiveInputs();
        var limits = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits(
            "measurement", 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1);
        var incomplete = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", null, TARGET,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 1, "placement-proof",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY,
            "missing-topology", limits, null);

        assertThatThrownBy(() -> service.reserveCustomer("deployment1", "version",
            new Reserve("missing-topology", "manifest1", incomplete)))
            .hasMessageContaining("CUSTOMER_ADMISSION_SCOPE_INVALID");
    }
    @Test
    void targetConfigDriftAndPublishedVersionDriftDeny() throws Exception {
        approveLiveInputs();
        DeploymentTargetProfileEntity profile = targets.findById(TARGET).orElseThrow();
        profile.setProviderConfigJson("{\"serverUuid\":\"kvufjk78dj4wyhjgp1mlxecr\",\"destinationUuid\":\"r3thf2xmxcjn1tt2bclabebz\"}");
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("PHYSICAL_PLACEMENT_FORBIDDEN");
        versions.findById("version").orElseThrow().setConfigHash("new-config");
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("REVIEWED_VERSION_CHANGED");
    }
    @Test
    void ownershipChangedDuringSlowEvidenceReadCannotCommitReservation() throws Exception {
        approveLiveInputs();
        AdmissionEvidenceSource source = context -> { customer.setStatus("DISABLED"); return evidence(context, Instant.now()); };
        adapters.registerSingleton("readback", source);
        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1"))).hasMessageContaining("ACTIVE_CUSTOMER_REQUIRED");
        assertThat(store.held()).isEmpty();
    }
    @Test
    void retirementPlannedBeforeTheFinalAdmissionGuardCannotCommitReservation() throws Exception {
        approveLiveInputs();
        AdmissionEvidenceSource source = mock(AdmissionEvidenceSource.class);
        when(source.read(any())).thenAnswer(invocation -> evidence(invocation.getArgument(0), Instant.now()));
        adapters.registerSingleton("readback", source);
        doThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "TARGET_RETIREMENT_FENCED"))
            .when(retirementGates).requireAvailableForMutation("deployment1", TARGET);

        assertThatThrownBy(() -> service.reserve("deployment1", new Reserve("op", "manifest1")))
            .hasMessageContaining("TARGET_RETIREMENT_FENCED");
        assertThat(store.held()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"version","profile","profile-lock"})
    void renewalClearsRealJpaCacheBeforeFinalPublishedVersionAndProfileChecks(String changed) throws Exception {
        approveLiveInputs();
        var source = mock(AdmissionEvidenceSource.class);
        when(source.read(any())).thenAnswer(invocation -> evidence(invocation.getArgument(0), Instant.now()));
        adapters.registerSingleton("readback", source);
        var original = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", null, TARGET,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 1, "P1",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY, "apply",
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedLimits(
                "measurement", 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1),
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 0, 0, 0, 1));
        var reservation = service.reserveCustomer("deployment1", "version", new Reserve("apply", "manifest1", original));
        var fresh = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            original.customerId(), original.tenantId(), original.deploymentId(), null, original.targetProfileId(), original.intent(),
            original.offerId(), original.offerFingerprint(), original.entitlementRevision(), "P2", original.operation(),
            original.operationKey(), original.limits(), original.topology());
        var profile = targets.findById(TARGET).orElseThrow();
        profile.setName("Synthetic reviewed profile"); profile.setCreatedAt(Instant.now()); profile.setUpdatedAt(Instant.now());
        var version = versions.findById("version").orElseThrow();
        version.setSourceDraftId("draft"); version.setVersionLabel("v1"); version.setPublishedAt(Instant.now());
        version.setActionsConfigJson("{}"); version.setEntityConfigJson("{}"); version.setRoutingConfigJson("{}");
        version.setProviderConfigJson("{}"); version.setSecurityConfigJson("{}"); version.setPromptConfigJson("{}");
        version.setActionsArtifactYaml("{}"); version.setEntityArtifactYaml("{}"); version.setRoutingArtifactYaml("{}"); version.setManifestJson("{}");

        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(DeploymentTargetProfileEntity.class.getName(), DeploymentVersionEntity.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        var emf = factory.getObject(); var entities = emf.createEntityManager();
        try (var competing = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            entities.getTransaction().begin(); entities.persist(profile); entities.persist(version); entities.getTransaction().commit();
            // Keep these managed, exactly as an OpenEntityManagerInView request can across its preparation transaction.
            assertThat(entities.contains(profile)).isTrue(); assertThat(entities.contains(version)).isTrue();
            TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(entities));
            when(targets.findById(TARGET)).thenAnswer(invocation -> Optional.ofNullable(entities.find(DeploymentTargetProfileEntity.class, TARGET)));
            when(versions.findById("version")).thenAnswer(invocation -> Optional.ofNullable(entities.find(DeploymentVersionEntity.class, "version")));
            var authorization = mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService.class);
            var profileWriter = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
            when(authorization.prepareAdmission(eq("customer"), any())).thenAnswer(invocation -> {
                if (changed.equals("profile-lock")) {
                    assertThat(entities.find(DeploymentTargetProfileEntity.class, TARGET).isActive()).isTrue();
                    var started = new java.util.concurrent.CountDownLatch(1);
                    profileWriter.set(competing.submit(() -> {
                        started.countDown();
                        return jdbc.update("UPDATE deployment_target_profiles SET active=false WHERE id=?", TARGET);
                    }));
                    assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> profileWriter.get().get(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                }
                return new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization(fresh, null);
            });
            var placement = mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService.class);
            var observed = new java.util.concurrent.atomic.AtomicReference<Instant>();
            String fingerprint = store.context("customer", "tenant", "deployment1", "manifest1").target().profileFingerprint();
            when(placement.requireEvidenceById(any(), eq(fresh.intent()), eq("P2"))).thenAnswer(invocation ->
                new com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity("P2", TARGET, fresh.intent(),
                    fingerprint, STAGING.serverUuid(), STAGING.ip(), STAGING.destinationUuid(), observed.get(), observed.get().plusSeconds(60)));
            var authorizer = new InitialAdmissionRenewalAuthorizer(authorization, placement, targets);
            ReflectionTestUtils.setField(authorizer, "entityManager", entities);
            ReflectionTestUtils.setField(service, "initialRenewalAuthorizer", authorizer);
            var ledger = spy(store);
            doReturn("synthetic-provider-grant-fingerprint").when(ledger).initialProviderGrantsFingerprint(any());
            var transactions = new JpaTransactionManager(emf); transactions.setDataSource(dataSource);
            // Isolate the real service/authorizer/cache ordering; ledger commit and NOWAIT are covered separately.
            doAnswer(invocation -> new TransactionTemplate(transactions).execute(tx -> {
                invocation.<Runnable>getArgument(7).run(); return reservation;
            })).when(ledger).renewInitialCustomerEvidence(any(), any(), any(), any(), anyString(), anyString(), any(), any());
            ReflectionTestUtils.setField(service, "store", ledger);
            doAnswer(invocation -> {
                if (changed.equals("version")) {
                    jdbc.update("UPDATE platform_deployment_versions SET status='DRAFT' WHERE id='version'");
                    assertThat(entities.find(DeploymentVersionEntity.class, "version").getStatus()).isEqualTo("PUBLISHED");
                } else if (changed.equals("profile")) {
                    jdbc.update("UPDATE deployment_target_profiles SET network_policy_json='{\"changed\":true}' WHERE id=?", TARGET);
                    assertThat(entities.find(DeploymentTargetProfileEntity.class, TARGET).getNetworkPolicyJson()).isEqualTo("{}");
                }
                observed.set(Instant.now()); return evidence(invocation.getArgument(0), observed.get());
            }).when(source).read(any());
            if (changed.equals("profile-lock")) {
                assertThat(service.refreshCustomerEvidence("deployment1", "version", reservation.id(), 0,
                    new Reserve("apply", "manifest1", fresh))).isEqualTo(reservation);
                assertThat(profileWriter.get().get(3, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT active FROM deployment_target_profiles WHERE id=?", Boolean.class, TARGET)).isFalse();
            } else {
                assertThatThrownBy(() -> service.refreshCustomerEvidence("deployment1", "version", reservation.id(), 0,
                    new Reserve("apply", "manifest1", fresh)))
                    .hasMessageContaining(changed.equals("version") ? "REVIEWED_VERSION_CHANGED" : "TARGET_PROFILE_CHANGED");
            }
            assertThat(store.require(reservation.id())).isEqualTo(reservation);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF'", Long.class)).isZero();
        } finally {
            if (TransactionSynchronizationManager.hasResource(emf)) TransactionSynchronizationManager.unbindResource(emf);
            entities.close(); factory.destroy();
        }
    }
    @Test
    void statusCannotReadGuessedReservationFromAnotherDeployment() {
        Reservation r = one(); deployment.setTenantId("foreign");
        PlatformTenantEntity foreign = new PlatformTenantEntity(); foreign.setId("foreign"); foreign.setCustomerId("customer"); foreign.setStatus("ACTIVE");
        when(tenants.findById("foreign")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.status("deployment1", r.id())).hasMessageContaining("RESERVATION_NOT_FOUND");
    }
    private void approveLiveInputs() throws Exception {
        Instant now = Instant.now();
        for (String table : List.of("platform_deployments", "platform_customers", "platform_customer_tenants", "platform_deployment_versions", "deployment_target_profiles"))
            jdbc.execute("CREATE TABLE " + table + " (id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment1')"); jdbc.update("INSERT INTO platform_customers VALUES ('customer')");
        jdbc.update("INSERT INTO platform_customer_tenants VALUES ('tenant')"); jdbc.update("INSERT INTO platform_deployment_versions VALUES ('version')");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES (?)", TARGET);
        DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity(); target.setId(TARGET); target.setActive(true);
        target.setProviderType(DeploymentProviderType.COOLIFY); target.setEnvironmentName("customer-staging"); target.setSourceStrategy("IMMUTABLE_IMAGE");
        target.setProviderConfigJson(mapper.writeValueAsString(Map.of("serverUuid", STAGING.serverUuid(), "destinationUuid", STAGING.destinationUuid())));
        target.setNetworkPolicyJson("{}"); target.setResourceDefaultsJson("{}"); when(targets.findById(TARGET)).thenReturn(Optional.of(target));
        String fingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, target);
        var p = policy(2); Budget live = new Budget(vector(2), vector(0), vector(0), "measurement", now.minusSeconds(1), now.plusSeconds(10000));
        service.approvePolicy(new FleetPolicy(p.id(), live, Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, fingerprint, live)),
            p.lifecycle(), p.restoreEvidenceReference(), p.privacyEvidenceReference(), p.supportReference()));
        var g = grant(2); service.approveCustomerGrant(new CustomerGrant(g.customerId(), g.fleetLimit(), g.targetLimits(), now.plusSeconds(5000),
            g.ownerContactEvidenceReference(), g.payerReference(), g.keepStagingEntitlementReference()));
        DeploymentVersionEntity version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment1");
        version.setConfigHash("config-hash"); version.setStatus("PUBLISHED"); when(versions.findById("version")).thenReturn(Optional.of(version));
        service.approveManifest(manifest("manifest1", "deployment1"));
    }
    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("actor@example.test", role, "Actor", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
