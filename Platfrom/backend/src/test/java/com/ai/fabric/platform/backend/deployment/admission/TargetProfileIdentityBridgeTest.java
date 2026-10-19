package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetPermission;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerPlacementEvidenceRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real composition/placement/admission services, with provider and persistence edges isolated to test fixtures. */
class TargetProfileIdentityBridgeTest extends AdmissionFixture {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient client = mock(CoolifyApiClient.class);
    private final CoolifyTargetProfileConfig config = mock(CoolifyTargetProfileConfig.class);
    private CustomerReviewedCompositionService composition;
    private CustomerTargetPlacementService placement;
    private DeploymentAdmissionService admissions;
    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites;
    private DeploymentTargetProfileEntity profile;
    private CustomerPlacementEvidenceEntity placementEvidence;
    private TargetPermission permission;
    private FleetPolicy reviewedPolicy;
    private CustomerOfferContracts.AdmissionContext customerContext;

    @BeforeEach void bridge() throws Exception {
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        var transactions = new DataSourceTransactionManager(dataSource);
        store = new AdmissionStore(jdbc, json, transactions, clock);
        for (String table : List.of("platform_deployments", "platform_customers", "platform_customer_tenants",
            "platform_deployment_versions", "deployment_target_profiles")) jdbc.execute("CREATE TABLE " + table + " (id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment1')");
        jdbc.update("INSERT INTO platform_customers VALUES ('customer')");
        jdbc.update("INSERT INTO platform_customer_tenants VALUES ('tenant')");
        jdbc.update("INSERT INTO platform_deployment_versions VALUES ('version')");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES (?)", TARGET);
        profile = new DeploymentTargetProfileEntity();
        profile.setId(TARGET); profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("customer-staging"); profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setCredentialRefId("fixture-credential");
        profile.setProviderConfigJson(mapper.writeValueAsString(Map.of("serverUuid", STAGING.serverUuid(),
            "destinationUuid", STAGING.destinationUuid(), "options", Map.of("generation", 1))));
        profile.setNetworkPolicyJson("{\"origins\":[\"https://workspace.example\"],\"cookies\":false}");
        profile.setResourceDefaultsJson("{\"limits\":{\"cpuMillis\":1,\"memoryMiB\":1}}");
        when(targets.findById(TARGET)).thenReturn(Optional.of(profile));
        var codec = new CustomerOfferCodec(mapper);
        composition = new CustomerReviewedCompositionService(codec, mock(jakarta.validation.Validator.class),
            mock(MarketplacePluginRepository.class), mock(MarketplacePluginVersionRepository.class), mock(MarketplaceManifestService.class),
            mock(DeploymentSourceArtifactRepository.class), targets, mock(CustomerWorkspaceProfileValidator.class), mapper,
            mock(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy.class),
            mock(com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.class));
        permission = new TargetPermission(TARGET, TargetIntent.CUSTOMER_STAGING, composition.profileFingerprint(profile));
        composition.requireProfile(profile, permission);

        var savedPlacement = new AtomicReference<CustomerPlacementEvidenceEntity>();
        var evidenceRepository = mock(CustomerPlacementEvidenceRepository.class);
        when(evidenceRepository.saveAndFlush(any())).thenAnswer(call -> {
            savedPlacement.set(call.getArgument(0)); return savedPlacement.get();
        });
        when(evidenceRepository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(savedPlacement.get())
            .filter(evidence -> evidence.getId().equals(call.getArgument(0))));
        when(evidenceRepository.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(anyString(), any(), anyString()))
            .thenAnswer(call -> Optional.ofNullable(savedPlacement.get()).filter(evidence ->
                evidence.getTargetProfileId().equals(call.getArgument(0)) && evidence.getIntent() == call.getArgument(1)
                    && evidence.getProfileFingerprint().equals(call.getArgument(2))));
        var trust = new CustomerSelfServiceProperties(Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer(STAGING.serverUuid(), STAGING.ip(), "host.docker.internal"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer("kvufjk78dj4wyhjgp1mlxecr", "46.225.162.106", "host.docker.internal")), 300);
        placement = new CustomerTargetPlacementService(trust, resolver, client, composition, codec, evidenceRepository);
        when(config.serverUuid()).thenReturn(STAGING.serverUuid()); when(config.destinationUuid()).thenReturn(STAGING.destinationUuid());
        var connection = new CoolifyConnection("https://provider.example", "fictional-provider-token", config);
        when(resolver.requireConnection(profile)).thenReturn(connection);
        var details = mapper.createObjectNode().put("serverUuid", STAGING.serverUuid()).put("destinationServerUuid", STAGING.serverUuid())
            .put("destinationUuid", STAGING.destinationUuid()).put("serverReachable", true).put("serverUsable", true);
        when(resolver.preflight(profile)).thenReturn(new DeploymentProviderPreflightSummary(TARGET, DeploymentProviderType.COOLIFY,
            "PASSED", "fixture", "https://provider.example", "fixture", List.of("destination_server_matches"), details, now.minusSeconds(1)));
        var server = mapper.createObjectNode().put("uuid", STAGING.serverUuid()).put("ip", "host.docker.internal");
        server.putObject("settings").put("is_reachable", true).put("is_usable", true);
        when(client.getServer(connection, STAGING.serverUuid())).thenReturn(server);
        placementEvidence = placement.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING);

        var deployment = new DeploymentEntity(); deployment.setId("deployment1"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant"); deployment.setStatus("ACTIVE");
        when(deployments.findById("deployment1")).thenReturn(Optional.of(deployment));
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment1");
        version.setStatus("PUBLISHED"); version.setConfigHash("config-hash");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        var tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setCustomerId("customer"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        var owner = new PlatformUserEntity(); owner.setId("owner"); owner.setCustomerId("customer");
        owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE"); owner.setEmail("owner@example.test");
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        var adapters = new DefaultListableBeanFactory();
        var documentBindings = mock(DeploymentDocumentBindingResolver.class);
        var binding = mapper.createObjectNode().put("bindingRef", "fixture-binding");
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(binding);
        var dependencyEvidence = mock(ReviewedManagedDependencyEvidenceService.class);
        var notices = new AdmissionLifecycleProperties(true, Duration.ofSeconds(30), Duration.ofSeconds(30), 32,
            "approved-mail", true, true, "notices@example.test", true);
        var mail = new DefaultListableBeanFactory(); mail.registerSingleton("sender", mock(JavaMailSender.class));
        var readback = new ProductionAdmissionEvidenceSource(targets, deployments, versions, documentBindings, dependencyEvidence,
            placement, new AdmissionOwnerContactResolver(users), notices, mail.getBeanProvider(JavaMailSender.class), json, clock);
        adapters.registerSingleton("prerequisites", readback);
        admissions = new DeploymentAdmissionService(store, json, mapper, deployments, versions, targets, mock(DeploymentAccessService.class),
            new PlatformCustomerAccessService(users, customers), customers, tenants, adapters.getBeanProvider(AdmissionEvidenceSource.class),
            adapters.getBeanProvider(AdmissionResourceReadbackSource.class), adapters.getBeanProvider(AdmissionNoticeDeliverySource.class),
            mock(TargetRetirementGateService.class));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("staff@example.test", PlatformRole.PLATFORM_ADMIN, "Staff", "SESSION"), null, List.of()));

        Budget budget = new Budget(capacity(4), vector(0), vector(0), "measurement-proof", now.minusSeconds(2), now.plusSeconds(3_600));
        reviewedPolicy = new FleetPolicy("bridge-policy", budget, Map.of(TARGET,
            new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, permission.profileFingerprint(), budget)),
            policy(4).lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        admissions.approvePolicy(reviewedPolicy);
        admissions.approveCustomerGrant(new CustomerGrant("customer", capacity(4), Map.of(TARGET, capacity(4)), now.plusSeconds(3_600),
            "platform-user:owner", "fixture-payer", null));
        Map<Prerequisite, String> customerReferences = Map.of(
            Prerequisite.APPROVED_POLICY, "policy:bridge-policy", Prerequisite.IMMUTABLE_ARTIFACTS, "deployment-version:version:config-hash",
            Prerequisite.STORAGE, "document-binding:" + json.fingerprint(binding), Prerequisite.IDENTITY, "binding:runtime-authority",
            Prerequisite.NOTIFICATIONS, "approved-mail", Prerequisite.OPERATING_POLICY, "published-lifecycle");
        var references = new HashSet<>(customerReferences.values());
        references.addAll(Set.of("bridge-topology", "restore-proof", "privacy-proof", "support-owner", "platform-user:owner", "fixture-payer"));
        admissions.approveManifest(new Manifest("bridge-manifest", "deployment1", "version", "config-hash", TARGET,
            "runtime-authority", "bridge-topology", capacity(1), references, customerReferences));
        customerContext = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment1", null, TARGET,
            TargetIntent.CUSTOMER_STAGING, "offer", "reviewed-offer", 1, placementEvidence.getId(), CustomerOfferContracts.Operation.APPLY,
            "bridge-apply", new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 1, 4, 1, 1, 1, 1, 1, 1, 24, 24, 24),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
        prerequisites = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(store, json, mapper, deployments, versions, targets,
            placement, new CustomerSourcePrerequisiteEvidenceStore(jdbc, json), mock(DeploymentTargetContextService.class), clock);
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void realProfileCompositionPlacementAndBothAdmissionValidatorsAgree() {
        Reservation held = reserve();
        var resolved = resolve();
        assertThat(placementEvidence.getProfileFingerprint()).isEqualTo(permission.profileFingerprint());
        assertThat(held.prerequisiteReadback().profileFingerprint()).isEqualTo(permission.profileFingerprint());
        assertThat(resolved.capacityReservationId()).isEqualTo(held.id());
        assertThat(resolved.passed()).containsExactlyInAnyOrder(Prerequisite.values());
    }

    @ParameterizedTest @ValueSource(strings = {"id", "provider", "environment", "sourceStrategy", "credentialReference",
        "providerConfig", "networkPolicy", "resourceDefaults", "inactive"})
    void identityOrActivationDriftCannotCrossAnyReviewedBoundary(String field) {
        Reservation held = reserve();
        switch (field) {
            case "id" -> profile.setId("other-target");
            case "provider" -> profile.setProviderType(DeploymentProviderType.RAILWAY_API);
            case "environment" -> profile.setEnvironmentName("production");
            case "sourceStrategy" -> profile.setSourceStrategy("GIT_SOURCE");
            case "credentialReference" -> profile.setCredentialRefId("changed-credential");
            case "providerConfig" -> profile.setProviderConfigJson(profile.getProviderConfigJson().replace("\"generation\":1", "\"generation\":2"));
            case "networkPolicy" -> profile.setNetworkPolicyJson("{\"cookies\":true}");
            case "resourceDefaults" -> profile.setResourceDefaultsJson("{\"limits\":{\"cpuMillis\":2}}");
            case "inactive" -> profile.setActive(false);
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(() -> composition.requireProfile(profile, permission)).isInstanceOf(CustomerSelfServiceException.class);
        assertThatThrownBy(() -> placement.requireEvidenceById(profile, TargetIntent.CUSTOMER_STAGING, placementEvidence.getId()))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessage("PLACEMENT_DENIED");
        assertThatThrownBy(() -> admissions.approvePolicy(reviewedPolicy)).isInstanceOf(AdmissionFailure.class)
            .satisfies(error -> assertThat(((AdmissionFailure) error).getReason()).isIn("TARGET_PROFILE_CHANGED", "TARGET_NOT_ADMISSIBLE"));
        assertThatThrownBy(this::resolve).isInstanceOf(AdmissionFailure.class)
            .satisfies(error -> assertThat(((AdmissionFailure) error).getReason()).isIn("CUSTOMER_TARGET_PROFILE_CHANGED", "CUSTOMER_TARGET_NOT_ADMISSIBLE"));
        assertThat(store.require(held.id())).isEqualTo(held);
    }

    @Test void environmentOmittingLegacyOfferHashIsRejectedRatherThanAcceptedAsAnAlternative() throws Exception {
        var codec = new CustomerOfferCodec(mapper);
        String legacy = codec.hash(Map.of("id", profile.getId(), "providerType", profile.getProviderType(),
            "sourceStrategy", profile.getSourceStrategy(), "credentialRefId", profile.getCredentialRefId(),
            "providerConfig", mapper.readTree(profile.getProviderConfigJson()), "networkPolicy", mapper.readTree(profile.getNetworkPolicyJson()),
            "resourceDefaults", mapper.readTree(profile.getResourceDefaultsJson())));
        assertThat(legacy).isNotEqualTo(permission.profileFingerprint());
        assertThatThrownBy(() -> composition.requireProfile(profile, new TargetPermission(TARGET, TargetIntent.CUSTOMER_STAGING, legacy)))
            .hasMessage("REVIEWED_COMPOSITION_CHANGED");
        var original = reviewedPolicy.targets().get(TARGET);
        var legacyPolicy = new FleetPolicy("legacy-policy", reviewedPolicy.fleet(), Map.of(TARGET,
            new TargetBudget(TARGET, original.intent(), original.placement(), legacy, original.budget())), reviewedPolicy.lifecycle(),
            reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference());
        assertThatThrownBy(() -> admissions.approvePolicy(legacyPolicy)).hasMessageContaining("TARGET_PROFILE_CHANGED");
    }

    @Test void policyDriftInvalidatesTheExistingHeldCustomerProof() {
        reserve();
        admissions.approvePolicy(new FleetPolicy("new-policy", reviewedPolicy.fleet(), reviewedPolicy.targets(), reviewedPolicy.lifecycle(),
            reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference()));
        assertThatThrownBy(this::resolve).hasMessageContaining("CUSTOMER_ADMISSION_FINGERPRINT_CHANGED");
    }

    @Test void matchingIdentityDoesNotAuthorizeTheWrongPhysicalStagingServer() throws Exception {
        var production = new Placement("kvufjk78dj4wyhjgp1mlxecr", "r3thf2xmxcjn1tt2bclabebz", "46.225.162.106");
        profile.setProviderConfigJson(mapper.writeValueAsString(Map.of("serverUuid", production.serverUuid(), "destinationUuid", production.destinationUuid())));
        String hash = composition.profileFingerprint(profile);
        composition.requireProfile(profile, new TargetPermission(TARGET, TargetIntent.CUSTOMER_STAGING, hash));
        when(config.serverUuid()).thenReturn(production.serverUuid());
        assertThatThrownBy(() -> placement.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING)).hasMessage("PLACEMENT_DENIED");
        var forged = new FleetPolicy("wrong-physical-host", reviewedPolicy.fleet(), Map.of(TARGET,
            new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, production, hash, reviewedPolicy.fleet())), reviewedPolicy.lifecycle(),
            reviewedPolicy.restoreEvidenceReference(), reviewedPolicy.privacyEvidenceReference(), reviewedPolicy.supportReference());
        assertThatThrownBy(() -> admissions.approvePolicy(forged)).hasMessageContaining("PHYSICAL_PLACEMENT_FORBIDDEN");
    }

    private Reservation reserve() {
        return admissions.reserveCustomer("deployment1", "version", new Reserve("bridge-apply", "bridge-manifest", customerContext));
    }

    private CustomerOfferContracts.PrerequisiteEvidence resolve() {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(ignored -> prerequisites.resolve(customerContext));
    }

    private Vector capacity(long units) {
        return with(with(vector(units), Dimension.MEMORY_BYTES, units * 1024L * 1024L), Dimension.DISK_BYTES, units * 1024L * 1024L);
    }
}
