package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerReviewedCompositionServiceTest {
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final MarketplacePluginRepository plugins = mock(MarketplacePluginRepository.class);
    private final MarketplacePluginVersionRepository versions = mock(MarketplacePluginVersionRepository.class);
    private final MarketplaceManifestService manifests = mock(MarketplaceManifestService.class);
    private final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final jakarta.validation.ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private CustomerReviewedCompositionService service;
    private Definition definition;
    private MarketplacePluginVersionEntity version;
    private DeploymentSourceArtifactEntity artifact;
    private DeploymentTargetProfileEntity staging;

    @BeforeEach void setup() {
        service = new CustomerReviewedCompositionService(codec, validatorFactory.getValidator(), plugins, versions, manifests, artifacts, profiles);
        var template = new MarketplacePluginEntity(); template.setId("template-plugin"); template.setStatus("ACTIVE"); template.setPluginType("TEMPLATE");
        version = new MarketplacePluginVersionEntity(); version.setId("template-v1"); version.setPluginId(template.getId());
        version.setVersion("1.0.0"); version.setStatus("PUBLISHED");
        version.setManifestJson("{\"contributions\":{\"template\":{\"templateId\":\"reviewed-template\"}}}");
        when(plugins.findById(template.getId())).thenReturn(Optional.of(template));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        var parsed = mock(MarketplaceManifestService.ParsedMarketplaceManifest.class, RETURNS_DEEP_STUBS);
        when(parsed.manifest()).thenReturn(codec.tree(version.getManifestJson()));
        when(parsed.contributions().templateRequiredPluginRefs()).thenReturn(List.of());
        when(manifests.parseAndValidate(template, version)).thenReturn(parsed);
        artifact = new DeploymentSourceArtifactEntity(); artifact.setId("runtime-artifact"); artifact.setServiceName("runtime");
        artifact.setArtifactType("DOCKER_IMAGE"); artifact.setImageRepository("PRIVATE_REPOSITORY");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setPromotedAt(Instant.now());
        artifact.setCapabilityManifestJson("{\"schemaVersion\":\"1\"}"); artifact.setCapabilityManifestHash("approved-manifest-hash");
        when(artifacts.findById(artifact.getId())).thenReturn(Optional.of(artifact));
        staging = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        var production = CustomerOfferTestFixtures.profile("production-target", "prod-server");
        when(profiles.findById(staging.getId())).thenReturn(Optional.of(staging));
        when(profiles.findById(production.getId())).thenReturn(Optional.of(production));
        var base = CustomerOfferTestFixtures.definition();
        definition = new Definition(base.schemaVersion(), base.name(), base.templateId(),
            new PluginPin(template.getId(), version.getId(), codec.hash(codec.tree(version.getManifestJson()))), List.of(),
            List.of(new ArtifactPin(artifact.getId(), service.artifactFingerprint(artifact))),
            List.of(new TargetPermission(staging.getId(), TargetIntent.CUSTOMER_STAGING, service.profileFingerprint(staging)),
                new TargetPermission(production.getId(), TargetIntent.CUSTOMER_PRODUCTION, service.profileFingerprint(production))),
            base.operations(), base.settings(), base.bindingSlots(), base.documentAudiences(), base.admissionPrerequisites(), base.limits(),
            base.approvedPolicyRef(), base.operatingPolicyRef(), base.identityPolicyRef(), base.storagePolicyRef());
    }

    @AfterEach void close() { validatorFactory.close(); }

    @Test void explicitReviewedPublishedImmutableCompositionPasses() { service.validate(definition); }

    @Test void changedManifestArtifactProfileOrUnpublishedVersionDenies() {
        version.setManifestJson("{\"different\":true}"); assertCode(REVIEWED_COMPOSITION_CHANGED);
        version.setManifestJson("{\"contributions\":{\"template\":{\"templateId\":\"reviewed-template\"}}}");
        artifact.setImageDigest("sha256:" + "b".repeat(64)); assertCode(REVIEWED_COMPOSITION_CHANGED);
        artifact.setImageDigest("sha256:" + "a".repeat(64)); staging.setCredentialRefId("OTHER_PROVIDER_CREDENTIAL"); assertCode(REVIEWED_COMPOSITION_CHANGED);
        staging.setCredentialRefId("PRIVATE_CREDENTIAL_REF"); version.setStatus("DRAFT"); assertCode(REVIEWED_COMPOSITION_CHANGED);
    }

    @Test void artifactWithoutDigestOrApprovalNeverBecomesReady() {
        artifact.setImageDigest(null); assertCode(ARTIFACT_REQUIRED);
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setPromotedAt(null); assertCode(ARTIFACT_REQUIRED);
    }

    @Test void mutableRepositorySourcePolicyIsDenied() {
        staging.setSourceStrategy("GIT_SOURCE"); assertCode(REVIEWED_COMPOSITION_CHANGED);
        staging.setSourceStrategy("IMAGE_SOURCE"); staging.setResourceDefaultsJson("{\"sourceStrategy\":\"GIT_SOURCE\"}"); assertCode(REVIEWED_COMPOSITION_CHANGED);
    }

    @Test void noOldSchemaUnknownFieldsOrUnboundedQuotaDefaults() {
        var json = codec.write(definition);
        assertThatThrownBy(() -> codec.read(json.substring(0, json.length() - 1) + ",\"repository\":\"arbitrary\"}", Definition.class))
            .isInstanceOf(CustomerSelfServiceException.class);
        var old = codec.read(json.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"0\""), Definition.class);
        assertThatThrownBy(() -> service.validate(old)).isInstanceOf(CustomerSelfServiceException.class);
        var missingLimits = codec.read(json.replace("\"activeStagingTargets\":1", "\"activeStagingTargets\":0"), Definition.class);
        assertThatThrownBy(() -> service.validate(missingLimits)).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void hashesSurviveStructuredFieldOrderingAndSerializationRestart() {
        assertThat(codec.hash(codec.tree("{\"b\":1,\"a\":2}"))).isEqualTo(codec.hash(codec.tree("{\"a\":2,\"b\":1}")));
        var freshCodec = CustomerOfferTestFixtures.codec();
        assertThat(freshCodec.hash(freshCodec.read(codec.write(definition), Definition.class))).isEqualTo(codec.hash(definition));
        assertThat(CustomerPermissionMatrix.rows(EnumSet.allOf(Operation.class))).hasSize(Operation.values().length);
    }

    private void assertCode(CustomerSelfServiceException.Code code) {
        assertThatThrownBy(() -> service.validate(definition)).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
