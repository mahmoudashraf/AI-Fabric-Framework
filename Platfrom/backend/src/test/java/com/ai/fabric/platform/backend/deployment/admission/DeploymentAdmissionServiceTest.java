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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
