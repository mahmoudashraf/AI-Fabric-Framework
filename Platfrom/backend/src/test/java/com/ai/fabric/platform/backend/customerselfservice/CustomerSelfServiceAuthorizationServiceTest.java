package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.conflict;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerSelfServiceAuthorizationServiceTest {
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerOfferEntitlementRepository grants = mock(CustomerOfferEntitlementRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentAssignmentRepository assignments = mock(DeploymentAssignmentRepository.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final CustomerPrerequisiteEvidenceProvider provider = mock(CustomerPrerequisiteEvidenceProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CustomerPrerequisiteEvidenceProvider> providers = mock(ObjectProvider.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private CustomerSelfServiceAuthorizationService service;
    private CustomerOfferEntitlementService entitlements;
    private Definition definition;
    private CustomerOfferEntity offer;
    private CustomerOfferEntitlementEntity grant;
    private PlatformUserEntity owner;
    private PlatformCustomerEntity customer;
    private PlatformTenantEntity tenant;
    private DeploymentEntity deployment;

    @BeforeEach
    void setup() {
        definition = CustomerOfferTestFixtures.definition();
        offer = new CustomerOfferEntity("offer", codec.write(definition), codec.hash(definition), "review", "admin", Instant.now());
        owner = new PlatformUserEntity();
        owner.setId("owner"); owner.setEmail("owner@test.invalid"); owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE"); owner.setCustomerId("customer");
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setCustomerId("customer"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        grant = new CustomerOfferEntitlementEntity();
        grant.setId("grant"); grant.setCustomerId("customer"); grant.setOfferId("offer"); grant.setOwnerUserId("owner");
        grant.setStatus(EntitlementStatus.ACTIVE); grant.setRevision(2); grant.setExpiresAt(Instant.now().plusSeconds(3600));
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.of(grant));
        when(grants.findByCustomerIdAndOfferId("customer", "offer")).thenReturn(Optional.of(grant));
        when(grants.findByCustomerIdOrderByCreatedAtAsc("customer")).thenReturn(List.of(grant));
        when(offers.findById("offer")).thenReturn(Optional.of(offer));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant"); deployment.setTemplateId("reviewed-template"); deployment.setStatus("ACTIVE");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity("deployment",
            "customer", "tenant", "offer", "staging-target", offer.getFingerprint(), 2, "bootstrap", Instant.now())));
        when(profiles.findById("staging-target")).thenReturn(Optional.of(CustomerOfferTestFixtures.profile("staging-target", "stage-server")));
        when(profiles.findById("production-target")).thenReturn(Optional.of(CustomerOfferTestFixtures.profile("production-target", "prod-server")));
        var reviewedVersion = new DeploymentVersionEntity();
        reviewedVersion.setId("version"); reviewedVersion.setDeploymentId("deployment"); reviewedVersion.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(reviewedVersion));
        when(placement.requireFreshEvidence(any(), any())).thenReturn(new CustomerPlacementEvidenceEntity("placement-proof", "staging-target",
            TargetIntent.CUSTOMER_STAGING, "staging-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(60)));
        var customerAccess = new PlatformCustomerAccessService(users, customers);
        entitlements = new CustomerOfferEntitlementService(offers, grants, mock(CustomerEntitlementCommandRepository.class),
            customerAccess, customers, users, codec, composition, placement, profiles, mock(PlatformAuditService.class));
        var access = new DeploymentAccessService(assignments, mock(PublicApiDeploymentRepository.class), users, customerAccess);
        service = new CustomerSelfServiceAuthorizationService(entitlements, composition, placement, deployments, releases, versions, profiles,
            tenants, consumers, access, providers, new CustomerSelfServiceProperties(null, 60), associations);
        when(providers.orderedStream()).thenAnswer(call -> Stream.of(provider));
        when(provider.resolve(any())).thenAnswer(call -> evidence(call.getArgument(0), EnumSet.allOf(Prerequisite.class)));
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void activeOwnerCanApplyOnlyTheReviewedOwnedComposition() {
        var authorization = service.authorize("customer", request(Operation.APPLY));
        assertThat(authorization.context().entitlementRevision()).isEqualTo(2);
        service.requirePinnedComposition(authorization, "template-v1", Set.of(), Set.of("runtime-artifact"));
        verify(placement).requireFreshEvidence(any(), eq(TargetIntent.CUSTOMER_STAGING));
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void authorityCandidatePreflightRechecksOfferTargetEntitlementAndPlacementWithoutAdmissionEvidence() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);

        var approved = boundary.prepareAdmission(
            deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight"
        );

        assertThat(approved.context().operation()).isEqualTo(Operation.APPLY);
        assertThat(approved.context().targetProfileId()).isEqualTo("staging-target");
        assertThat(approved.context().offerId()).isEqualTo("offer");
        verify(placement).requireFreshEvidence(any(), eq(TargetIntent.CUSTOMER_STAGING));
        verify(provider, never()).resolve(any());

        assertCode(UNENTITLED_TARGET, () -> boundary.prepareAdmission(
            deployment, "other-target", Operation.APPLY, "runtime-authority-preflight"
        ));
        doThrow(conflict(REVIEWED_COMPOSITION_CHANGED)).when(composition).requireProfile(any(), any());
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> boundary.prepareAdmission(
            deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight"
        ));
        reset(composition);
        grant.setStatus(EntitlementStatus.REVOKED);
        assertCode(ENTITLEMENT_REVOKED, () -> boundary.prepareAdmission(
            deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight"
        ));
    }

    @Test void owningBoundaryRejectsOutOfOfferTargetsArtifactsAndActualSettings() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.require(deployment, "staging-target", null, Operation.APPLY, "apply-key", Map.of(), null, null, null);
        boundary.requireArtifact(approved, "runtime-artifact");
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> boundary.requireArtifact(approved, "foreign-artifact"));
        assertCode(UNENTITLED_TARGET, () -> boundary.require(deployment, "other-target", null, Operation.APPLY,
            "apply-key", Map.of(), null, null, null));
        var value = com.fasterxml.jackson.databind.node.TextNode.valueOf("unreviewed-auth");
        assertCode(SETTING_FORBIDDEN, () -> boundary.require(deployment, "staging-target", null, Operation.AUTH_CONFIGURE,
            "auth-key", Map.of(Setting.AUTH_PROFILE_ID, value), null, null, null));
    }

    @Test void genericApplyDelegatesExactVersionValidationToTheReviewedCompositionBoundary() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.require(deployment, "staging-target", null, Operation.APPLY, "apply-key", Map.of(), null, null, null);
        var version = new DeploymentVersionEntity();
        version.setDeploymentId(deployment.getId());
        version.setStatus("PUBLISHED");
        boundary.requireVersion(approved, version);
        verify(composition).requireVersion(definition, version);

        version.setDeploymentId("foreign-deployment");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(approved, version));
    }

    @Test void owningBoundaryCannotSubstituteCustomerOwnershipForAnAcceptedOffer() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertCode(ENTITLEMENT_REQUIRED, () -> boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            "app-key", Map.of(), null, null, null));
        CustomerOfferTestFixtures.authenticate("platform-admin", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        assertThat(boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            null, Map.of(), null, null, null)).isNull();
    }

    @Test void owningBoundaryRechecksRevokedEntitlementsAndExcludedOperationsForStaffAndCustomers() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        grant.setStatus(EntitlementStatus.REVOKED);
        assertCode(ENTITLEMENT_REVOKED, () -> boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            "app-key", Map.of(), null, null, null));
        grant.setStatus(EntitlementStatus.ACTIVE);
        var limited = new Definition(definition.schemaVersion(), definition.name(), definition.templateId(), definition.template(),
            definition.plugins(), definition.artifacts(), definition.targets(), Set.of(Operation.READ), definition.settings(),
            definition.bindingSlots(), definition.documentAudiences(), definition.admissionPrerequisites(), definition.limits(), definition.topology(),
            definition.approvedPolicyRef(), definition.operatingPolicyRef(), definition.identityPolicyRef(), definition.storagePolicyRef());
        offer = new CustomerOfferEntity("offer", codec.write(limited), codec.hash(limited), "review", "admin", Instant.now());
        when(offers.findById("offer")).thenReturn(Optional.of(offer));
        for (var role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_ADMIN)) {
            CustomerOfferTestFixtures.authenticate(role == PlatformRole.CUSTOMER_ADMIN ? owner.getEmail() : "platform-admin", role, "SESSION");
            assertCode(OPERATION_FORBIDDEN, () -> boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
                "app-key", Map.of(), null, null, null));
        }
    }

    @Test void consumerBoundaryRequiresExactOwnedBoundTargetAndAllowsConfigurationBeforeRelease() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var consumer = new PlatformConsumerEntity();
        consumer.setId("consumer"); consumer.setCustomerId("customer"); consumer.setStatus("ACTIVE");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of()));
        consumer.setBoundDeploymentId("deployment"); consumer.setBoundTargetProfileId("staging-target");
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of(Setting.APP_TITLE,
            com.fasterxml.jackson.databind.node.TextNode.valueOf("Approved app")));
        consumer.setBoundReleaseId("release");
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("staging-target"); release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of(Setting.APP_TITLE,
            com.fasterxml.jackson.databind.node.TextNode.valueOf("Approved app")));
        release.setTargetProfileId("production-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of()));
        assertThat(boundary.profileId("workspace", Map.of("a", 1, "b", 2)))
            .isEqualTo(boundary.profileId("workspace", Map.of("b", 2, "a", 1)))
            .isNotEqualTo(boundary.profileId("workspace", Map.of("a", 2, "b", 2)));
    }

    @Test void reviewedS3PermissionCannotAuthorizeAMountedSourceOrAnUnrelatedSlot() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.require(deployment, "staging-target", null, Operation.BINDING_REPLACE, "bind",
            Map.of(), "document-storage", "reviewed-s3", null);
        boundary.requireS3(approved, "document-storage", "S3_COMPATIBLE_OBJECT_STORAGE");
        assertCode(BINDING_SLOT_FORBIDDEN, () -> boundary.requireS3(approved, "document-storage", "MOUNTED_FOLDER"));
        assertCode(BINDING_SLOT_FORBIDDEN, () -> boundary.requireS3(approved, "foreign-slot", "S3_COMPATIBLE_OBJECT_STORAGE"));
    }

    @Test void reviewedMetadataResolvesAudienceAndSoleOwnedBindingWithoutCallerSuppliedScope() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);

        assertThat(boundary.reviewedDocumentAudiences(deployment)).containsExactly(DocumentAudience.PUBLIC);
        var approved = boundary.requireBindingKind(deployment, "staging-target", null,
            Operation.BINDING_REVOKE, "binding-key", BindingKind.CUSTOMER_S3);

        assertThat(approved.context().operation()).isEqualTo(Operation.BINDING_REVOKE);
        assertThat(approved.definition().bindingSlots()).singleElement()
            .extracting(BindingSlot::id).isEqualTo("document-storage");
    }

    @Test void missingAuthenticationNeverBecomesGlobalStaff() {
        clear(); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
    }

    @Test void missingDisabledRoleChangedOrDeletedOwnerScopeDenies() {
        owner.setCustomerId(null); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
        owner.setCustomerId("customer"); owner.setStatus("DISABLED"); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
        owner.setStatus("ACTIVE"); owner.setRole("PLATFORM_ADMIN"); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.empty());
        assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
    }

    @Test void disabledDeletedAndForeignCustomerAndTenantDeny() {
        customer.setStatus("DISABLED"); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
        customer.setStatus("ACTIVE"); tenant.setStatus("DISABLED"); assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        tenant.setStatus("ACTIVE"); tenant.setCustomerId("other"); assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        tenant.setCustomerId("customer"); when(tenants.findById("tenant")).thenReturn(Optional.empty());
        assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        when(customers.findById("customer")).thenReturn(Optional.empty()); assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
    }

    @Test void customerApiKeysAndUnrelatedIdentitiesCannotEnterPortal() {
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "API_KEY");
        assertDenied(ACTIVE_CUSTOMER_SCOPE_REQUIRED, Operation.APPLY);
        CustomerOfferTestFixtures.authenticate("public", PlatformRole.PUBLIC_API_CLIENT, "API_KEY");
        assertDenied(OPERATION_FORBIDDEN, Operation.APPLY);
    }

    @Test void foreignDeploymentAndMissingDurableAssociationDeny() {
        deployment.setCustomerId("other"); assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        deployment.setCustomerId("customer"); when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertDenied(ENTITLEMENT_REQUIRED, Operation.APPLY);
    }

    @Test void missingInvitedExpiredRevokedAndForeignOwnerGrantsDeny() {
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.empty()); assertDenied(ENTITLEMENT_REQUIRED, Operation.APPLY);
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.of(grant));
        grant.setStatus(EntitlementStatus.INVITED); assertDenied(ENTITLEMENT_REQUIRED, Operation.APPLY);
        grant.setStatus(EntitlementStatus.REVOKED); assertDenied(ENTITLEMENT_REVOKED, Operation.APPLY);
        grant.setStatus(EntitlementStatus.ACTIVE); grant.setExpiresAt(Instant.now().minusSeconds(1)); assertDenied(ENTITLEMENT_EXPIRED, Operation.APPLY);
        grant.setExpiresAt(Instant.now().plusSeconds(100)); grant.setOwnerUserId("other-owner"); assertDenied(INVITE_OWNER_MISMATCH, Operation.APPLY);
    }

    @Test void arbitrarySourceOverrideAndUnentitledTargetDeny() {
        deployment.setSourceRepositoryOverride("https://arbitrary.invalid/repo"); assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        deployment.setSourceRepositoryOverride(null);
        var request = new AuthorizationRequest("offer", "guessed-profile", "deployment", "tenant", null,
            Operation.APPLY, "operation", Set.of(), null, null, null);
        assertCode(UNENTITLED_TARGET, () -> service.authorize("customer", request));
    }

    @Test void unsupportedFieldsAndValuesCannotWidenSettings() {
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.DRAFT_EDIT, "operation", Set.of(Setting.AUTH_PROFILE_ID), null, null, null);
        assertCode(SETTING_FORBIDDEN, () -> service.authorize("customer", request));
        var auth = service.authorize("customer", request(Operation.AUTH_CONFIGURE));
        var value = new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory().textNode("unreviewed-auth");
        assertCode(SETTING_FORBIDDEN, () -> service.validateSettings(auth, new SettingsPatch(Map.of(Setting.AUTH_PROFILE_ID, value))));
        var allowed = new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory().textNode("reviewed-mixed-auth");
        service.validateSettings(auth, new SettingsPatch(Map.of(Setting.AUTH_PROFILE_ID, allowed)));
        assertCode(INVALID_CONTRACT, () -> codec.read("{\"values\":{\"SOURCE_REPOSITORY\":\"x\"}}", SettingsPatch.class));
    }

    @Test void bindingSlotRequiresReviewedProfileAndNeverAcceptsVaultNames() {
        var valid = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.BINDING_REPLACE, "operation", Set.of(), "document-storage", "reviewed-s3", null);
        service.authorize("customer", valid);
        var invalid = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.BINDING_REPLACE, "operation", Set.of(), "GLOBAL_VAULT", "reviewed-s3", null);
        assertCode(BINDING_SLOT_FORBIDDEN, () -> service.authorize("customer", invalid));
    }

    @Test void absentOrAmbiguousPrerequisiteAdapterNeverInventsReadiness() {
        when(providers.orderedStream()).thenAnswer(call -> Stream.empty()); assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);
        when(providers.orderedStream()).thenAnswer(call -> Stream.of(provider, provider)); assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);
    }

    @Test void admissionPreparationKeepsEveryAuthorizationCheckButDoesNotRequireItsFutureReservation() {
        when(providers.orderedStream()).thenAnswer(call -> Stream.empty());
        var prepared = service.prepareAdmission("customer", request(Operation.APPLY));
        assertThat(prepared.context().operation()).isEqualTo(Operation.APPLY);
        assertThat(prepared.context().customerId()).isEqualTo("customer");
        verifyNoInteractions(provider);
        assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);

        deployment.setCustomerId("other");
        assertCode(RESOURCE_SCOPE_MISMATCH,
            () -> service.prepareAdmission("customer", request(Operation.APPLY)));
    }

    @Test void admissionPreparationCannotBeUsedForOperationsWithoutAdmissionEvidence() {
        assertCode(OPERATION_FORBIDDEN,
            () -> service.prepareAdmission("customer", request(Operation.DRAFT_EDIT)));
        verifyNoInteractions(provider);
    }

    @Test void sourceRefreshPreparationRequiresExactEntitlementAndReleaseBeforeCollectingEvidence() {
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging-target");
        release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.SOURCE_DISCOVER, null, Set.of(), null, null, DocumentAudience.PUBLIC);

        var prepared = service.prepareSourcePrerequisiteRefresh("customer", request);

        assertThat(prepared.context().entitlementRevision()).isEqualTo(2);
        assertThat(prepared.context().placementEvidenceId()).isNull();
        verifyNoInteractions(provider, placement);
        grant.setStatus(EntitlementStatus.REVOKED);
        assertCode(ENTITLEMENT_REVOKED, () -> service.prepareSourcePrerequisiteRefresh("customer", request));
        grant.setStatus(EntitlementStatus.ACTIVE);
        release.setDeploymentId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.prepareSourcePrerequisiteRefresh("customer", request));
        assertCode(OPERATION_FORBIDDEN,
            () -> service.prepareSourcePrerequisiteRefresh("customer", request(Operation.APPLY)));
    }

    @Test void missingStaleAndMismatchedPlacementEvidenceNeverTriggersAProbeUnderCommandLocks() {
        for (var code : List.of(PLACEMENT_EVIDENCE_REQUIRED, PLACEMENT_EVIDENCE_STALE, PLACEMENT_DENIED)) {
            doThrow(conflict(code)).when(placement).requireFreshEvidence(any(), any());
            assertDenied(code, Operation.APPLY);
        }
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void prerequisiteConfigurationDoesNotRequireAdmissionOrReserveCapacity() {
        when(providers.orderedStream()).thenAnswer(call -> Stream.empty());
        var request = new AuthorizationRequest("offer", "staging-target", null, null, null,
            Operation.BOOTSTRAP_CONFIGURATION, "configure-operation", Set.of(), null, null, null);
        var authorization = service.authorize("customer", request);
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        service.registerBootstrap(authorization, "deployment");
        var saved = org.mockito.ArgumentCaptor.forClass(CustomerOfferDeploymentEntity.class);
        verify(associations).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBootstrapTargetProfileId()).isEqualTo("staging-target");
        when(associations.findById("deployment")).thenReturn(Optional.of(saved.getValue()));
        verifyNoInteractions(provider);
        assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);
    }

    @ParameterizedTest @EnumSource(Prerequisite.class)
    void everyAdmissionPrerequisiteIsMandatory(Prerequisite missing) {
        var passed = EnumSet.allOf(Prerequisite.class); passed.remove(missing);
        when(provider.resolve(any())).thenAnswer(call -> evidence(call.getArgument(0), passed));
        assertThatThrownBy(() -> service.authorize("customer", request(Operation.APPLY))).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void wrongContextStaleEvidenceAndMissingCapacityReservationDeny() {
        when(provider.resolve(any())).thenReturn(new PrerequisiteEvidence(null, EnumSet.allOf(Prerequisite.class),
            Instant.now().minusSeconds(1), Instant.now().plusSeconds(100), "evidence", "reservation"));
        assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);
        when(provider.resolve(any())).thenAnswer(call -> new PrerequisiteEvidence(call.getArgument(0), EnumSet.allOf(Prerequisite.class),
            Instant.now().minusSeconds(120), Instant.now().plusSeconds(100), "evidence", "reservation"));
        assertDenied(PREREQUISITE_EVIDENCE_REQUIRED, Operation.APPLY);
        when(provider.resolve(any())).thenAnswer(call -> new PrerequisiteEvidence(call.getArgument(0), EnumSet.allOf(Prerequisite.class),
            Instant.now().minusSeconds(1), Instant.now().plusSeconds(100), "evidence", null));
        assertDenied(CAPACITY_REQUIRED, Operation.APPLY);
    }

    @Test void adminAssignedOperatorAndOperatorKeyKeepDistinctCeilings() {
        CustomerOfferTestFixtures.authenticate("admin-key", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        service.authorize("customer", request(Operation.APPLY));
        var operator = new PlatformUserEntity(); operator.setId("operator"); operator.setRole("PLATFORM_OPERATOR"); operator.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("operator@test.invalid")).thenReturn(Optional.of(operator));
        var assignment = new DeploymentAssignmentEntity(); assignment.setAssignmentRole("DEPLOYMENT_OPERATOR");
        when(assignments.findByUserIdAndDeploymentId("operator", "deployment")).thenReturn(Optional.of(assignment));
        CustomerOfferTestFixtures.authenticate("operator@test.invalid", PlatformRole.PLATFORM_OPERATOR, "SESSION");
        service.authorize("customer", request(Operation.APPLY));
        when(assignments.findByUserIdAndDeploymentId("operator", "deployment")).thenReturn(Optional.empty());
        assertDenied(OPERATION_FORBIDDEN, Operation.APPLY);
        CustomerOfferTestFixtures.authenticate("operator-key", PlatformRole.PLATFORM_OPERATOR, "API_KEY");
        service.authorize("customer", request(Operation.APPLY));
        assertDenied(OPERATION_FORBIDDEN, Operation.STAGING_RETAIN);
    }

    @Test void activationRequiresExplicitOwnedReleaseAndPinnedArtifact() {
        assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.ACTIVATE);
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("staging-target"); release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.ACTIVATE, "operation", Set.of(), null, null, null);
        service.authorize("customer", request);
        var reviewedVersion = versions.findById("version").orElseThrow();
        verify(composition).requireVersion(eq(definition), same(reviewedVersion));
        release.setDeploymentId("other"); assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.authorize("customer", request));
    }

    @Test void crossCustomerConsumerCannotBeConfigured() {
        var auth = service.authorize("customer", request(Operation.APP_CONFIGURE));
        var consumer = new PlatformConsumerEntity(); consumer.setCustomerId("other"); consumer.setStatus("ACTIVE");
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.requireOwnedConsumer(auth, "consumer"));
        consumer.setCustomerId("customer"); service.requireOwnedConsumer(auth, "consumer");
    }

    @Test void catalogueRedactsSourceProviderAndVaultDataAndExcludesBadPlacement() {
        var catalogue = new CustomerSelfServiceCatalogueService(entitlements, grants, profiles, composition, placement);
        var result = catalogue.offer("offer");
        assertThat(result.targets()).hasSize(2);
        String json = codec.write(result);
        assertThat(json).doesNotContain("PRIVATE_CREDENTIAL_REF", "PRIVATE_PROVIDER_VALUE", "runtime-artifact", "providerConfig", "imageRepository");
        doThrow(conflict(PLACEMENT_DENIED)).when(placement).requireFreshEvidence(any(), eq(TargetIntent.CUSTOMER_STAGING));
        result = catalogue.offer("offer");
        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("production-target");
        assertThat(result.blockers()).contains("PLACEMENT_DENIED");
    }

    private AuthorizationRequest request(Operation operation) {
        return new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            operation, "operation", Set.of(), null, null, null);
    }
    private PrerequisiteEvidence evidence(AdmissionContext context, Set<Prerequisite> passed) {
        return new PrerequisiteEvidence(context, passed, Instant.now().minusSeconds(1), Instant.now().plusSeconds(100), "evidence", "reservation");
    }
    private void assertDenied(CustomerSelfServiceException.Code code, Operation operation) {
        assertCode(code, () -> service.authorize("customer", request(operation)));
    }
    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
