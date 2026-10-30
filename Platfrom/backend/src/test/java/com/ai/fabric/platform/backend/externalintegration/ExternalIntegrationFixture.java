package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.authority.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationFixture {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    final CustomerOfferEntitlementRepository ownerRepository = mock(CustomerOfferEntitlementRepository.class);
    final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    final ExternalIntegrationOwnerEligibility owners = new ExternalIntegrationOwnerEligibility(ownerRepository, entitlements, tenants);
    final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    final MarketplacePluginRepository plugins = mock(MarketplacePluginRepository.class);
    final MarketplacePluginVersionRepository pluginVersions = mock(MarketplacePluginVersionRepository.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final ExternalIntegrationStore store = mock(ExternalIntegrationStore.class);
    final PlatformSecretService vault = mock(PlatformSecretService.class);
    final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    final PlatformAuditService audit = mock(PlatformAuditService.class);
    final AtomicInteger dnsCalls = new AtomicInteger();
    final ExternalIntegrationEndpointPolicy endpoints = new ExternalIntegrationEndpointPolicy(host -> {
        dnsCalls.incrementAndGet(); return new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})};
    });
    final ExternalIntegrationPolicy policy = new ExternalIntegrationPolicy(plugins, pluginVersions,
        new MarketplaceManifestService(mapper), codec, endpoints);
    final ExternalIntegrationCompilerResolver compiler = new ExternalIntegrationCompilerResolver(mapper);
    final ExternalIntegrationIntegrity integrity = new ExternalIntegrationIntegrity(codec, vault);
    final Map<String, ExternalIntegrationStore.Row<Approval>> approvals = new HashMap<>();
    final Map<String, ExternalIntegrationStore.Row<Binding>> bindings = new HashMap<>();
    final Map<String, ExternalIntegrationStore.Row<ReleaseSnapshot>> snapshots = new HashMap<>();
    final Map<String, CustomerEntitlementCommandEntity> commandRows = new HashMap<>();
    final Map<String, ExternalIntegrationStore.SecretEvidence> secretRows = new HashMap<>();
    final DeploymentEntity deployment = new DeploymentEntity();
    final DeploymentVersionEntity version = new DeploymentVersionEntity();
    final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    final CustomerOfferEntitlementEntity grant = new CustomerOfferEntitlementEntity();
    final Profile profile;
    final Definition definition;
    final ExternalIntegrationService service;
    final ExternalIntegrationResolver resolver;

    ExternalIntegrationFixture() throws Exception {
        when(vault.resolveSecret("PLATFORM_ARTIFACT_SIGNING_KEY")).thenReturn("synthetic-integration-signing-key");
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId("generic-action"); plugin.setPluginType("ACTION"); plugin.setStatus("ACTIVE"); plugin.setDisplayName("Generic business action");
        MarketplacePluginVersionEntity pluginVersion = new MarketplacePluginVersionEntity();
        pluginVersion.setId("generic-action-version"); pluginVersion.setPluginId(plugin.getId()); pluginVersion.setVersion("1.0.0"); pluginVersion.setStatus("PUBLISHED");
        pluginVersion.setManifestJson("""
            {"schemaVersion":1,"pluginId":"generic-action","version":"1.0.0","pluginType":"ACTION","displayName":"Generic action",
              "compatibility":{"requiredCapabilities":["actions"],"supportedDeploymentTargets":["dev-openai-milvus"]},
              "pricing":{"pricingModel":"FREE"},"permissions":{"contributesActions":true,"requiresExternalHttpExecution":true,"requiresDeploymentSecrets":true},
              "installForm":[{"id":"endpointUrl","label":"External endpoint","type":"url","required":true},{"id":"apiKey","label":"Key","type":"secretRef","required":true}],
              "contributions":{"actions":[{"actionId":"submit_request","adapterType":"connector-http","description":"Submit confirmed request",
                "readOnly":false,"anonymousAllowed":false,"requiresConfirmation":true,"params":[],
                "route":{"managedUrlField":"endpointUrl","managedHeaderSecretRefFields":{"X-API-Key":"apiKey"},"method":"POST",
                  "request":{"body":{"actionId":"{{actionId}}"}},"response":{"successHttpStatus":[200],"result":{"receipt":"{{body.data}}"}},"authz":{"enabled":false}}}]}}
            """);
        when(plugins.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        when(pluginVersions.findById(pluginVersion.getId())).thenReturn(Optional.of(pluginVersion));
        profile = new Profile(PROFILE_SCHEMA, "generic-profile", "business-actions", plugin.getId(), pluginVersion.getId(),
            codec.hash(codec.tree(pluginVersion.getManifestJson())), Set.of("target-stage", "target-production"), Set.of("endpointUrl"),
            Set.of("apiKey"), Set.of("endpointUrl"), Set.of(), Set.of("provider.example.com"));
        target.setId("target-stage"); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging"); target.setSourceStrategy("IMAGE_SOURCE");
        target.setProviderConfigJson("{}"); target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        var definitionTree = mapper.createObjectNode(); definitionTree.put("schemaVersion", "1"); definitionTree.put("name", "Generic reviewed composition");
        definitionTree.put("templateId", "dev-openai-milvus");
        definitionTree.set("template", mapper.valueToTree(new PluginPin("template", "template-version", "a".repeat(64))));
        definitionTree.set("plugins", mapper.valueToTree(List.of(new PluginPin(profile.pluginId(), profile.pluginVersionId(), profile.manifestSha256()))));
        definitionTree.putArray("artifacts"); definitionTree.set("targets", mapper.valueToTree(List.of(
            new TargetPermission("target-stage", TargetIntent.CUSTOMER_STAGING, DeploymentTargetProfileIdentity.fingerprint(mapper, target)),
            new TargetPermission("target-production", TargetIntent.CUSTOMER_PRODUCTION, "production-fingerprint"))));
        definitionTree.set("operations", mapper.valueToTree(Set.of(Operation.BINDING_REPLACE, Operation.BINDING_REVOKE, Operation.READ)));
        definitionTree.putArray("settings"); definitionTree.set("bindingSlots", mapper.valueToTree(List.of(
            new BindingSlot(profile.bindingSlotId(), BindingKind.valueOf("EXTERNAL_SERVICE"), Set.of(profile.id()), profile.pluginId(), null))));
        definitionTree.set("externalIntegrationProfiles", mapper.valueToTree(List.of(profile)));
        definitionTree.putArray("documentAudiences"); definitionTree.putArray("admissionPrerequisites");
        definitionTree.put("approvedPolicyRef", "review"); definitionTree.put("operatingPolicyRef", "review");
        definitionTree.put("identityPolicyRef", "review"); definitionTree.put("storagePolicyRef", "review");
        definition = mapper.treeToValue(definitionTree, Definition.class);
        deployment.setId("dep-generic"); deployment.setCustomerId("customer-generic"); deployment.setTenantId("tenant-generic"); deployment.setStatus("DRAFT");
        var tenant = new PlatformTenantEntity(); tenant.setId(deployment.getTenantId());
        tenant.setCustomerId(deployment.getCustomerId()); tenant.setStatus("ACTIVE");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        version.setId("version-generic"); version.setDeploymentId(deployment.getId()); version.setStatus("PUBLISHED"); version.setConfigHash("config-hash");
        var provenance = mapper.createObjectNode(); provenance.putArray("externalIntegrationProfiles").add(compiler.compilerEvidence(profile));
        provenance.putArray("marketplaceInstalls").addObject().put("pluginId", profile.pluginId())
            .put("pluginVersionId", profile.pluginVersionId()).put("pluginType", "ACTION")
            .put("manifestSha256", "sha256:" + profile.manifestSha256());
        version.setCompositionProvenanceJson(provenance.toString());
        var installed = new DeploymentMarketplacePluginInstallEntity(); installed.setId("install");
        installed.setDeploymentId(deployment.getId()); installed.setPluginId(profile.pluginId());
        installed.setPluginVersionId(profile.pluginVersionId()); installed.setStatus("ENABLED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(List.of(installed));
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(targets.findById(target.getId())).thenReturn(Optional.of(target));
        var offer = new CustomerOfferEntity("offer-generic", codec.write(definition), "offer-fingerprint", "review", "staff", Instant.now());
        when(entitlements.requireOffer(offer.getId())).thenReturn(offer); when(entitlements.definition(offer)).thenReturn(definition);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor(deployment.getCustomerId(), "owner"));
        grant.setId("entitlement-generic"); grant.setRevision(3); grant.setOwnerUserId("owner");
        grant.setCustomerId(deployment.getCustomerId()); grant.setOfferId(offer.getId());
        when(ownerRepository.findByCustomerIdAndOfferId(deployment.getCustomerId(), offer.getId())).thenReturn(Optional.of(grant));
        when(ownerRepository.findForUpdate(deployment.getCustomerId(), offer.getId())).thenReturn(Optional.of(grant));
        when(entitlements.requireEntitlement(anyString(), anyString(), nullable(String.class), anyBoolean())).thenReturn(grant);
        when(authorization.authorize(eq(deployment.getCustomerId()), any())).thenReturn(new Authorization(null, definition));
        when(associations.findById(deployment.getId())).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(deployment.getId(),
            deployment.getCustomerId(), deployment.getTenantId(), offer.getId(), target.getId(), offer.getFingerprint(), 3, "bootstrap", Instant.now())));
        when(store.approval(anyString())).thenAnswer(call -> Optional.ofNullable(approvals.get(call.getArgument(0))));
        when(store.approvalReplay(anyString(), anyString())).thenAnswer(call -> approvals.values().stream()
            .filter(row -> row.value().scope().deploymentId().equals(call.getArgument(0)) && row.value().operationKey().equals(call.getArgument(1))).findFirst());
        doAnswer(call -> { Approval approval = call.getArgument(0); approvals.put(approval.id(),
            new ExternalIntegrationStore.Row<>(approval, call.getArgument(1), "APPROVED", 0)); return null; }).when(store).insertApproval(any(), anyString());
        when(store.binding(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(bindings.get(
            key(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)))));
        when(store.latestBinding(anyString(), anyString(), anyString())).thenAnswer(call -> bindings.values().stream()
            .filter(row -> row.value().scope().deploymentId().equals(call.getArgument(0))
                && row.value().scope().targetProfileId().equals(call.getArgument(1))
                && row.value().scope().bindingSlotId().equals(call.getArgument(2))).reduce((first, second) -> second));
        doAnswer(call -> { Binding binding = call.getArgument(0); bindings.put(key(binding.scope()),
            new ExternalIntegrationStore.Row<>(binding, call.getArgument(1), "CONFIGURED", 0)); return null; }).when(store).insertBinding(any(), anyString());
        doAnswer(call -> { bindings.replaceAll((key, row) -> row.value().id().equals(call.getArgument(0))
            ? new ExternalIntegrationStore.Row<>(row.value(), call.getArgument(2), "REVOKED", row.revision() + 1) : row); return null;
        }).when(store).revokeBinding(anyString(), anyLong(), anyString());
        when(commands.findByEntitlementIdAndOperationKey(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(commandRows.get(call.getArgument(1))));
        when(commands.saveAndFlush(any())).thenAnswer(call -> { CustomerEntitlementCommandEntity value = call.getArgument(0); commandRows.put(value.getOperationKey(), value); return value; });
        when(vault.upsertTargetManagedSecret(anyString(), anyString(), eq(deployment.getId()), anyString(), anyMap())).thenAnswer(call -> {
            String name = call.getArgument(0); secretRows.put(name, new ExternalIntegrationStore.SecretEvidence(call.getArgument(2),
                "DEPLOYMENT_MANAGED", "PLATFORM_MANAGED", true, true, "2026-10-19T01:00:00.123456Z")); return null;
        });
        when(store.secretEvidence(anyString())).thenAnswer(call -> Optional.ofNullable(secretRows.get(call.getArgument(0))));
        when(store.snapshot(anyString())).thenAnswer(call -> Optional.ofNullable(snapshots.get(call.getArgument(0))));
        doAnswer(call -> { ReleaseSnapshot snapshot = call.getArgument(0); snapshots.put(snapshot.releaseId(),
            new ExternalIntegrationStore.Row<>(snapshot, call.getArgument(2), "FROZEN", 0)); return null; }).when(store).insertSnapshot(any(), any(), anyString());
        service = new ExternalIntegrationService(entitlements, authorization, composition, commands, deployments, versions, targets,
            policy, integrity, store, vault, secrets, codec, mapper, audit, Validation.buildDefaultValidatorFactory().getValidator(), owners);
        resolver = new ExternalIntegrationResolver(deployments, versions, targets, releases, associations, entitlements, composition,
            authority, store, policy, integrity, endpoints, compiler, codec, mapper, owners);
        customer();
    }
    ApprovalRequest approvalRequest() { return new ApprovalRequest(version.getId(), target.getId(), profile.bindingSlotId(), profile.id(),
        mapper.createObjectNode().put("endpointUrl", "https://provider.example.com/actions"), mapper.createObjectNode().put("externalAccount", "reviewed-account"), "review-ticket", "review-operation"); }
    Summary approve() { staff(); Summary result = service.approve("offer-generic", deployment.getId(), approvalRequest()); customer(); return result; }
    BindRequest bindRequest(String approvalId) { return new BindRequest(version.getId(), target.getId(), profile.bindingSlotId(), profile.id(), approvalId,
        3L, "bind-operation", mapper.createObjectNode().put("apiKey", "synthetic-customer-key")); }
    Summary bind() { return service.bind("offer-generic", deployment.getId(), bindRequest(approve().approvalId())); }
    RevokeRequest revokeRequest(String bindingId) { return new RevokeRequest(version.getId(), target.getId(), profile.bindingSlotId(), profile.id(), bindingId, 0L, 3L, "revoke-operation"); }
    void release() {
        var release = new DeploymentReleaseEntity(); release.setId("release-generic"); release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId(version.getId()); release.setTargetProfileId(target.getId());
        release.setRuntimeAuthoritySnapshotId("authority-snapshot"); release.setRuntimeAuthorityFingerprint("authority-fingerprint");
        when(releases.findById(release.getId())).thenReturn(Optional.of(release));
        var binding = new RuntimeAuthorityBinding(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), target.getId(), version.getId(),
            version.getConfigHash(), 1, "private", "private-audience", "public", "public-audience", true, true, List.of(), Map.of());
        when(authority.requireReleaseUnchanged(deployment.getId(), target.getId(), release.getId(), "authority-fingerprint"))
            .thenReturn(new RuntimeAuthorityBinding.Snapshot("authority-snapshot", release.getId(), "authority-fingerprint", binding));
        when(authority.requireReleaseUnchangedForProvisioning(deployment, version, release))
            .thenReturn(new RuntimeAuthorityBinding.Snapshot("authority-snapshot", release.getId(), "authority-fingerprint", binding));
    }
    void customer() { authenticate(PlatformRole.CUSTOMER_ADMIN, "owner"); }
    void staff() { authenticate(PlatformRole.PLATFORM_ADMIN, "reviewer"); }
    static void authenticate(PlatformRole role, String actor) { SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(new PlatformPrincipal(actor, role, actor, "TEST"), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }
    static String key(Scope scope) { return key(scope.deploymentId(), scope.versionId(), scope.targetProfileId(), scope.bindingSlotId()); }
    static String key(String... values) { return String.join("|", values); }
}
