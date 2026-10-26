package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.conflict;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerSelfServiceAuthorizationServiceTest {
    @Test
    void retiredInboxOperationsCannotGrantAccessEvenFromHistoricalOfferDefinitions() {
        clearInvocations(deployments, grants, offers);
        for (var operation : Set.of(Operation.INBOX_OPERATOR_ENROLL, Operation.INBOX_OPERATOR_ROTATE,
                Operation.INBOX_OPERATOR_DELIVER, Operation.INBOX_OPERATOR_REVOKE)) {
            assertThatThrownBy(() -> service.authorize("customer", new AuthorizationRequest(
                "offer", "staging-target", "deployment", "tenant", null, operation,
                "retired-operation", Set.of(), "inbox", null, null)))
                .hasMessageContaining(OPERATION_FORBIDDEN.name());
        }
        verifyNoInteractions(deployments, grants, offers);
    }
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
    private final com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver bindings =
        mock(com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver.class);
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
        var staging = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        staging.setEnvironmentName("staging");
        var production = CustomerOfferTestFixtures.profile("production-target", "prod-server");
        production.setEnvironmentName("production");
        when(profiles.findById("staging-target")).thenReturn(Optional.of(staging));
        when(profiles.findById("production-target")).thenReturn(Optional.of(production));
        var reviewedVersion = new DeploymentVersionEntity();
        reviewedVersion.setId("version"); reviewedVersion.setDeploymentId("deployment"); reviewedVersion.setStatus("PUBLISHED");
        reviewedVersion.setConfigHash("published-config-hash");
        when(versions.findById("version")).thenReturn(Optional.of(reviewedVersion));
        var customerAccess = new PlatformCustomerAccessService(users, customers);
        entitlements = new CustomerOfferEntitlementService(offers, grants, mock(CustomerEntitlementCommandRepository.class),
            customerAccess, customers, users, codec, composition, placement, profiles, mock(PlatformAuditService.class));
        var access = new DeploymentAccessService(assignments, mock(PublicApiDeploymentRepository.class), users, customerAccess);
        service = new CustomerSelfServiceAuthorizationService(entitlements, composition, placement, deployments, releases, versions, profiles,
            tenants, consumers, access, associations, bindings);
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void activeOwnerCanUpgradeWithoutBootstrapPinComparisons() {
        var authorization = service.authorize("customer", request(Operation.APPLY));
        assertThat(authorization.context().entitlementRevision()).isEqualTo(2);
        assertThat(authorization.definition()).isNull();
        service.requirePinnedComposition(authorization, "template-v2", Set.of("new-plugin-v2"), Set.of("new-runtime"));
        verifyNoInteractions(composition, grants, offers);
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void externalOperationsPathDoesNotRequireCapacityOrPlacementLease() {
        var approved = service.authorize("customer", request(Operation.APPLY));
        assertThat(approved.context().placementEvidenceId()).isNull();
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));
        verify(placement, never()).requireFreshEvidence(any(), any());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.authorize("other-customer", request(Operation.APPLY)));
        grant.setStatus(EntitlementStatus.REVOKED);
        service.authorize("customer", request(Operation.APPLY));
    }

    @Test void sourceAccessStillRequiresTheFrozenOwnedStorageBindingWithoutCapacityRecords() {
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setDeploymentVersionId("version"); release.setTargetProfileId("staging-target");
        release.setSourceArtifactId("runtime-artifact");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(bindings.required(any())).thenReturn(true);
        var source = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.SOURCE_PREVIEW, "preview-command", Set.of(), null, null, DocumentAudience.PUBLIC);
        service.authorize("customer", source);
        verify(bindings).requireUnchanged(eq(deployment), any(), eq(release));
        doThrow(new IllegalStateException("STORAGE_BINDING_CHANGED")).when(bindings).requireUnchanged(any(), any(), any());
        assertThatThrownBy(() -> service.authorize("customer", source)).hasMessage("STORAGE_BINDING_CHANGED");
    }

    @Test void authorityCandidatePreflightChecksOwnedActiveTargetNotOriginalOfferOrInvitation() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);

        var approved = boundary.prepareAdmission(
            deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight"
        );

        assertThat(approved.context().operation()).isEqualTo(Operation.APPLY);
        assertThat(approved.context().targetProfileId()).isEqualTo("staging-target");
        assertThat(approved.context().offerId()).isEqualTo("offer");
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));

        assertCode(UNENTITLED_TARGET, () -> boundary.prepareAdmission(
            deployment, "other-target", Operation.APPLY, "runtime-authority-preflight"
        ));
        doThrow(conflict(REVIEWED_COMPOSITION_CHANGED)).when(composition).requireProfile(any(), any());
        boundary.prepareAdmission(deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight");
        grant.setStatus(EntitlementStatus.REVOKED);
        boundary.prepareAdmission(deployment, "staging-target", Operation.APPLY, "runtime-authority-preflight");
        verifyNoInteractions(composition, grants, offers);
    }

    @Test void owningBoundaryAllowsNewArtifactsAndSettingsWithoutAnOfferReview() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.require(deployment, "staging-target", null, Operation.APPLY, "apply-key", Map.of(), null, null, null);
        boundary.requireArtifact(approved, "runtime-artifact");
        boundary.requireArtifact(approved, "new-runtime-artifact");
        assertCode(ARTIFACT_REQUIRED, () -> boundary.requireArtifact(approved, " "));
        assertCode(INVALID_CONTRACT, () -> boundary.requireArtifact(null, "runtime-artifact"));
        assertCode(UNENTITLED_TARGET, () -> boundary.require(deployment, "other-target", null, Operation.APPLY,
            "apply-key", Map.of(), null, null, null));
        var value = com.fasterxml.jackson.databind.node.TextNode.valueOf("unreviewed-auth");
        boundary.require(deployment, "staging-target", null, Operation.AUTH_CONFIGURE,
            "auth-key", Map.of(Setting.AUTH_PROFILE_ID, value), null, null, null);
    }

    @Test void genericApplyRequiresTheActualOwnedPublishedVersionNotOfferComposition() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.require(deployment, "staging-target", null, Operation.APPLY, "apply-key", Map.of(), null, null, null);
        var version = versions.findById("version").orElseThrow();
        boundary.requireVersion(approved, version);
        verify(composition).requireVersion(null, version);

        version.setDeploymentId("foreign-deployment");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(approved, version));
    }

    @Test void genericExistingDeploymentUsesRealAuthorizationWithoutAnyOfferAssociation() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        var customerAuthorization = boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            "app-key", Map.of(), null, null, null);
        assertThat(customerAuthorization).isNotNull();
        assertThat(customerAuthorization.context().offerId()).isNull();
        assertThat(customerAuthorization.definition()).isNull();
        CustomerOfferTestFixtures.authenticate("platform-admin", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        assertThat(boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            "staff-app-key", Map.of(), null, null, null)).isNotNull();
    }

    @Test void changedTemplateAndNewActiveTargetRemainOwnedOperations() {
        deployment.setTemplateId("customer-customized-template");
        var target = CustomerOfferTestFixtures.profile("new-production-target", "prod-server");
        target.setEnvironmentName("production");
        when(profiles.findById(target.getId())).thenReturn(Optional.of(target));
        grant.setExpiresAt(Instant.now().minusSeconds(1));
        var request = new AuthorizationRequest("offer", target.getId(), "deployment", "tenant", null,
            Operation.APPLY, "upgrade-new-target", Set.of(), null, null, null);
        assertThat(service.authorize("customer", request).context().intent()).isEqualTo(TargetIntent.CUSTOMER_PRODUCTION);
        target.setActive(false);
        assertCode(UNENTITLED_TARGET, () -> service.authorize("customer", request));
        verifyNoInteractions(offers, grants, composition);
    }

    @Test void publishedVersionMustExistAndKeepItsActualStoredIdentity() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var authorization = service.authorize("customer", request(Operation.APPLY));
        var candidate = new DeploymentVersionEntity();
        candidate.setId("version"); candidate.setDeploymentId("deployment"); candidate.setStatus("PUBLISHED");
        candidate.setConfigHash("published-config-hash");
        boundary.requireVersion(authorization, candidate);
        candidate.setConfigHash("forged-hash");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(authorization, candidate));
        candidate.setConfigHash("published-config-hash"); candidate.setStatus("DRAFT");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(authorization, candidate));
        candidate.setStatus("PUBLISHED"); candidate.setManifestJson("changed-manifest");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(authorization, candidate));
        candidate.setManifestJson(null); candidate.setId("missing-version");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireVersion(authorization, candidate));
        assertCode(INVALID_CONTRACT, () -> boundary.requireVersion(null, candidate));
    }

    @Test void bootstrapStillRequiresExactCompositionAndAllowedDefaults() {
        var authorization = service.authorize("customer", request(Operation.BOOTSTRAP));
        service.requirePinnedComposition(authorization, "template-v1", Set.of(), Set.of("runtime-artifact"));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.requirePinnedComposition(authorization,
            "template-v2", Set.of(), Set.of("runtime-artifact")));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.requirePinnedComposition(authorization,
            "template-v1", Set.of("new-plugin"), Set.of("runtime-artifact")));
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.requirePinnedComposition(authorization,
            "template-v1", Set.of(), Set.of("new-runtime")));
        assertCode(SETTING_FORBIDDEN, () -> service.validateSettings(authorization, new SettingsPatch(
            Map.of(Setting.AUTH_PROFILE_ID, com.fasterxml.jackson.databind.node.TextNode.valueOf("new-auth")))));
        deployment.setTemplateId("not-the-selected-template");
        assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.BOOTSTRAP);
    }

    @Test void anotherActiveCustomerAdminForTheSameCustomerDoesNotNeedOriginalInviteOwnership() {
        owner.setId("another-customer-admin");
        when(users.findById("another-customer-admin")).thenReturn(Optional.of(owner));
        assertThat(service.authorize("customer", request(Operation.APPLY))).isNotNull();
        verifyNoInteractions(grants, offers);
    }

    @Test void foreignTenantOrReleaseVersionCannotEnterExistingOperation() {
        var foreignTenant = new AuthorizationRequest("offer", "staging-target", "deployment", "foreign-tenant", null,
            Operation.APPLY, "operation", Set.of(), null, null, null);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.authorize("customer", foreignTenant));
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("staging-target"); release.setDeploymentVersionId("foreign-version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var version = new DeploymentVersionEntity(); version.setId("foreign-version"); version.setDeploymentId("foreign-deployment");
        version.setStatus("PUBLISHED"); version.setConfigHash("hash");
        when(versions.findById("foreign-version")).thenReturn(Optional.of(version));
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.ACTIVATE, "activate", Set.of(), null, null, null);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.authorize("customer", request));
    }

    @Test void staffViewerCannotApplyOrEditEvenWhenTheDeploymentIsGeneric() {
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        var operator = new PlatformUserEntity(); operator.setId("operator"); operator.setRole("PLATFORM_OPERATOR");
        operator.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("operator@test.invalid")).thenReturn(Optional.of(operator));
        var assignment = new DeploymentAssignmentEntity(); assignment.setAssignmentRole("DEPLOYMENT_VIEWER");
        when(assignments.findByUserIdAndDeploymentId("operator", "deployment")).thenReturn(Optional.of(assignment));
        CustomerOfferTestFixtures.authenticate("operator@test.invalid", PlatformRole.PLATFORM_OPERATOR, "SESSION");
        service.authorize("customer", request(Operation.READ));
        assertDenied(OPERATION_FORBIDDEN, Operation.APPLY);
        assertDenied(OPERATION_FORBIDDEN, Operation.DRAFT_EDIT);
    }

    @Test void bootstrapInvitationAndOperationAllowlistDoNotGovernExistingStaffOrCustomerOperations() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        grant.setStatus(EntitlementStatus.REVOKED);
        boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
            "app-key", Map.of(), null, null, null);
        grant.setStatus(EntitlementStatus.ACTIVE);
        var limited = new Definition(definition.schemaVersion(), definition.name(), definition.templateId(), definition.template(),
            definition.plugins(), definition.artifacts(), definition.targets(), Set.of(Operation.READ), definition.settings(),
            definition.bindingSlots(), definition.documentAudiences(), definition.admissionPrerequisites(), definition.limits(), definition.topology(),
            definition.approvedPolicyRef(), definition.operatingPolicyRef(), definition.identityPolicyRef(), definition.storagePolicyRef());
        offer = new CustomerOfferEntity("offer", codec.write(limited), codec.hash(limited), "review", "admin", Instant.now());
        when(offers.findById("offer")).thenReturn(Optional.of(offer));
        var staff = new PlatformUserEntity(); staff.setId("staff-admin");
        staff.setRole("PLATFORM_ADMIN"); staff.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("platform-admin")).thenReturn(Optional.of(staff));
        for (var role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_ADMIN)) {
            CustomerOfferTestFixtures.authenticate(role == PlatformRole.CUSTOMER_ADMIN ? owner.getEmail() : "platform-admin", role, "SESSION");
            boundary.require(deployment, "staging-target", null, Operation.APP_CONFIGURE,
                "app-key", Map.of(), null, null, null);
        }
    }

    @Test void consumerBoundaryRequiresExactOwnedBoundTargetAndAllowsConfigurationBeforeRelease() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var consumer = new PlatformConsumerEntity();
        consumer.setId("consumer"); consumer.setCustomerId("customer"); consumer.setStatus("ACTIVE");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of()));
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        var draftAuthorization = boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "draft-app", Map.of());
        assertThat(draftAuthorization).isNotNull();
        assertThat(draftAuthorization.context().customerId()).isEqualTo("customer");
        consumer.setCustomerId("foreign-customer");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, "app", Map.of()));
        consumer.setCustomerId("customer");
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

    @Test void bootstrapS3PermissionCannotAuthorizeAMountedSourceOrAnUnrelatedSlot() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = service.authorize("customer", request(Operation.BOOTSTRAP));
        boundary.requireS3(approved, "document-storage", "S3_COMPATIBLE_OBJECT_STORAGE");
        assertCode(BINDING_SLOT_FORBIDDEN, () -> boundary.requireS3(approved, "document-storage", "MOUNTED_FOLDER"));
        assertCode(BINDING_SLOT_FORBIDDEN, () -> boundary.requireS3(approved, "foreign-slot", "S3_COMPATIBLE_OBJECT_STORAGE"));
    }

    @Test void assignedStaffCanUseActiveDevelopmentTargetsWithoutCustomerPlacementRestrictions() {
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        var target = CustomerOfferTestFixtures.profile("dev-target", "dev-server");
        target.setEnvironmentName("dev");
        when(profiles.findById("dev-target")).thenReturn(Optional.of(target));
        var staff = new PlatformUserEntity(); staff.setId("operator"); staff.setStatus("ACTIVE");
        staff.setRole("PLATFORM_OPERATOR");
        when(users.findByEmailIgnoreCase("operator@test.invalid")).thenReturn(Optional.of(staff));
        var assignment = new DeploymentAssignmentEntity(); assignment.setAssignmentRole("DEPLOYMENT_OPERATOR");
        when(assignments.findByUserIdAndDeploymentId("operator", "deployment")).thenReturn(Optional.of(assignment));
        CustomerOfferTestFixtures.authenticate("operator@test.invalid", PlatformRole.PLATFORM_OPERATOR, "SESSION");

        var approved = service.authorize("customer", new AuthorizationRequest(null, "dev-target", "deployment",
            "tenant", null, Operation.APPLY, "apply-dev", Set.of(), null, null, null));

        assertThat(approved.context().intent()).isNull();
        assertThat(approved.context().targetProfileId()).isEqualTo("dev-target");
        assertThat(approved.definition()).isNull();
        verifyNoInteractions(placement, composition, grants, offers);
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        boundary.requireS3(approved, "custom-slot", "MOUNTED_FOLDER");
        deployment.setCustomerId(null);
        deployment.setTenantId(null);
        var internal = service.authorize(null, new AuthorizationRequest(null, "dev-target", "deployment",
            null, null, Operation.APPLY, null, Set.of(), null, null, null));
        assertThat(internal.context().customerId()).isNull();
        assertThat(internal.context().tenantId()).isNull();
        assertThat(internal.context().targetProfileId()).isEqualTo("dev-target");
    }

    @Test void existingGenericApplyAllowsNoOptionalKeyButValidatesAnySuppliedKey() {
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        var approved = service.authorize("customer", new AuthorizationRequest(null, "staging-target", "deployment",
            "tenant", null, Operation.APPLY, null, Set.of(), null, null, null));
        assertThat(approved.context().operationKey()).isNull();
        assertCode(INVALID_CONTRACT, () -> service.authorize("customer", new AuthorizationRequest(null,
            "staging-target", "deployment", "tenant", null, Operation.APPLY, " key-not-trimmed", Set.of(), null, null, null)));
        assertCode(INVALID_CONTRACT, () -> service.authorize("customer", new AuthorizationRequest(null,
            "staging-target", "deployment", "tenant", null, Operation.APPLY, "", Set.of(), null, null, null)));
    }

    @Test void targetlessAppConfigurationUsesOwnedDeploymentScopeWithoutChoosingATarget() {
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer"); consumer.setCustomerId("customer");
        consumer.setStatus("ACTIVE"); consumer.setBoundDeploymentId("deployment");
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);
        var approved = boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, null, Map.of());
        assertThat(approved.context().deploymentId()).isEqualTo("deployment");
        assertThat(approved.context().targetProfileId()).isNull();
        verifyNoInteractions(profiles, placement);
        consumer.setBoundReleaseId("release-without-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, null, Map.of()));
        consumer.setBoundReleaseId(null);
        consumer.setCustomerId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireConsumer(consumer, Operation.APP_CONFIGURE, null, Map.of()));
    }

    @Test void bootstrapAudiencesAndSlotsDoNotRestrictActualOwnedReleaseBindings() {
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, service, entitlements, codec);

        assertThat(boundary.reviewedDocumentAudiences(deployment)).isEmpty();
        var approved = boundary.requireBindingKind(deployment, "staging-target", null,
            Operation.BINDING_REVOKE, "binding-key", BindingKind.CUSTOMER_S3);

        assertThat(approved.context().operation()).isEqualTo(Operation.BINDING_REVOKE);
        assertThat(approved.definition()).isNull();
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

    @Test void foreignDeploymentDeniesButMissingBootstrapAssociationDoesNot() {
        deployment.setCustomerId("other"); assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.APPLY);
        deployment.setCustomerId("customer"); when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertThat(service.authorize("customer", request(Operation.APPLY))).isNotNull();
    }

    @Test void missingInvitedExpiredRevokedAndForeignOwnerGrantsDeny() {
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.empty()); assertDenied(ENTITLEMENT_REQUIRED, Operation.BOOTSTRAP);
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.of(grant));
        grant.setStatus(EntitlementStatus.INVITED); assertDenied(ENTITLEMENT_REQUIRED, Operation.BOOTSTRAP);
        grant.setStatus(EntitlementStatus.REVOKED); assertDenied(ENTITLEMENT_REVOKED, Operation.BOOTSTRAP);
        grant.setStatus(EntitlementStatus.ACTIVE); grant.setExpiresAt(Instant.now().minusSeconds(1)); assertDenied(ENTITLEMENT_EXPIRED, Operation.BOOTSTRAP);
        grant.setExpiresAt(Instant.now().plusSeconds(100)); grant.setOwnerUserId("other-owner"); assertDenied(INVITE_OWNER_MISMATCH, Operation.BOOTSTRAP);
    }

    @Test void sourceChangesAreNotBootstrapLockedButAnUnknownTargetStillDenies() {
        deployment.setSourceRepositoryOverride("https://changed-source.invalid/repo"); service.authorize("customer", request(Operation.APPLY));
        deployment.setSourceRepositoryOverride(null);
        var request = new AuthorizationRequest("offer", "guessed-profile", "deployment", "tenant", null,
            Operation.APPLY, "operation", Set.of(), null, null, null);
        assertCode(UNENTITLED_TARGET, () -> service.authorize("customer", request));
    }

    @Test void changedDeploymentAndProviderProfilesDoNotRequireNewBootstrapSettingsApproval() {
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.DRAFT_EDIT, "operation", Set.of(Setting.AUTH_PROFILE_ID), null, null, null);
        service.authorize("customer", request);
        var auth = service.authorize("customer", request(Operation.AUTH_CONFIGURE));
        var value = new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory().textNode("unreviewed-auth");
        service.validateSettings(auth, new SettingsPatch(Map.of(Setting.AUTH_PROFILE_ID, value)));
        var allowed = new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory().textNode("reviewed-mixed-auth");
        service.validateSettings(auth, new SettingsPatch(Map.of(Setting.AUTH_PROFILE_ID, allowed)));
        assertCode(INVALID_CONTRACT, () -> codec.read("{\"values\":{\"SOURCE_REPOSITORY\":\"x\"}}", SettingsPatch.class));
    }

    @Test void bindingAuthorizationDoesNotResolveOrGrantAccessToCallerSuppliedCredentialNames() {
        var valid = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.BINDING_REPLACE, "operation", Set.of(), "document-storage", "reviewed-s3", null);
        service.authorize("customer", valid);
        var invalid = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            Operation.BINDING_REPLACE, "operation", Set.of(), "GLOBAL_VAULT", "reviewed-s3", null);
        service.authorize("customer", invalid);
        verifyNoInteractions(composition, grants, offers, bindings);
    }

    @Test void capacityAndNotificationPolicyDoNotBecomeSecurityPrerequisites() {
        service.authorize("customer", request(Operation.APPLY));
        var context = service.authorize("customer", request(Operation.APPLY)).context();
        service.requirePrerequisites(context, Set.of(Prerequisite.CAPACITY, Prerequisite.NOTIFICATIONS));
        verifyNoInteractions(bindings);
    }

    @Test void admissionPreparationKeepsEveryAuthorizationCheckButDoesNotRequireItsFutureReservation() {
        var prepared = service.prepareAdmission("customer", request(Operation.APPLY));
        assertThat(prepared.context().operation()).isEqualTo(Operation.APPLY);
        assertThat(prepared.context().customerId()).isEqualTo("customer");
        service.authorize("customer", request(Operation.APPLY));

        deployment.setCustomerId("other");
        assertCode(RESOURCE_SCOPE_MISMATCH,
            () -> service.prepareAdmission("customer", request(Operation.APPLY)));
    }

    @Test void admissionPreparationCannotBeUsedForOperationsWithoutAdmissionEvidence() {
        assertCode(OPERATION_FORBIDDEN,
            () -> service.prepareAdmission("customer", request(Operation.DRAFT_EDIT)));
    }

    @Test void sourceRefreshPreparationRequiresExactReleaseNotAnUnexpiredBootstrapInvitation() {
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging-target");
        release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.SOURCE_DISCOVER, null, Set.of(), null, null, DocumentAudience.PUBLIC);

        var prepared = service.prepareSourcePrerequisiteRefresh("customer", request);

        assertThat(prepared.context().entitlementRevision()).isEqualTo(2);
        assertThat(prepared.context().placementEvidenceId()).isNull();
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));
        grant.setStatus(EntitlementStatus.REVOKED);
        service.prepareSourcePrerequisiteRefresh("customer", request);
        grant.setStatus(EntitlementStatus.ACTIVE);
        release.setDeploymentId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.prepareSourcePrerequisiteRefresh("customer", request));
        assertCode(OPERATION_FORBIDDEN,
            () -> service.prepareSourcePrerequisiteRefresh("customer", request(Operation.APPLY)));
    }

    @Test void configuredTargetIdentityRemainsMandatoryWithoutAnyPlacementLease() {
        doThrow(conflict(PLACEMENT_DENIED)).when(placement).requireConfiguredPlacement(any(), any());
        assertDenied(PLACEMENT_DENIED, Operation.APPLY);
        verify(placement, never()).probeAndRecord(any(), any());
        verify(placement, never()).requireFreshEvidence(any(), any());
    }

    @Test void prerequisiteConfigurationDoesNotRequireAdmissionOrReserveCapacity() {
        var request = new AuthorizationRequest("offer", "staging-target", null, null, null,
            Operation.BOOTSTRAP_CONFIGURATION, "configure-operation", Set.of(), null, null, null);
        var authorization = service.authorize("customer", request);
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        service.registerBootstrap(authorization, "deployment");
        var saved = org.mockito.ArgumentCaptor.forClass(CustomerOfferDeploymentEntity.class);
        verify(associations).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBootstrapTargetProfileId()).isEqualTo("staging-target");
        when(associations.findById("deployment")).thenReturn(Optional.of(saved.getValue()));
        service.authorize("customer", request(Operation.APPLY));
    }

    @Test void sourcePreparationCannotBypassFrozenStorageChecks() {
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setDeploymentVersionId("version");
        release.setTargetProfileId("staging-target"); release.setSourceArtifactId("runtime-artifact");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(bindings.required(any())).thenReturn(true);
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.SOURCE_PREVIEW, null, Set.of(), null, null, DocumentAudience.PUBLIC);
        doThrow(new IllegalStateException("STORAGE_BINDING_CHANGED")).when(bindings).requireUnchanged(any(), any(), any());
        assertThatThrownBy(() -> service.prepareSourcePrerequisiteRefresh("customer", request))
            .hasMessage("STORAGE_BINDING_CHANGED");
        assertThatThrownBy(() -> service.authorize("customer", request)).hasMessage("STORAGE_BINDING_CHANGED");
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

    @Test void activationRequiresExplicitOwnedReleaseNotTheOriginalPinnedArtifact() {
        assertDenied(RESOURCE_SCOPE_MISMATCH, Operation.ACTIVATE);
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("staging-target"); release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var request = new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", "release",
            Operation.ACTIVATE, "operation", Set.of(), null, null, null);
        service.authorize("customer", request);
        var reviewedVersion = versions.findById("version").orElseThrow();
        verify(composition).requireVersion(isNull(), same(reviewedVersion));
        release.setSourceArtifactId("new-runtime-artifact");
        service.authorize("customer", request);
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
        doThrow(conflict(PLACEMENT_DENIED)).when(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));
        result = catalogue.offer("offer");
        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("production-target");
        assertThat(result.blockers()).contains("PLACEMENT_DENIED");
    }

    private AuthorizationRequest request(Operation operation) {
        return new AuthorizationRequest("offer", "staging-target", "deployment", "tenant", null,
            operation, "operation", Set.of(), null, null, null);
    }
    private void assertDenied(CustomerSelfServiceException.Code code, Operation operation) {
        assertCode(code, () -> service.authorize("customer", request(operation)));
    }
    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
