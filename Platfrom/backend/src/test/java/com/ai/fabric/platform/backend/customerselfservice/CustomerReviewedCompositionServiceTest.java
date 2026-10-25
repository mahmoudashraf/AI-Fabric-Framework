package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginInstallFieldSummary;
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
    private MarketplaceManifestService.ParsedMarketplaceManifest templateManifest;

    @BeforeEach void setup() {
        service = new CustomerReviewedCompositionService(codec, validatorFactory.getValidator(), plugins, versions, manifests, artifacts, profiles,
            mock(CustomerWorkspaceProfileValidator.class), new com.fasterxml.jackson.databind.ObjectMapper(),
            new com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy(artifacts,
                new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(
                    new com.fasterxml.jackson.databind.ObjectMapper())),
            new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy(plugins, versions, manifests, codec,
                new com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationEndpointPolicy()));
        var template = new MarketplacePluginEntity(); template.setId("template-plugin"); template.setStatus("ACTIVE"); template.setPluginType("TEMPLATE");
        version = new MarketplacePluginVersionEntity(); version.setId("template-v1"); version.setPluginId(template.getId());
        version.setVersion("1.0.0"); version.setStatus("PUBLISHED");
        version.setManifestJson("{\"contributions\":{\"template\":{\"templateId\":\"reviewed-template\"}}}");
        when(plugins.findById(template.getId())).thenReturn(Optional.of(template));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        templateManifest = mock(MarketplaceManifestService.ParsedMarketplaceManifest.class, RETURNS_DEEP_STUBS);
        when(templateManifest.manifest()).thenReturn(codec.tree(version.getManifestJson()));
        when(templateManifest.contributions().templateRequiredPluginRefs()).thenReturn(List.of());
        when(manifests.parseAndValidate(template, version)).thenReturn(templateManifest);
        artifact = new DeploymentSourceArtifactEntity(); artifact.setId("runtime-artifact"); artifact.setServiceName("runtime");
        artifact.setArtifactRole("RUNTIME");
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
            base.operations(), base.settings(), base.bindingSlots(), base.documentAudiences(), base.admissionPrerequisites(), base.limits(), base.topology(),
            base.approvedPolicyRef(), base.operatingPolicyRef(), base.identityPolicyRef(), base.storagePolicyRef());
    }

    @AfterEach void close() { validatorFactory.close(); }

    @Test void explicitReviewedPublishedImmutableCompositionPasses() { service.validate(definition); }

    @Test void optionalPrerequisitesMayBeAbsentButCorePolicyAndImmutableArtifactsRemainMandatory() {
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(codec.write(definition));
        tree.putArray("admissionPrerequisites").add("APPROVED_POLICY").add("IMMUTABLE_ARTIFACTS");
        definition = codec.read(codec.write(tree), Definition.class);
        service.validate(definition);

        for (String retained : List.of("APPROVED_POLICY", "IMMUTABLE_ARTIFACTS")) {
            tree.putArray("admissionPrerequisites").add(retained);
            var incomplete = codec.read(codec.write(tree), Definition.class);
            assertThatThrownBy(() -> service.validate(incomplete)).isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(INVALID_CONTRACT));
        }
        artifact.setImageDigest(null);
        assertCode(ARTIFACT_REQUIRED);
    }

    @Test void stagingOnlyWithoutPromotionPassesWithoutInferringProductionPermissionOrArtifactPromotion() {
        definition = copyWithTargetsAndOperations(List.of(definition.targets().getFirst()), withoutPromotionOperations());
        artifact.setPromotionChannel("staging");
        String fingerprint = codec.hash(definition);

        service.validate(definition);

        assertThat(definition.targets()).extracting(TargetPermission::intent).containsExactly(TargetIntent.CUSTOMER_STAGING);
        assertThat(definition.operations()).doesNotContain(Operation.PROMOTION_PLAN, Operation.PROMOTION_APPLY);
        assertThat(codec.hash(definition)).isEqualTo(fingerprint);
        assertThat(artifact.getPromotionChannel()).isEqualTo("staging");
        verify(profiles, never()).findById("production-target");
    }

    @Test void stagingOnlyWithPromotionPlanIsInvalid() {
        var operations = withoutPromotionOperations();
        operations.add(Operation.PROMOTION_PLAN);
        definition = copyWithTargetsAndOperations(List.of(definition.targets().getFirst()), operations);
        assertCode(INVALID_CONTRACT);
    }

    @Test void stagingOnlyWithPromotionApplyIsInvalid() {
        var operations = withoutPromotionOperations();
        operations.add(Operation.PROMOTION_APPLY);
        definition = copyWithTargetsAndOperations(List.of(definition.targets().getFirst()), operations);
        assertCode(INVALID_CONTRACT);
    }

    @Test void productionOnlyWithoutPromotionIsInvalid() {
        definition = copyWithTargetsAndOperations(List.of(definition.targets().get(1)), withoutPromotionOperations());
        assertCode(INVALID_CONTRACT);
    }

    @Test void dualTargetWithoutPromotionStillPasses() {
        definition = copyWithTargetsAndOperations(definition.targets(), withoutPromotionOperations());
        service.validate(definition);
    }

    @Test void immutableBusinessArtifactCannotBePublishedAsACustomerAiDependency() throws Exception {
        var dependency = com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures.ownedAiArtifact(
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures.http("inventory-service", true));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(dependency.getCapabilityManifestJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) candidate.path("managedDependencyContract"))
            .put("purpose", "CUSTOMER_BUSINESS_SYSTEM");
        var normalized = new com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService(mapper)
            .normalize(candidate);
        dependency.setCapabilityManifestJson(normalized.manifest().toString());
        dependency.setCapabilityManifestHash(normalized.hash());
        when(artifacts.findById(dependency.getId())).thenReturn(Optional.of(dependency));
        var definitionTree = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(codec.write(definition));
        ((com.fasterxml.jackson.databind.node.ArrayNode) definitionTree.path("artifacts"))
            .add(mapper.valueToTree(new ArtifactPin(dependency.getId(), service.artifactFingerprint(dependency))));
        definition = codec.read(definitionTree.toString(), Definition.class);
        assertCode(APPROVED_POLICY_REQUIRED);
    }

    @Test void publishedCandidateMayChangeTemplatePluginsAndProviderConfiguration() {
        var deploymentVersion = new DeploymentVersionEntity();
        deploymentVersion.setId("version-v2"); deploymentVersion.setDeploymentId("owned-deployment");
        deploymentVersion.setStatus("PUBLISHED");
        deploymentVersion.setConfigHash("new-published-config-hash");
        deploymentVersion.setActionsConfigJson("{}"); deploymentVersion.setRoutingConfigJson("{}");
        deploymentVersion.setProviderConfigJson("{\"generation\":{\"model\":\"new-model\"}}");
        deploymentVersion.setSecurityConfigJson("{}"); deploymentVersion.setPromptConfigJson("{}");
        deploymentVersion.setCompositionProvenanceJson(codec.write(Map.of("templateId", "changed-template",
            "marketplaceInstalls", List.of(Map.of("pluginId", "new-plugin", "pluginVersionId", "new-plugin-v2",
                "manifestSha256", "sha256:" + "b".repeat(64))))));
        sealManifest(deploymentVersion);
        service.requireVersion(definition, deploymentVersion);
        service.requireVersion(null, deploymentVersion);

        deploymentVersion.setProviderConfigJson("{\"generation\":{\"model\":\"tampered-model\"}}");
        service.requireVersion(null, deploymentVersion);
        sealManifest(deploymentVersion);
        deploymentVersion.setCompositionProvenanceJson("{}");
        assertVersionDenied(deploymentVersion);
        sealManifest(deploymentVersion);
        service.requireVersion(null, deploymentVersion);
        deploymentVersion.setId("foreign-manifest-version");
        assertVersionDenied(deploymentVersion);
        deploymentVersion.setId("version-v2");
        deploymentVersion.setDeploymentId("foreign-manifest-deployment");
        assertVersionDenied(deploymentVersion);
        deploymentVersion.setDeploymentId("owned-deployment");
        deploymentVersion.setConfigHash(null);
        assertVersionDenied(deploymentVersion);
        deploymentVersion.setConfigHash("new-published-config-hash");
        deploymentVersion.setManifestJson("not-json");
        assertVersionDenied(deploymentVersion);
        sealManifest(deploymentVersion);
        deploymentVersion.setCompositionProvenanceJson("{}");
        deploymentVersion.setStatus("DRAFT");
        assertVersionDenied(deploymentVersion);
    }

    @Test void acceptsRealCompilerDerivedRoutingWithoutComparingItToRawDraftRouting() throws Exception {
        var deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setName("Compiler fixture"); deployment.setEnvironmentName("staging");
        deployment.setTemplateId("independent-template"); deployment.setBehaviorType("CONVERSATIONAL");
        var draft = new DeploymentDraftEntity();
        draft.setActionsConfigJson("""
            {"actions":[{"name":"lookup_record","route":{"method":"GET","url":"https://records.example.test/records"}}]}
            """);
        draft.setEntityConfigJson("{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}");
        draft.setRoutingConfigJson("{}"); draft.setProviderConfigJson("{\"llmProvider\":\"openai\",\"vectorStrategy\":\"lucene\"}");
        draft.setSecurityConfigJson("{\"connectorApiKeyEnabled\":true}"); draft.setPromptConfigJson("{}");
        draft.setKnowledgeSourceConfigJson("{}"); draft.setShellConfigJson("{}");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var compiler = new com.ai.fabric.platform.backend.deployment.service.DeploymentConfigCompiler(mapper);
        var compiled = compiler.compile(deployment, draft, "compiled-version", "v2", false);
        var candidate = new DeploymentVersionEntity(); candidate.setId("compiled-version"); candidate.setDeploymentId("deployment");
        candidate.setStatus("PUBLISHED"); candidate.setConfigHash(compiled.configHash());
        candidate.setAiFabricFrameworkVersion(compiler.frameworkVersion());
        candidate.setEntityConfigJson(draft.getEntityConfigJson()); candidate.setProviderConfigJson(draft.getProviderConfigJson());
        candidate.setRoutingConfigJson(draft.getRoutingConfigJson()); candidate.setBehaviorConfigJson(draft.getBehaviorConfigJson());
        candidate.setEntityArtifactYaml(compiled.entityArtifactYaml()); candidate.setManifestJson(compiled.manifestJson());
        candidate.setCompositionProvenanceJson(compiled.compositionProvenanceJson());
        var manifest = mapper.readTree(compiled.manifestJson());
        assertThat(manifest.path("routingConfig")).isNotEqualTo(mapper.readTree(candidate.getRoutingConfigJson()));
        assertThat(manifest.path("routingConfig").path("actions").path("lookup_record").path("url").asText())
            .isEqualTo("https://records.example.test/records");
        compiler.requireRuntimeArtifactCompatible(candidate);
        service.requireVersion(null, candidate);
    }

    private void sealManifest(DeploymentVersionEntity version) {
        var manifest = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
            .put("deploymentId", version.getDeploymentId()).put("versionId", version.getId())
            .put("aiFabricFrameworkVersion", version.getAiFabricFrameworkVersion())
            .put("entityConfigContractVersion", version.getEntityConfigContractVersion());
        manifest.set("actionsConfig", codec.tree(version.getActionsConfigJson()));
        manifest.set("routingConfig", codec.tree(version.getRoutingConfigJson()));
        manifest.set("providerConfig", codec.tree(version.getProviderConfigJson()));
        manifest.set("securityConfig", codec.tree(version.getSecurityConfigJson()));
        manifest.set("promptConfig", codec.tree(version.getPromptConfigJson()));
        manifest.set("knowledgeSourceConfig", codec.tree(version.getKnowledgeSourceConfigJson()));
        manifest.set("shellConfig", codec.tree(version.getShellConfigJson()));
        manifest.set("marketplaceDatasetConfig", codec.tree(version.getMarketplaceDatasetConfigJson()));
        manifest.set("behaviorConfig", codec.tree(version.getBehaviorConfigJson()));
        manifest.set("compositionProvenance", codec.tree(version.getCompositionProvenanceJson()));
        version.setManifestJson(codec.write(manifest));
    }

    private void assertVersionDenied(DeploymentVersionEntity deploymentVersion) {
        assertThatThrownBy(() -> service.requireVersion(definition, deploymentVersion))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(REVIEWED_COMPOSITION_CHANGED));
    }

    @Test void resolvesOnlyTheSoleReviewedRuntimeArtifactAndRejectsRoleDrift() {
        assertThat(service.runtimeArtifactId(definition)).isEqualTo("runtime-artifact");

        artifact.setArtifactRole("CONNECTOR");
        assertThatThrownBy(() -> service.runtimeArtifactId(definition))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(REVIEWED_COMPOSITION_CHANGED));
    }

    @Test void artifactRoleIsPartOfTheReviewedFingerprint() {
        artifact.setArtifactRole("CONNECTOR");
        assertCode(REVIEWED_COMPOSITION_CHANGED);
    }

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

    @Test void reviewedTopologyIsMandatoryAndCannotExceedTheReviewedCeilings() {
        var oversized = copyWithTopology(new ReviewedTopology(1, definition.limits().cpuMillis() + 1,
            definition.topology().memoryMiB(), definition.topology().diskMiB(),
            definition.topology().databases(), definition.topology().indexes(),
            definition.topology().dependencies(), definition.topology().concurrentApplies()));
        assertThatThrownBy(() -> service.validate(oversized)).isInstanceOf(CustomerSelfServiceException.class);

        var json = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(codec.write(definition));
        json.remove("topology");
        var missing = codec.read(json.toString(), Definition.class);
        assertThatThrownBy(() -> service.validate(missing)).isInstanceOf(CustomerSelfServiceException.class);
    }

    @Test void hashesSurviveStructuredFieldOrderingAndSerializationRestart() {
        assertThat(codec.hash(codec.tree("{\"b\":1,\"a\":2}"))).isEqualTo(codec.hash(codec.tree("{\"a\":2,\"b\":1}")));
        var freshCodec = CustomerOfferTestFixtures.codec();
        assertThat(freshCodec.hash(freshCodec.read(codec.write(definition), Definition.class))).isEqualTo(codec.hash(definition));
        assertThat(CustomerPermissionMatrix.rows(EnumSet.allOf(Operation.class))).hasSize(Operation.values().length);
    }

    @Test void reviewedBindingMayTargetOnlyATextConfigFieldOnAnExactlyPinnedPlugin() {
        var knowledge = new MarketplacePluginEntity();
        knowledge.setId("knowledge-plugin");
        knowledge.setStatus("ACTIVE");
        knowledge.setPluginType("DATA");
        var knowledgeVersion = new MarketplacePluginVersionEntity();
        knowledgeVersion.setId("knowledge-v1");
        knowledgeVersion.setPluginId(knowledge.getId());
        knowledgeVersion.setVersion("1.0.0");
        knowledgeVersion.setStatus("PUBLISHED");
        knowledgeVersion.setManifestJson("{\"plugin\":\"knowledge\"}");
        when(plugins.findById(knowledge.getId())).thenReturn(Optional.of(knowledge));
        when(versions.findById(knowledgeVersion.getId())).thenReturn(Optional.of(knowledgeVersion));
        var knowledgeManifest = mock(MarketplaceManifestService.ParsedMarketplaceManifest.class, RETURNS_DEEP_STUBS);
        when(knowledgeManifest.installForm()).thenReturn(List.of(new MarketplacePluginInstallFieldSummary(
            "documentStorageBindingRef", "Storage binding", "text", true, "Reviewed binding", List.of()
        )));
        when(manifests.parseAndValidate(knowledge, knowledgeVersion)).thenReturn(knowledgeManifest);
        when(templateManifest.contributions().templateRequiredPluginRefs())
            .thenReturn(List.of("knowledge-plugin@1.0.0"));

        PluginPin pin = new PluginPin(knowledge.getId(), knowledgeVersion.getId(),
            codec.hash(codec.tree(knowledgeVersion.getManifestJson())));
        definition = copyWithPluginBinding(pin, "documentStorageBindingRef");
        service.validate(definition);

        definition = copyWithPluginBinding(pin, "unreviewedField");
        assertThatThrownBy(() -> service.validate(definition)).isInstanceOf(CustomerSelfServiceException.class);
    }

    private Definition copyWithPluginBinding(PluginPin pin, String field) {
        return new Definition(definition.schemaVersion(), definition.name(), definition.templateId(),
            definition.template(), List.of(pin), definition.artifacts(), definition.targets(), definition.operations(),
            definition.settings(), List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3,
                Set.of("reviewed-s3"), pin.pluginId(), field)), definition.documentAudiences(),
            definition.admissionPrerequisites(), definition.limits(), definition.topology(), definition.approvedPolicyRef(),
            definition.operatingPolicyRef(), definition.identityPolicyRef(), definition.storagePolicyRef());
    }

    private Definition copyWithTopology(ReviewedTopology topology) {
        return new Definition(definition.schemaVersion(), definition.name(), definition.templateId(),
            definition.template(), definition.plugins(), definition.artifacts(), definition.targets(), definition.operations(),
            definition.settings(), definition.bindingSlots(), definition.documentAudiences(),
            definition.admissionPrerequisites(), definition.limits(), topology, definition.approvedPolicyRef(),
            definition.operatingPolicyRef(), definition.identityPolicyRef(), definition.storagePolicyRef());
    }

    private EnumSet<Operation> withoutPromotionOperations() {
        var operations = EnumSet.copyOf(definition.operations());
        operations.removeAll(Set.of(Operation.PROMOTION_PLAN, Operation.PROMOTION_APPLY));
        return operations;
    }

    private Definition copyWithTargetsAndOperations(List<TargetPermission> targets, Set<Operation> operations) {
        return new Definition(definition.schemaVersion(), definition.name(), definition.templateId(),
            definition.template(), definition.plugins(), definition.artifacts(), targets, operations,
            definition.settings(), definition.bindingSlots(), definition.documentAudiences(),
            definition.admissionPrerequisites(), definition.limits(), definition.topology(), definition.approvedPolicyRef(),
            definition.operatingPolicyRef(), definition.identityPolicyRef(), definition.storagePolicyRef(),
            definition.workspaceProfiles(), definition.aiStateStorageTopologies(), definition.externalIntegrationProfiles());
    }

    private void assertCode(CustomerSelfServiceException.Code code) {
        assertThatThrownBy(() -> service.validate(definition)).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
