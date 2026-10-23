package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOperationalReadAuthorizationTest {
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerOfferEntitlementRepository grants = mock(CustomerOfferEntitlementRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerPlacementEvidenceRepository evidence = mock(CustomerPlacementEvidenceRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CustomerPrerequisiteEvidenceProvider> providers = mock(ObjectProvider.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    private CustomerReviewedCompositionService composition;
    private CustomerSelfServiceAuthorizationService authorization;
    private CustomerSelfServiceOperationBoundary boundary;
    private CustomerOfferEntitlementEntity grant;
    private CustomerOfferEntity offer;
    private Definition definition;
    private DeploymentEntity deployment;
    private DeploymentReleaseEntity release;
    private PlatformUserEntity owner;
    private PlatformTenantEntity tenant;
    private CustomerPlacementEvidenceEntity stagingProof;

    @BeforeEach void setup() throws Exception {
        var properties = new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer("stage-server", "stage-address", "stage-provider-address"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("prod-server", "prod-address", "prod-provider-address")), 60);
        composition = spy(new CustomerReviewedCompositionService(codec, mock(Validator.class), mock(MarketplacePluginRepository.class),
            mock(MarketplacePluginVersionRepository.class), mock(MarketplaceManifestService.class), mock(DeploymentSourceArtifactRepository.class),
            profiles, mock(CustomerWorkspaceProfileValidator.class), mapper, mock(ManagedDependencyArtifactPolicy.class), mock(ExternalIntegrationPolicy.class)));
        doNothing().when(composition).validate(any());
        doNothing().when(composition).requireVersion(any(), any());
        var placement = new CustomerTargetPlacementService(properties, resolver, client, composition, codec, evidence);
        var staging = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        var production = CustomerOfferTestFixtures.profile("production-target", "prod-server");
        when(profiles.findById(staging.getId())).thenReturn(Optional.of(staging));
        when(profiles.findById(production.getId())).thenReturn(Optional.of(production));
        ObjectNode tree = mapper.valueToTree(CustomerOfferTestFixtures.definition());
        tree.set("targets", mapper.valueToTree(List.of(
            new TargetPermission(staging.getId(), TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(staging)),
            new TargetPermission(production.getId(), TargetIntent.CUSTOMER_PRODUCTION, composition.profileFingerprint(production)))));
        tree.set("bindingSlots", mapper.valueToTree(List.of(
            new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3")),
            new BindingSlot("inbox", BindingKind.INBOX_OPERATOR, Set.of("reviewed-inbox")))));
        definition = mapper.treeToValue(tree, Definition.class);
        offer = new CustomerOfferEntity("offer", codec.write(definition), codec.hash(definition), "review", "admin", Instant.now());
        when(offers.findById("offer")).thenReturn(Optional.of(offer));
        owner = new PlatformUserEntity(); owner.setId("owner"); owner.setEmail("owner@test.invalid");
        owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE"); owner.setCustomerId("customer");
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setCustomerId("customer"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        grant = new CustomerOfferEntitlementEntity(); grant.setId("grant"); grant.setCustomerId("customer");
        grant.setOfferId("offer"); grant.setOwnerUserId("owner"); grant.setStatus(EntitlementStatus.ACTIVE);
        grant.setRevision(2); grant.setExpiresAt(Instant.now().plusSeconds(3600));
        when(grants.findForUpdate("customer", "offer")).thenReturn(Optional.of(grant));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant"); deployment.setTemplateId(definition.templateId()); deployment.setStatus("ACTIVE");
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        associate(offer.getFingerprint());
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId(deployment.getId());
        release.setTargetProfileId(staging.getId()); release.setSourceArtifactId("runtime-artifact"); release.setDeploymentVersionId("version");
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId(deployment.getId());
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        var customerAccess = new PlatformCustomerAccessService(users, customers);
        var entitlements = new CustomerOfferEntitlementService(offers, grants, mock(CustomerEntitlementCommandRepository.class),
            customerAccess, customers, users, codec, composition, placement, profiles, mock(PlatformAuditService.class));
        var access = new DeploymentAccessService(mock(DeploymentAssignmentRepository.class), mock(PublicApiDeploymentRepository.class), users, customerAccess);
        authorization = new CustomerSelfServiceAuthorizationService(entitlements, composition, placement, deployments, releases, versions,
            profiles, tenants, mock(PlatformConsumerRepository.class), access, providers, properties, associations);
        boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, authorization, entitlements, codec);
        stagingProof = record(staging, TargetIntent.CUSTOMER_STAGING, "stage-server", "stage-address");
        record(production, TargetIntent.CUSTOMER_PRODUCTION, "prod-server", "prod-address");
        CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @EnumSource(TargetIntent.class)
    void expiredPlacementAllowsOnlyAnExactExistingReleaseReadWithoutProbeOrAdmission(TargetIntent intent) {
        release.setTargetProfileId(intent == TargetIntent.CUSTOMER_STAGING ? "staging-target" : "production-target");
        var approved = read();

        assertThat(approved.context().operation()).isEqualTo(Operation.READ);
        assertThat(approved.context().customerId()).isEqualTo("customer");
        assertThat(approved.context().targetProfileId()).isEqualTo(release.getTargetProfileId());
        assertThat(approved.context().releaseId()).isEqualTo("release");
        assertThat(approved.context().entitlementRevision()).isEqualTo(2);
        assertThat(approved.context().placementEvidenceId()).isEqualTo("proof-" + release.getTargetProfileId());
        var version = versions.findById("version").orElseThrow();
        verify(composition).requireVersion(eq(definition), same(version));
        verifyNoInteractions(resolver, client, providers);
        verify(evidence, never()).saveAndFlush(any());
        assertThat(stagingProof.getExpiresAt()).isBefore(Instant.now());
    }

    @ParameterizedTest @ValueSource(strings = {"anonymous", "api-key", "disabled-owner", "foreign-owner", "foreign-customer",
        "foreign-tenant", "disabled-tenant", "revoked", "expired", "missing-association", "offer-fingerprint",
        "read-not-offered", "foreign-release", "wrong-target", "foreign-version", "unreviewed-version",
        "unreviewed-composition", "profile-drift", "inactive-profile", "missing-proof"})
    void staleReadStillDeniesCurrentAuthorityScopeAndIdentityFailures(String fault) throws Exception {
        var expected = switch (fault) {
            case "anonymous" -> { SecurityContextHolder.clearContext(); yield ACTIVE_CUSTOMER_SCOPE_REQUIRED; }
            case "api-key" -> {
                CustomerOfferTestFixtures.authenticate(owner.getEmail(), PlatformRole.CUSTOMER_ADMIN, "API_KEY");
                yield ACTIVE_CUSTOMER_SCOPE_REQUIRED;
            }
            case "disabled-owner" -> { owner.setStatus("DISABLED"); yield ACTIVE_CUSTOMER_SCOPE_REQUIRED; }
            case "foreign-owner" -> {
                var other = new PlatformUserEntity(); other.setId("other"); other.setEmail("other@test.invalid");
                other.setRole("CUSTOMER_ADMIN"); other.setStatus("ACTIVE"); other.setCustomerId("customer");
                when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(other));
                yield INVITE_OWNER_MISMATCH;
            }
            case "foreign-customer" -> { deployment.setCustomerId("foreign"); yield RESOURCE_SCOPE_MISMATCH; }
            case "foreign-tenant" -> { tenant.setCustomerId("foreign"); yield RESOURCE_SCOPE_MISMATCH; }
            case "disabled-tenant" -> { tenant.setStatus("DISABLED"); yield RESOURCE_SCOPE_MISMATCH; }
            case "revoked" -> { grant.setStatus(EntitlementStatus.REVOKED); yield ENTITLEMENT_REVOKED; }
            case "expired" -> { grant.setExpiresAt(Instant.now().minusSeconds(1)); yield ENTITLEMENT_EXPIRED; }
            case "missing-association" -> { when(associations.findById("deployment")).thenReturn(Optional.empty()); yield ENTITLEMENT_REQUIRED; }
            case "offer-fingerprint" -> { associate("old-fingerprint"); yield RESOURCE_SCOPE_MISMATCH; }
            case "read-not-offered" -> {
                ObjectNode tree = mapper.valueToTree(definition); tree.set("operations", mapper.valueToTree(Set.of(Operation.APPLY)));
                var changed = mapper.treeToValue(tree, Definition.class);
                when(offers.findById("offer")).thenReturn(Optional.of(new CustomerOfferEntity("offer", codec.write(changed),
                    codec.hash(changed), "review", "admin", Instant.now())));
                yield OPERATION_FORBIDDEN;
            }
            case "foreign-release" -> { release.setDeploymentId("foreign"); yield RESOURCE_SCOPE_MISMATCH; }
            case "wrong-target" -> { release.setTargetProfileId("production-target"); yield RESOURCE_SCOPE_MISMATCH; }
            case "foreign-version" -> { versions.findById("version").orElseThrow().setDeploymentId("foreign"); yield RESOURCE_SCOPE_MISMATCH; }
            case "unreviewed-version" -> {
                doThrow(CustomerSelfServiceException.deny(REVIEWED_COMPOSITION_CHANGED)).when(composition).requireVersion(any(), any());
                yield REVIEWED_COMPOSITION_CHANGED;
            }
            case "unreviewed-composition" -> {
                doThrow(CustomerSelfServiceException.conflict(REVIEWED_COMPOSITION_CHANGED)).when(composition).validate(any());
                yield REVIEWED_COMPOSITION_CHANGED;
            }
            case "profile-drift" -> { profiles.findById("staging-target").orElseThrow().setCredentialRefId("changed"); yield REVIEWED_COMPOSITION_CHANGED; }
            case "inactive-profile" -> { profiles.findById("staging-target").orElseThrow().setActive(false); yield REVIEWED_COMPOSITION_CHANGED; }
            case "missing-proof" -> {
                when(evidence.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(any(), any(), any())).thenReturn(Optional.empty());
                yield PLACEMENT_EVIDENCE_REQUIRED;
            }
            default -> throw new IllegalArgumentException(fault);
        };

        assertCode(expected, () -> boundary.requireOperationalRead(deployment, "staging-target", "release", DocumentAudience.PUBLIC));
        verifyNoInteractions(resolver, client, providers);
        verify(evidence, never()).saveAndFlush(any());
    }

    @ParameterizedTest @EnumSource(Operation.class)
    void everyOrdinaryAuthorizationIncludingWritesAndSourceCommandsStillRequiresFreshPlacement(Operation operation) {
        if (operation == Operation.PROMOTION_PLAN || operation == Operation.PROMOTION_APPLY) release.setTargetProfileId("production-target");
        assertCode(PLACEMENT_EVIDENCE_STALE, () -> authorization.authorize("customer", request(operation)));
        verifyNoInteractions(resolver, client, providers);
    }

    @ParameterizedTest @EnumSource(value = Operation.class, mode = EnumSource.Mode.EXCLUDE, names = "READ")
    void operationalReadEntryPointCannotAuthorizeAnyOtherOperation(Operation operation) {
        assertCode(OPERATION_FORBIDDEN, () -> authorization.authorizeOperationalRead("customer", request(operation)));
        verifyNoInteractions(evidence, resolver, client, providers);
    }

    @Test void operationalReadRequiresExplicitReleaseAndCannotWidenSettingsBindingsOrAudience() {
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> boundary.requireOperationalRead(deployment, "staging-target", null, DocumentAudience.PUBLIC));
        assertCode(SETTING_FORBIDDEN, () -> boundary.requireOperationalRead(deployment, "staging-target", "release", DocumentAudience.AUTHENTICATED));
        assertCode(SETTING_FORBIDDEN, () -> authorization.authorizeOperationalRead("customer", new AuthorizationRequest(
            "offer", "staging-target", "deployment", "tenant", "release", Operation.READ, null, Set.of(Setting.APP_TITLE), null, null, null)));
        assertCode(BINDING_SLOT_FORBIDDEN, () -> authorization.authorizeOperationalRead("customer", new AuthorizationRequest(
            "offer", "staging-target", "deployment", "tenant", "release", Operation.READ, null, Set.of(), "document-storage", "reviewed-s3", null)));
    }

    private Authorization read() {
        return boundary.requireOperationalRead(deployment, release.getTargetProfileId(), "release", DocumentAudience.PUBLIC);
    }

    private AuthorizationRequest request(Operation operation) {
        var row = CustomerPermissionMatrix.row(operation);
        boolean inbox = switch (operation) {
            case INBOX_OPERATOR_ENROLL, INBOX_OPERATOR_ROTATE, INBOX_OPERATOR_DELIVER, INBOX_OPERATOR_REVOKE -> true;
            default -> false;
        };
        return new AuthorizationRequest("offer", release.getTargetProfileId(), "deployment", "tenant", "release", operation,
            "operation-key", Set.of(), row.ownedBindingRequired() ? inbox ? "inbox" : "document-storage" : null,
            operation == Operation.BINDING_REPLACE ? "reviewed-s3" : null, DocumentAudience.PUBLIC);
    }

    private CustomerPlacementEvidenceEntity record(DeploymentTargetProfileEntity profile, TargetIntent intent, String server, String address) {
        String fingerprint = composition.profileFingerprint(profile);
        var proof = new CustomerPlacementEvidenceEntity("proof-" + profile.getId(), profile.getId(), intent, fingerprint, server, address,
            "destination", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3540));
        when(evidence.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(profile.getId(), intent, fingerprint))
            .thenReturn(Optional.of(proof));
        return proof;
    }

    private void associate(String fingerprint) {
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity("deployment", "customer", "tenant",
            "offer", "staging-target", fingerprint, 2, "bootstrap", Instant.now())));
    }

    private void assertCode(CustomerSelfServiceException.Code code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomerSelfServiceException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
