package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.provider.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceShopifyBillingConfigSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceShopifyBillingConfigSupport;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductProvisioningService {

    private static final String CUSTOMER_ACCOUNT_MCP_CLIENT_ID_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID";
    private static final String CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET";
    private static final String CUSTOMER_ACCOUNT_MCP_REDIRECT_URI_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI";
    private static final String CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN";
    private static final String CUSTOMER_ACCOUNT_MCP_SCOPES_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES";
    private static final String CUSTOMER_ACCOUNT_MCP_PROTECTED_APPROVED_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED";
    private static final String CUSTOMER_ACCOUNT_MCP_STATE_TTL_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL";
    private static final String CUSTOMER_ACCOUNT_MCP_SESSION_TTL_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL";
    private static final String CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT";
    private static final String CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT";

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformProductProvisioningProperties productProvisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final CoolifyTargetProfileResolver coolifyTargetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformManagedProductServiceService serviceService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final CustomerSelfServiceProperties customerSelfServiceProperties;
    private final CoolifyHostVolumeReclamationService hostVolumeReclamationService;

    private OwnedDependencyOwnership ownedDependencyOwnership;
    private PlatformManagedServiceOwnership sharedServiceOwnership;

    @Autowired
    void setSharedServiceOwnership(PlatformManagedServiceOwnership sharedServiceOwnership) {
        this.sharedServiceOwnership = sharedServiceOwnership;
    }

    @Autowired
    void setOwnedDependencyOwnership(OwnedDependencyOwnership ownedDependencyOwnership) {
        this.ownedDependencyOwnership = ownedDependencyOwnership;
    }

    @Autowired
    public PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                                     PlatformProductProvisioningProperties productProvisioningProperties,
                                                     PlatformDeliveryProperties deliveryProperties,
                                                     RailwayGraphqlClient railwayGraphqlClient,
                                                     CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                                     CoolifyApiClient coolifyApiClient,
                                                     DeploymentTargetProfileRepository targetProfileRepository,
                                                     PlatformSecretService platformSecretService,
                                                     PlatformManagedProductServiceRepository serviceRepository,
                                                     ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                                     PlatformManagedProductServiceService serviceService,
                                                     PlatformAuditService platformAuditService,
                                                     ObjectMapper objectMapper,
                                                     CustomerSelfServiceProperties customerSelfServiceProperties,
                                                     CoolifyHostVolumeReclamationService hostVolumeReclamationService) {
        this.provisioningProperties = provisioningProperties;
        this.productProvisioningProperties = productProvisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.coolifyTargetProfileResolver = coolifyTargetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.targetProfileRepository = targetProfileRepository;
        this.platformSecretService = platformSecretService;
        this.serviceRepository = serviceRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.serviceService = serviceService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.customerSelfServiceProperties = customerSelfServiceProperties;
        this.hostVolumeReclamationService = hostVolumeReclamationService;
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                             CoolifyApiClient coolifyApiClient,
                                             DeploymentTargetProfileRepository targetProfileRepository,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService, ObjectMapper objectMapper) {
        this(provisioningProperties, productProvisioningProperties, deliveryProperties, railwayGraphqlClient,
            coolifyTargetProfileResolver, coolifyApiClient, targetProfileRepository, platformSecretService,
            serviceRepository, shopifyStoreConnectionRepository, serviceService, platformAuditService, objectMapper,
            new CustomerSelfServiceProperties(Map.of(), 0),
            new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
                throw new IllegalStateException("Provider host verification is not configured for this test.");
            }));
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                             CoolifyApiClient coolifyApiClient,
                                             DeploymentTargetProfileRepository targetProfileRepository,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService, ObjectMapper objectMapper,
                                             CustomerSelfServiceProperties customerSelfServiceProperties) {
        this(provisioningProperties, productProvisioningProperties, deliveryProperties, railwayGraphqlClient,
            coolifyTargetProfileResolver, coolifyApiClient, targetProfileRepository, platformSecretService,
            serviceRepository, shopifyStoreConnectionRepository, serviceService, platformAuditService, objectMapper,
            customerSelfServiceProperties,
            new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
                throw new IllegalStateException("Provider host verification is not configured for this test.");
            }));
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService,
                                             ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            productProvisioningProperties,
            deliveryProperties,
            railwayGraphqlClient,
            null,
            null,
            null,
            platformSecretService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            serviceService,
            platformAuditService,
            objectMapper
        );
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary reconcile(String serviceRef) {
        return reconcile(serviceRef, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary reconcile(String serviceRef, String targetProfileId) {
        return reconcileCoolify(requireSharedCoolifyService(serviceRef), trimToNull(targetProfileId));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        requireSharedServiceMutationAuthority(serviceRef);
        serviceService.updateDesiredReplicas(serviceRef, desiredReplicas);
        return reconcile(serviceRef);
    }

    /** Credential writers must check this before changing either vault or registry state. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void requireSharedServiceMutationAuthority(String serviceRef) {
        PlatformManagedProductServiceEntity service = requireSharedCoolifyService(serviceRef);
        CoolifyBinding binding = resolveCoolifyBinding(service, null);
        var owner = sharedServiceOwnership.context(service, binding.profile(), binding.connection(), sharedServiceName(service));
        sharedServiceOwnership.observe(sharedServiceOwnership.existing(owner));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void requireSharedServiceCredentialMutationAuthority(String serviceRef, String expectedSecretName) {
        PlatformManagedProductServiceEntity service = requireSharedCoolifyService(serviceRef);
        if (!java.util.Objects.equals(expectedSecretName, service.getSecretName())) throw PlatformManagedServiceOwnershipJournal.denied();
        CoolifyBinding binding = resolveCoolifyBinding(service, null);
        var context = sharedServiceOwnership.context(service, binding.profile(), binding.connection(), sharedServiceName(service));
        var owned = sharedServiceOwnership.existing(context);
        sharedServiceOwnership.observe(owned);
        sharedServiceOwnership.requireCredentialMutation(owned, expectedSecretName);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary restart(String serviceRef) {
        PlatformManagedProductServiceEntity service = requireSharedCoolifyService(serviceRef);
        CoolifyBinding binding = resolveCoolifyBinding(service, null);
        var context = sharedServiceOwnership.context(service, binding.profile(), binding.connection(), sharedServiceName(service));
        var owned = sharedServiceOwnership.existing(context);
        SharedDeployment deployed = sharedServiceOwnership.mutate(owned, () -> {
            CoolifyActionResponse response = coolifyApiClient.restart(binding.connection(), owned.uuid());
            return new SharedDeployment(awaitSharedCoolifyDeployment(owned, response), response);
        });
        finalizeSharedCoolifyService(service, binding, owned, deployed, "MANAGED_PRODUCT_RESTARTED", "Coolify product service restarted successfully.");
        return serviceService.getService(serviceRef);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceEntity refreshRailwayBindingFromWorkspace(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (requiresRailwayLifecycle(service) || hasText(service.getRailwayProjectId())
            || hasText(service.getRailwayEnvironmentId()) || hasText(service.getRailwayServiceId())
            || "RAILWAY".equalsIgnoreCase(mutableDetails(service).path("providerType").asText(null))) {
            throw PlatformManagedServiceOwnershipJournal.denied();
        }
        return service;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary forceRecreate(String serviceRef) {
        PlatformManagedProductServiceEntity service = requireSharedCoolifyService(serviceRef);
        deleteSharedCoolifyService(service);
        platformAuditService.record("MANAGED_PRODUCT_FORCE_RECREATE_REQUESTED","MANAGED_PRODUCT_SERVICE",serviceRef,
            Map.of("serviceRef",serviceRef,"providerType","COOLIFY"));
        clearCoolifyBinding(service);
        return reconcileCoolify(service, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PlatformManagedProductServiceSummary decommission(String serviceRef) {
        PlatformManagedProductServiceEntity service = requireSharedCoolifyService(serviceRef);
        long dependentStores = shopifyStoreConnectionRepository.countByProductServiceId(service.getId());
        if (dependentStores > 0) {
            throw new ResponseStatusException(CONFLICT,
                "Managed product service still has " + dependentStores + " dependent store mapping(s).");
        }
        deleteSharedCoolifyService(service);
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("DECOMMISSIONED");
        ObjectNode details = mutableDetails(service);
        // Retain the exact deleted receipt/UUID as evidence, not live mutation authority.
        if (hasText(service.getSecretName())) details.put("credentialDisposition", "RETAINED_OWNERSHIP_UNPROVEN");
        details.put("sharedOwnershipState","DELETED");
        details.put("lastDecommissionedAt", Instant.now().toString());
        details.put("lastDecommissionStatus", "SUCCESS");
        details.put("lastDecommissionMessage", "Owned Coolify service deletion was verified.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        platformAuditService.record("MANAGED_PRODUCT_DECOMMISSIONED","MANAGED_PRODUCT_SERVICE",serviceRef,
            Map.of("serviceRef",serviceRef,"providerType","COOLIFY"));
        return serviceService.getService(serviceRef);
    }

    private PlatformManagedProductServiceEntity requireSharedCoolifyService(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (sharedServiceOwnership == null || !requiresCoolifyLifecycle(service)
            || hasText(service.getRailwayProjectId()) || hasText(service.getRailwayEnvironmentId())
            || hasText(service.getRailwayServiceId())) throw PlatformManagedServiceOwnershipJournal.denied();
        return service;
    }

    private PlatformManagedProductServiceSummary reconcileCoolify(PlatformManagedProductServiceEntity service, String targetOverride) {
        CoolifyBinding binding = resolveCoolifyBinding(service, targetOverride);
        var context = sharedServiceOwnership.context(service, binding.profile(), binding.connection(), sharedServiceName(service));
        boolean docker = PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(upper(service.getServiceKind()));
        var publicRequest = docker ? null : coolifyPublicApplicationRequest(service, binding, sharedServiceName(service));
        var dockerRequest = docker ? coolifyDockerImageApplicationRequest(service, binding, sharedServiceName(service)) : null;
        var owned = sharedServiceOwnership.reconcile(context, docker ? dockerRequest.domains() : publicRequest.domains(),
            () -> docker ? coolifyApiClient.createDockerImageApplication(binding.connection(), dockerRequest)
                : coolifyApiClient.createPublicApplication(binding.connection(), publicRequest));
        CoolifyApplicationSummary application = sharedServiceOwnership.observe(owned);
        if (docker && !hasText(application.imageRepository())) throw PlatformManagedServiceOwnershipJournal.denied();
        sharedServiceOwnership.mutate(owned, () -> {
            if (docker) {
                coolifyApiClient.updateDockerImageApplication(binding.connection(), owned.uuid(),
                    dockerRequest.withDomains(mergeCoolifyDomains(dockerRequest.domains(), application.fqdn())));
                requireSharedDockerImage(sharedServiceOwnership.observeDuringMutation(owned), dockerRequest);
            } else {
                coolifyApiClient.updatePublicApplication(binding.connection(), owned.uuid(),
                    publicRequest.withDomains(mergeCoolifyDomains(publicRequest.domains(), application.fqdn())));
            }
            return null;
        });
        sharedServiceOwnership.mutate(owned, () -> {
            ensureSharedServiceSecret(service, owned);
            coolifyApiClient.updateEnvironmentVariables(binding.connection(), owned.uuid(),
                buildCoolifyServiceEnv(service, publicBaseUrl(application)));
            return null;
        });
        SharedDeployment deployed = sharedServiceOwnership.mutate(owned, () -> {
            CoolifyActionResponse response = coolifyApiClient.start(binding.connection(), owned.uuid(), true, true);
            CoolifyApplicationSummary observed = awaitSharedCoolifyDeployment(owned, response);
            if (docker) requireSharedDockerImage(observed, dockerRequest);
            CoolifyApplicationCapacity.requireReadback(observed, coolifyManagedServiceRuntimeSettings(service),
                "MANAGED_PRODUCT_RUNTIME_CAPACITY_DRIFT");
            return new SharedDeployment(observed, response);
        });
        finalizeSharedCoolifyService(service, binding, owned, deployed, "MANAGED_PRODUCT_RECONCILED", "Coolify product service reconciled successfully.");
        return serviceService.getService(service.getServiceRef());
    }

    private void finalizeSharedCoolifyService(PlatformManagedProductServiceEntity service, CoolifyBinding binding,
            PlatformManagedServiceOwnership.Binding owned, SharedDeployment deployed, String action, String message) {
        sharedServiceOwnership.observe(owned);
        ObjectNode details = mutableDetails(service);
        details.put(PlatformManagedServiceOwnership.RECEIPT, owned.receipt().id());
        details.put("sharedOwnershipState","OWNED");
        if ("MANAGED_PRODUCT_RESTARTED".equals(action)) {
            details.put("lastRestartedAt",Instant.now().toString());
            details.put("lastRestartStatus","SUCCESS");
            details.put("lastRestartMessage",message);
        }
        if ("PENDING".equals(details.path("lastForceRecreateStatus").asText())) {
            details.put("lastForceRecreateStatus","SUCCESS");
            details.put("lastForceRecreateMessage","Verified owned replacement was created and deployed.");
        }
        service.setDetailsJson(details.toPrettyString());
        service.setEnvironmentScope(binding.profile().getEnvironmentName());
        finalizeActiveCoolifyService(service, binding.profile().getId(), binding.connection().config().projectUuid(),
            binding.connection().config().environmentUuid(), deployed.application(), deployed.response(), publicBaseUrl(deployed.application()), message);
        platformAuditService.record(action,"MANAGED_PRODUCT_SERVICE",service.getServiceRef(),
            Map.of("serviceRef",service.getServiceRef(),"providerType","COOLIFY","coolifyApplicationUuid",owned.uuid()));
    }

    private record SharedDeployment(CoolifyApplicationSummary application, CoolifyActionResponse response) { }

    private void ensureSharedServiceSecret(PlatformManagedProductServiceEntity service, PlatformManagedServiceOwnership.Binding owned) {
        if (PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(upper(service.getServiceKind()))) return;
        if (hasText(service.getSecretName())) {
            // A supplied reference permits reading a configured key, not creating/replacing it.
            if (!platformSecretService.isSecretPresent(service.getSecretName())) throw PlatformManagedServiceOwnershipJournal.denied();
            return;
        }
        String name = defaultSecretName(service.getServiceRef());
        if (platformSecretService.isSecretPresent(name)) throw PlatformManagedServiceOwnershipJournal.denied();
        sharedServiceOwnership.requireUnusedCredentialName(owned, name);
        platformSecretService.upsertManagedSecret(name, UUID.randomUUID().toString().replace("-", ""),
            Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET"));
        sharedServiceOwnership.recordGeneratedCredentialName(owned, name);
        service.setSecretName(name);
    }

    private CoolifyApplicationSummary awaitSharedCoolifyDeployment(PlatformManagedServiceOwnership.Binding owned,
                                                                  CoolifyActionResponse response) {
        String deploymentUuid = PlatformManagedServiceOwnershipJournal.required(response == null ? null : response.deploymentUuid(),128);
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        while (Instant.now().isBefore(deadline)) {
            CoolifyApplicationSummary observed = sharedServiceOwnership.observeDuringMutation(owned);
            CoolifyDeploymentSummary deployment = coolifyApiClient.getDeployment(owned.context().connection(), deploymentUuid)
                .orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
            if (!deploymentUuid.equals(deployment.deploymentUuid()) || !owned.uuid().equals(deployment.applicationUuid()))
                throw PlatformManagedServiceOwnershipJournal.denied();
            if (coolifyDeploymentFailed(deployment)) throw new ResponseStatusException(CONFLICT, "Coolify shared service deployment failed.");
            if (Set.of("FINISHED","SUCCESS","SUCCEEDED","COMPLETED").contains(upper(deployment.status()))
                && coolifyApplicationReady(observed)) return observed;
            sleepSharedPoll();
        }
        throw PlatformManagedServiceOwnershipJournal.denied();
    }

    private void deleteSharedCoolifyService(PlatformManagedProductServiceEntity service) {
        CoolifyBinding binding = resolveCoolifyBinding(service, null);
        var context = sharedServiceOwnership.context(service, binding.profile(), binding.connection(), sharedServiceName(service));
        var owned = sharedServiceOwnership.existing(context);
        var live = sharedServiceOwnership.observe(owned, true);
        if (live.isPresent()) {
            // Never resend an uncertain DELETE, even when the exact UUID remains visible.
            if (!"OWNED".equals(owned.receipt().state())) throw PlatformManagedServiceOwnershipJournal.denied();
            owned = sharedServiceOwnership.beginDeletion(owned);
            coolifyApiClient.delete(binding.connection(), owned.uuid(), true, false, false, false);
            Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
            while (sharedServiceOwnership.observe(owned, true).isPresent()) {
                if (!Instant.now().isBefore(deadline)) throw PlatformManagedServiceOwnershipJournal.denied();
                sleepSharedPoll();
            }
        }
        sharedServiceOwnership.deleted(owned);
    }

    private void sleepSharedPoll() {
        try {
            Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 1L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while verifying shared service lifecycle.", ex);
        }
    }

    private CoolifyCreateDockerImageApplicationRequest coolifyDockerImageApplicationRequest(
        PlatformManagedProductServiceEntity service,
        CoolifyBinding binding,
        String appName
    ) {
        CoolifyTargetProfileConfig config = binding.connection().config();
        ObjectNode details = mutableDetails(service);
        String imageRepository = requiredDetail(details, "imageRepository");
        String imageTag = requiredDetail(details, "imageTag");
        String imageDigest = requiredDetail(details, "imageDigest");
        String sourceCommit = requiredDetail(details, "sourceCommit");
        if (!imageRepository.matches("[A-Za-z0-9._/-]+") || !imageDigest.matches("sha256:[a-f0-9]{64}")
            || !sourceCommit.matches("[a-f0-9]{40}") || !sourceCommit.equals(imageTag)) {
            throw new ResponseStatusException(CONFLICT, "MANAGED_PRODUCT_IMAGE_RELEASE_INVALID");
        }
        String healthPath = firstNonBlank(healthPath(service), config.defaultHealthCheckPath());
        return new CoolifyCreateDockerImageApplicationRequest(
            config.projectUuid(),
            config.serverUuid(),
            config.environmentName(),
            config.environmentUuid(),
            imageRepository,
            "sha256-" + imageDigest.substring("sha256:".length()),
            firstNonBlank(config.defaultPortsExposes(), "8080"),
            config.destinationUuid(),
            appName,
            "Managed product service " + service.getServiceRef(),
            coolifyDomain(service, config),
            true,
            healthPath,
            firstNonBlank(config.defaultHealthCheckPort(), "8080"),
            coolifyManagedServiceRuntimeSettings(service),
            false,
            config.forceHttps(),
            config.autogenerateDomain()
        );
    }

    private void requireSharedDockerImage(CoolifyApplicationSummary observed,
                                         CoolifyCreateDockerImageApplicationRequest requested) {
        if (!requested.imageRepository().equals(observed.imageRepository())
            || !requested.imageTag().equals(observed.imageTag())) {
            throw new ResponseStatusException(CONFLICT, "MANAGED_PRODUCT_IMAGE_REFERENCE_DRIFT");
        }
    }

    private CoolifyApplicationRuntimeSettings coolifyManagedServiceRuntimeSettings(
        PlatformManagedProductServiceEntity service
    ) {
        if (!PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(
            upper(service.getServiceKind())
        )) {
            return null;
        }
        return new CoolifyApplicationRuntimeSettings(
            null,
            null,
            null,
            null,
            "256M",
            "256M",
            "64M",
            "0.250",
            null
        );
    }

    private String requiredDetail(JsonNode details, String field) {
        String value = trimToNull(details.path(field).asText(null));
        if (!hasText(value)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Standalone AI Workspace release is missing " + field + "."
            );
        }
        return value;
    }

    private CoolifyCreatePublicApplicationRequest coolifyPublicApplicationRequest(PlatformManagedProductServiceEntity service,
                                                                                 CoolifyBinding binding,
                                                                                 String appName) {
        CoolifyTargetProfileConfig config = binding.connection().config();
        String healthPath = firstNonBlank(healthPath(service), config.defaultHealthCheckPath());
        return new CoolifyCreatePublicApplicationRequest(
            config.projectUuid(),
            config.serverUuid(),
            config.environmentName(),
            config.environmentUuid(),
            coolifyGitRepository(provisioningProperties.repository()),
            provisioningProperties.branch(),
            "dockerfile",
            "/",
            coolifyDockerfileLocation(dockerfilePath(service)),
            firstNonBlank(config.defaultPortsExposes(), "8080"),
            config.destinationUuid(),
            appName,
            "Managed product service " + service.getServiceRef(),
            coolifyDomain(service, config),
            true,
            healthPath,
            firstNonBlank(config.defaultHealthCheckPort(), "8080"),
            false,
            false,
            config.forceHttps(),
            config.autogenerateDomain()
        );
    }


    private void awaitCoolifyApplicationDeletion(CoolifyConnection connection, String applicationUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (coolifyApiClient.getApplication(connection, applicationUuid).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Coolify application deletion.", ex);
            }
        }
        throw new ResponseStatusException(CONFLICT, "Timed out waiting for Coolify application deletion: " + applicationUuid);
    }


    private boolean coolifyApplicationReady(CoolifyApplicationSummary application) {
        if (application == null || !hasText(application.status())) {
            return false;
        }
        String normalized = upper(application.status());
        return normalized.startsWith("RUNNING") && !normalized.contains("UNHEALTHY");
    }

    private boolean coolifyDeploymentFinished(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = upper(deployment.status());
        return normalized.equals("FINISHED")
            || normalized.equals("SUCCESS")
            || normalized.equals("SUCCEEDED")
            || normalized.equals("COMPLETED")
            || hasText(deployment.finishedAt()) && !coolifyDeploymentFailed(deployment);
    }

    private boolean coolifyDeploymentFailed(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = upper(deployment.status());
        return normalized.contains("FAILED")
            || normalized.contains("ERROR")
            || normalized.contains("CANCELLED")
            || normalized.contains("CANCELED");
    }


    static String coolifyGitRepository(String repository) {
        String normalized = trimToNull(repository);
        if (!hasText(normalized)) {
            throw new RailwayProvisioningConfigurationException("Coolify git repository is required.");
        }
        String candidate = normalized;
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            candidate = candidate.substring(candidate.indexOf("github.com/") + "github.com/".length());
        } else if (lower.startsWith("ssh://git@github.com/")) {
            candidate = candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            candidate = candidate.substring("git@github.com:".length());
            candidate = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
            if (candidate.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
                return "git@github.com:" + (candidate.endsWith(".git") ? candidate : candidate + ".git");
            }
            throw new RailwayProvisioningConfigurationException(
                "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
            );
        } else if (lower.contains("://") || lower.startsWith("git@")) {
            throw new RailwayProvisioningConfigurationException(
                "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
            );
        }
        String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
        if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
            return "https://github.com/" + (slug.endsWith(".git") ? slug : slug + ".git");
        }
        throw new RailwayProvisioningConfigurationException(
            "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
        );
    }

    static String coolifyDockerfileLocation(String dockerfilePath) {
        String normalized = trimToNull(dockerfilePath);
        if (!hasText(normalized)) {
            throw new RailwayProvisioningConfigurationException("Coolify dockerfile path is required.");
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private List<CoolifyEnvVar> buildCoolifyServiceEnv(PlatformManagedProductServiceEntity service,
                                                       String publicBaseUrl) {
        return buildServiceEnv(service, publicBaseUrl).stream()
            .map(env -> new CoolifyEnvVar(env.name(), env.value(), false, true, containsLineBreak(env.value()), false))
            .toList();
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.contains("\n") || value.contains("\r"));
    }

    private void finalizeActiveCoolifyService(PlatformManagedProductServiceEntity service,
                                              String targetProfileId,
                                              String projectUuid,
                                              String environmentUuid,
                                              CoolifyApplicationSummary application,
                                              CoolifyActionResponse deployResponse,
                                              String publicBaseUrl,
                                              String message) {
        service.setDesiredReplicas(desiredReplicas(service));
        service.setActualReplicas(desiredReplicas(service));
        service.setBaseUrl(publicBaseUrl);
        service.setPrivateNetworkUrl(null);
        service.setHealthPath(healthPath(service));
        service.setServiceRoot(serviceRoot(service));
        service.setDockerfilePath(dockerfilePath(service));
        service.setStatus("ACTIVE");
        ObjectNode details = buildServiceDetails(service, deployResponse == null ? null : deployResponse.deploymentUuid());
        details.put("providerType", "COOLIFY");
        details.put("targetProfileId", targetProfileId);
        details.put("coolifyApplicationUuid", application.uuid());
        details.put("coolifyProjectUuid", projectUuid);
        details.put("coolifyEnvironmentUuid", environmentUuid);
        details.put("coolifyFqdn", blankToFallback(application.fqdn(), ""));
        details.put("lastObservedStatus", blankToFallback(application.status(), ""));
        details.put("lastReconcileStatus", "SUCCESS");
        details.put("lastReconcileMessage", blankToFallback(message, "Coolify product service is active."));
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private CoolifyBinding resolveCoolifyBinding(PlatformManagedProductServiceEntity service, String targetOverride) {
        ensureCoolifySupportAvailable();
        ObjectNode details = mutableDetails(service);
        String recorded = trimToNull(details.path("targetProfileId").asText(null));
        if (hasText(targetOverride) && hasText(recorded) && !targetOverride.equals(recorded))
            throw PlatformManagedServiceOwnershipJournal.denied();
        String targetProfileId = firstNonBlank(targetOverride, recorded);
        DeploymentTargetProfileEntity profile = hasText(targetProfileId)
            ? targetProfileRepository.findById(targetProfileId).orElseThrow(PlatformManagedServiceOwnershipJournal::denied)
            : resolveDefaultCoolifyPlatformServiceProfile();
        if (!profile.isActive() || !profile.isPlatformServicesAllowed() || profile.getProviderType() != DeploymentProviderType.COOLIFY)
            throw PlatformManagedServiceOwnershipJournal.denied();
        return new CoolifyBinding(profile, coolifyTargetProfileResolver.requireConnection(profile));
    }

    private DeploymentTargetProfileEntity resolveDefaultCoolifyPlatformServiceProfile() {
        List<DeploymentTargetProfileEntity> allowedProfiles = targetProfileRepository
            .findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(DeploymentProviderType.COOLIFY)
            .stream()
            .filter(DeploymentTargetProfileEntity::isActive)
            .filter(DeploymentTargetProfileEntity::isPlatformServicesAllowed)
            .toList();
        if (allowedProfiles.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "No active Coolify target profile allows platform services.");
        }
        return allowedProfiles.stream()
            .filter(DeploymentTargetProfileEntity::isDefaultForRestartableServices)
            .findFirst()
            .orElseGet(() -> {
                if (allowedProfiles.size() == 1) {
                    return allowedProfiles.get(0);
                }
                throw new ResponseStatusException(
                    CONFLICT,
                    "Multiple Coolify target profiles allow platform services. Set targetProfileId explicitly."
                );
            });
    }

    private String coolifyDomain(PlatformManagedProductServiceEntity service, CoolifyTargetProfileConfig config) {
        String existingBaseUrl = trimToNull(service.getBaseUrl());
        if (hasText(existingBaseUrl)) {
            return existingBaseUrl.split(",")[0].trim();
        }
        if (config.autogenerateDomain()) {
            return null;
        }
        String suffix = trimToNull(config.defaultPublicDomainSuffix());
        if (!hasText(suffix)) {
            return null;
        }
        String normalizedSuffix = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        return "https://" + normalizeToken(service.getServiceRef()) + "." + normalizedSuffix;
    }

    private String publicBaseUrl(CoolifyApplicationSummary application) {
        if (application == null || !hasText(application.fqdn())) {
            return null;
        }
        String fqdn = application.fqdn().split(",")[0].trim();
        if (!hasText(fqdn)) {
            return null;
        }
        return fqdn.startsWith("http://") || fqdn.startsWith("https://") ? fqdn : "https://" + fqdn;
    }

    private String mergeCoolifyDomains(String desiredDomains, String observedDomains) {
        if (!hasText(desiredDomains)) {
            return trimToNull(observedDomains);
        }
        List<String> merged = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        appendCoolifyDomains(merged, seen, desiredDomains);
        if (coolifyDomainListsOverlap(desiredDomains, observedDomains)) {
            appendCoolifyDomains(merged, seen, observedDomains);
        }
        return merged.isEmpty() ? trimToNull(desiredDomains) : String.join(",", merged);
    }

    private void appendCoolifyDomains(List<String> merged, java.util.Set<String> seen, String domains) {
        if (!hasText(domains)) {
            return;
        }
        for (String candidate : domains.split(",")) {
            String normalized = trimToNull(candidate);
            String key = coolifyDomainKey(normalized);
            if (hasText(normalized) && hasText(key) && seen.add(key)) {
                merged.add(normalized);
            }
        }
    }

    private boolean coolifyDomainListsOverlap(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        java.util.Set<String> leftKeys = new java.util.LinkedHashSet<>();
        for (String candidate : left.split(",")) {
            String key = coolifyDomainKey(candidate);
            if (hasText(key)) {
                leftKeys.add(key);
            }
        }
        for (String candidate : right.split(",")) {
            if (leftKeys.contains(coolifyDomainKey(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String coolifyDomainKey(String domain) {
        String host = normalizedUrlHost(domain);
        if (hasText(host)) {
            return host.toLowerCase(Locale.ROOT);
        }
        String normalized = trimToNull(domain);
        return hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private boolean coolifyFqdnMatchesBaseUrl(String fqdn, String baseUrl) {
        String expected = normalizedUrlHost(baseUrl);
        if (!hasText(expected) || !hasText(fqdn)) {
            return false;
        }
        for (String candidate : fqdn.split(",")) {
            if (expected.equalsIgnoreCase(normalizedUrlHost(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizedUrlHost(String value) {
        String normalized = trimToNull(value);
        if (!hasText(normalized)) {
            return null;
        }
        normalized = normalized.replaceFirst("^https?://", "");
        int slash = normalized.indexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(0, slash);
        }
        return trimToNull(normalized);
    }

    private void clearCoolifyBinding(PlatformManagedProductServiceEntity service) {
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("CREATED");
        ObjectNode details = mutableDetails(service);
        details.remove(List.of("coolifyApplicationUuid", "coolifyProjectUuid", "coolifyEnvironmentUuid", "coolifyFqdn"));
        details.put("providerType", "COOLIFY");
        details.put("lastForceRecreatedAt", Instant.now().toString());
        details.put("lastForceRecreateStatus", "PENDING");
        details.put("lastReconcileStatus", "RECONCILIATION_REQUIRED");
        details.put("sharedOwnershipState", "CREATE_PENDING");
        details.put("lastForceRecreateMessage", "Owned deletion was verified; replacement is not yet verified.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private void ensureCoolifySupportAvailable() {
        if (coolifyTargetProfileResolver == null || coolifyApiClient == null || targetProfileRepository == null) {
            throw new ResponseStatusException(CONFLICT, "Coolify product service lifecycle dependencies are not configured.");
        }
    }

    private void awaitProjectDeletion(String projectId, String projectName) {
        Instant deadline = Instant.now().plus(provisioningProperties.deploymentTimeout());
        while (Instant.now().isBefore(deadline)) {
            boolean projectExistsById;
            try {
                railwayGraphqlClient.getProject(projectId);
                projectExistsById = true;
            } catch (RuntimeException ignored) {
                projectExistsById = false;
            }
            if (!projectExistsById) {
                RailwayGraphqlClient.RailwayProjectSnapshot projectByName = null;
                try {
                    projectByName = railwayGraphqlClient.findProjectByName(provisioningProperties.workspaceId(), projectName);
                } catch (RuntimeException ignored) {
                    projectByName = null;
                }
                if (projectByName == null || !projectId.equalsIgnoreCase(blankToFallback(projectByName.id(), ""))) {
                    return;
                }
            }
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Railway project deletion.", ex);
            }
        }
        throw new RailwayProvisioningException(
            "Timed out waiting for Railway project deletion to settle: " + projectName
        );
    }

    private boolean requiresRailwayLifecycle(PlatformManagedProductServiceEntity service) {
        return "SHOPIFY_BRIDGE_SERVICE".equals(upper(service.getServiceKind()))
            && !isCoolifyManaged(service);
    }

    private boolean requiresCoolifyLifecycle(PlatformManagedProductServiceEntity service) {
        return coolifyTargetProfileResolver != null
            && coolifyApiClient != null
            && targetProfileRepository != null
            && (isCoolifyManaged(service)
            || "SHOPIFY_BRIDGE_SERVICE".equals(upper(service.getServiceKind()))
            || "MCP_EXECUTION_GATEWAY_SERVICE".equals(upper(service.getServiceKind())));
    }

    private boolean isCoolifyManaged(PlatformManagedProductServiceEntity service) {
        ObjectNode details = mutableDetails(service);
        return "COOLIFY".equalsIgnoreCase(details.path("providerType").asText(null))
            || hasText(details.path("coolifyApplicationUuid").asText(null))
            || details.path("targetProfileId").asText("").startsWith("dtp-coolify-");
    }

    private RailwayGraphqlClient.RailwayProjectSnapshot resolveCurrentProjectSnapshot(PlatformManagedProductServiceEntity service) {
        if (!hasText(service.getRailwayProjectId())) {
            return null;
        }
        try {
            return railwayGraphqlClient.getProject(service.getRailwayProjectId());
        } catch (RuntimeException ex) {
            String message = blankToFallback(ex.getMessage(), "").toLowerCase(Locale.ROOT);
            if (message.contains("project not found")) {
                return null;
            }
            throw ex;
        }
    }

    private ReconciledRailwayService reconcileRailwayService(PlatformManagedProductServiceEntity service) {
        String environmentName = resolveEnvironmentName(service);
        RailwayGraphqlClient.RailwayProjectSnapshot project = railwayGraphqlClient.findProjectByName(
            provisioningProperties.workspaceId(),
            sharedProjectName(service)
        );
        if (project == null) {
            project = railwayGraphqlClient.createProject(
                provisioningProperties.workspaceId(),
                sharedProjectName(service),
                environmentName
            );
        }
        RailwayGraphqlClient.RailwayEnvironmentSummary environment = project.environmentNamed(environmentName);
        if (environment == null) {
            environment = railwayGraphqlClient.createEnvironment(project.id(), environmentName);
        }

        RailwayGraphqlClient.RailwayServiceSummary railwayService = project.serviceNamed(sharedServiceName(service));
        if (railwayService == null) {
            railwayService = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                sharedServiceName(service),
                provisioningProperties.repository(),
                provisioningProperties.branch()
            );
        }

        railwayGraphqlClient.connectServiceToRepository(
            railwayService.id(),
            provisioningProperties.repository(),
            provisioningProperties.branch()
        );
        railwayGraphqlClient.updateServiceInstance(
            railwayService.id(),
            environment.id(),
            serviceRoot(service),
            dockerfilePath(service),
            healthPath(service),
            desiredReplicas(service)
        );

        ensureServiceSecret(service);
        String publicBaseUrl = ensureServiceDomain(project.id(), environment.id(), railwayService.id());
        railwayGraphqlClient.upsertVariables(
            project.id(),
            environment.id(),
            railwayService.id(),
            buildServiceEnv(service, publicBaseUrl)
        );

        if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
            railwayGraphqlClient.commitStagedChanges(environment.id());
        }
        String deploymentId = railwayGraphqlClient.deployService(railwayService.id(), environment.id());
        RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            deploymentId,
            sharedServiceName(service),
            provisioningProperties.deploymentTimeout(),
            productProvisioningProperties.pollInterval(),
            () -> railwayGraphqlClient.getDeployment(deploymentId),
            duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
            ex -> {
            }
        );

        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
            environment.id(),
            railwayService.id()
        );
        return new ReconciledRailwayService(
            project.id(),
            environment.id(),
            railwayService.id(),
            instance,
            publicBaseUrl,
            deploymentId
        );
    }

    private void finalizeActiveService(PlatformManagedProductServiceEntity service,
                                       String projectId,
                                       String environmentId,
                                       String serviceId,
                                       RailwayGraphqlClient.RailwayServiceInstanceSummary instance,
                                       String publicBaseUrl,
                                       String deploymentId,
                                       String message) {
        service.setRailwayProjectId(projectId);
        service.setRailwayEnvironmentId(environmentId);
        service.setRailwayServiceId(serviceId);
        service.setDesiredReplicas(desiredReplicas(service));
        service.setActualReplicas(desiredReplicas(service));
        service.setBaseUrl(publicBaseUrl);
        service.setPrivateNetworkUrl(trimToNull(instance.upstreamUrl()));
        service.setHealthPath(healthPath(service));
        service.setServiceRoot(serviceRoot(service));
        service.setDockerfilePath(dockerfilePath(service));
        service.setStatus("ACTIVE");
        ObjectNode details = buildServiceDetails(service, deploymentId);
        details.put("lastReconcileStatus", "SUCCESS");
        details.put("lastReconcileMessage", blankToFallback(message, "Managed product service is active."));
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private String resolveEnvironmentName(PlatformManagedProductServiceEntity service) {
        return hasText(service.getEnvironmentScope()) ? service.getEnvironmentScope().trim() : provisioningProperties.environmentName();
    }

    private String sharedProjectName(PlatformManagedProductServiceEntity service) {
        return trimProjectName(
            productProvisioningProperties.sharedProjectNamePrefix()
                + "-" + normalizeToken(resolveEnvironmentName(service))
                + "-" + normalizeToken(service.getServiceRef())
        );
    }

    private String sharedServiceName(PlatformManagedProductServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" ->
                trimProjectName(productProvisioningProperties.shopifyBridgeServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            case "MCP_EXECUTION_GATEWAY_SERVICE" ->
                trimProjectName(productProvisioningProperties.mcpExecutionGatewayServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                trimProjectName("loomai-engage-" + normalizeToken(service.getServiceRef()));
            default -> trimProjectName("product-" + normalizeToken(service.getServiceRef()));
        };
    }

    private String serviceRoot(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getServiceRoot())) {
            return service.getServiceRoot().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeServiceRoot();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayServiceRoot();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                "workspace-apps/standalone-ai-workspace";
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String dockerfilePath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getDockerfilePath())) {
            return service.getDockerfilePath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeDockerfilePath();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayDockerfilePath();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                "workspace-apps/standalone-ai-workspace/Dockerfile";
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String healthPath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getHealthPath())) {
            return service.getHealthPath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeHealthPath();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayHealthPath();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE -> "/health";
            default -> "/actuator/health";
        };
    }

    private int desiredReplicas(PlatformManagedProductServiceEntity service) {
        Integer desired = service.getDesiredReplicas();
        return desired != null && desired > 0 ? desired : 1;
    }

    private void ensureServiceSecret(PlatformManagedProductServiceEntity service) {
        if (PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(upper(service.getServiceKind()))) {
            return;
        }
        if (!hasText(service.getSecretName())) {
            service.setSecretName(defaultSecretName(service.getServiceRef()));
        }
        if (!platformSecretService.isSecretPresent(service.getSecretName())) {
            platformSecretService.upsertManagedSecret(
                service.getSecretName(),
                UUID.randomUUID().toString().replace("-", ""),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
            );
        }
    }

    private String defaultSecretName(String serviceRef) {
        return "MANAGED_PRODUCT_" + normalizeToken(serviceRef).replace('-', '_').toUpperCase(Locale.ROOT) + "_API_KEY";
    }

    private List<RailwayGraphqlClient.RailwayEnvVarInput> buildServiceEnv(PlatformManagedProductServiceEntity service,
                                                                          String publicBaseUrl) {
        List<RailwayGraphqlClient.RailwayEnvVarInput> env = new ArrayList<>();
        switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> {
                String sharedSecret = resolveSecret(service.getSecretName());
                String shopifyApiKey = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeShopifyApiKeySecretName());
                String shopifyApiSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeShopifyApiSecretSecretName());
                String webhookSharedSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeWebhookSharedSecretName());
                String customerAccountMcpClientId = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CLIENT_ID_SECRET);
                String customerAccountMcpClientSecret = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET_SECRET);
                String customerAccountMcpRedirectUri = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_REDIRECT_URI_SECRET);
                String customerAccountMcpStorefrontDomain = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN_SECRET);
                String customerAccountMcpScopes = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_SCOPES_SECRET);
                String customerAccountMcpStateTtl = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_STATE_TTL_SECRET);
                String customerAccountMcpSessionTtl = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_SESSION_TTL_SECRET);
                String customerAccountMcpConnectTimeout = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT_SECRET);
                String customerAccountMcpReadTimeout = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT_SECRET);
                boolean customerAccountMcpProtectedApproved = Boolean.parseBoolean(
                    blankToFallback(resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_PROTECTED_APPROVED_SECRET), "false")
                );
                boolean customerAccountMcpConfigured = customerAccountMcpProtectedApproved
                    && hasText(customerAccountMcpClientId)
                    && hasText(customerAccountMcpClientSecret)
                    && hasText(customerAccountMcpRedirectUri)
                    && hasText(customerAccountMcpScopes);
                boolean checkoutMcpConfigured = checkoutMcpCredentialsConfigured();
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_SHARED_SECRET", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_SERVICE_REF", service.getServiceRef()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_APP_NAME", blankToFallback(service.getDisplayName(), "Shopify Bridge Service")));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_ENVIRONMENT_SCOPE", resolveEnvironmentName(service)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_ADMIN_API_VERSION", productProvisioningProperties.shopifyBridgeAdminApiVersion()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PLATFORM_BASE_URL", deliveryProperties.publicBaseUrl()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PLATFORM_ADMIN_API_KEY", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PUBLIC_BASE_URL", blankToFallback(publicBaseUrl, "")));
                addMcpGatewayEnv(env);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_SHOPIFY_API_KEY", shopifyApiKey);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_SHOPIFY_API_SECRET", shopifyApiSecret);
                addOptionalEnv(
                    env,
                    "SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET",
                    hasText(webhookSharedSecret) ? webhookSharedSecret : shopifyApiSecret
                );
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_ENABLED", Boolean.toString(customerAccountMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED", Boolean.toString(customerAccountMcpProtectedApproved)));
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID", customerAccountMcpClientId);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET", customerAccountMcpClientSecret);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI", customerAccountMcpRedirectUri);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN", customerAccountMcpStorefrontDomain);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES", customerAccountMcpScopes);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL", customerAccountMcpStateTtl);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL", customerAccountMcpSessionTtl);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT", customerAccountMcpConnectTimeout);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT", customerAccountMcpReadTimeout);
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED", Boolean.toString(checkoutMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED", "false"));
                PlatformManagedProductServiceShopifyBillingConfigSummary billingConfig =
                    PlatformManagedProductServiceShopifyBillingConfigSupport.summaryFromDetails(
                        objectMapper,
                        service.getDetailsJson(),
                        service.getServiceKind()
                    );
                PlatformManagedProductServiceShopifyBillingConfigSupport.railwayEnv(billingConfig)
                    .forEach((name, value) -> env.add(new RailwayGraphqlClient.RailwayEnvVarInput(name, value)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info"));
            }
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> {
                String sharedSecret = resolveSecret(service.getSecretName());
                String checkoutClientId = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientIdSecretName());
                String checkoutClientSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientSecretSecretName());
                boolean checkoutMcpConfigured = hasText(checkoutClientId) && hasText(checkoutClientSecret);
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_INTERNAL_API_KEY", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_SERVICE_REF", service.getServiceRef()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SCOPE", resolveEnvironmentName(service)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROTOCOL_VERSION", "2025-11-25"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_API_KEY_HEADER_ALLOWLIST", "X-API-KEY,X-MCP-API-KEY,X-LOOM-MCP-KEY"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROFILE_REF_ALLOWLIST", "MCP_PROFILE_SHOPIFY_UCP_AGENT,SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_PROFILE_SHOPIFY_UCP_AGENT", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_RESOLUTION_ENABLED", Boolean.toString(checkoutMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_REF_PREFIX", "MCP_SECRET_"));
                if (checkoutMcpConfigured) {
                    env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID", checkoutClientId));
                    env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET", checkoutClientSecret));
                }
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info"));
            }
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE -> {
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput(
                    "PLATFORM_API_BASE_URL", deliveryProperties.publicBaseUrl()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("PORT", "8080"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("NODE_ENV", "production"));
            }
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        }
        return env;
    }

    private void addMcpGatewayEnv(List<RailwayGraphqlClient.RailwayEnvVarInput> env) {
        String gatewayRef = productProvisioningProperties.mcpExecutionGatewayServiceRef();
        PlatformManagedProductServiceEntity gateway = serviceRepository.findByServiceRefIgnoreCase(gatewayRef)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify Bridge requires managed MCP execution gateway service: " + gatewayRef
            ));
        if (!hasText(gateway.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "MCP execution gateway has no base URL: " + gatewayRef);
        }
        if (!hasText(gateway.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "MCP execution gateway has no managed API key secret: " + gatewayRef);
        }
        String gatewaySecret = resolveSecret(gateway.getSecretName());
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_BASE_URL", gateway.getBaseUrl()));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY", gatewaySecret));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY_HEADER", "X-MCP-GATEWAY-API-KEY"));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_EXECUTE_PATH", "/api/internal/mcp/actions/execute"));
    }

    private String defaultShopifyUcpAgentProfile() {
        return "https://shopify.dev/ucp/agent-profiles/examples/2026-08-25/valid-with-capabilities.json";
    }

    private boolean checkoutMcpCredentialsConfigured() {
        return hasText(resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientIdSecretName()))
            && hasText(resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientSecretSecretName()));
    }

    private String resolveSecret(String secretName) {
        String value = platformSecretService.resolveSecret(secretName);
        if (!hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing managed product service secret '" + secretName + "'."
            );
        }
        return value;
    }

    private String resolveOptionalSecret(String secretName) {
        String normalized = trimToNull(secretName);
        if (!hasText(normalized)) {
            return null;
        }
        String value = platformSecretService.resolveSecret(normalized);
        return hasText(value) ? value : null;
    }

    private void addOptionalEnv(List<RailwayGraphqlClient.RailwayEnvVarInput> env,
                                String name,
                                String value) {
        if (hasText(value)) {
            env.add(new RailwayGraphqlClient.RailwayEnvVarInput(name, value));
        }
    }

    private String ensureServiceDomain(String projectId,
                                       String environmentId,
                                       String serviceId) {
        List<RailwayGraphqlClient.RailwayServiceDomainSummary> existingDomains = railwayGraphqlClient.listServiceDomains(
            projectId,
            environmentId,
            serviceId
        );
        RailwayGraphqlClient.RailwayServiceDomainSummary domain = existingDomains.isEmpty()
            ? railwayGraphqlClient.createServiceDomain(serviceId, environmentId)
            : existingDomains.get(0);
        if (!hasText(domain.domain())) {
            throw new RailwayProvisioningException("Railway service domain was not created for managed product service " + serviceId + ".");
        }
        return "https://" + domain.domain();
    }

    private ObjectNode buildServiceDetails(PlatformManagedProductServiceEntity service,
                                           String deploymentId) {
        ObjectNode details = mutableDetails(service);
        details.put("serviceRef", service.getServiceRef());
        details.put("serviceKind", blankToFallback(service.getServiceKind(), ""));
        details.put("deploymentMode", blankToFallback(service.getDeploymentMode(), ""));
        details.put("deploymentId", deploymentId);
        details.put("reconciledAt", Instant.now().toString());
        details.put("lastDeploymentId", blankToFallback(deploymentId, ""));
        details.put("lastReconciledAt", Instant.now().toString());
        details.put("platformBaseUrl", deliveryProperties.publicBaseUrl());
        return details;
    }

    private void clearRailwayBinding(PlatformManagedProductServiceEntity service) {
        service.setRailwayProjectId(null);
        service.setRailwayEnvironmentId(null);
        service.setRailwayServiceId(null);
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("CREATED");
        ObjectNode details = mutableDetails(service);
        details.put("lastForceRecreatedAt", Instant.now().toString());
        details.put("lastForceRecreateStatus", "SUCCESS");
        details.put("lastForceRecreateMessage", "Managed product service linkage was cleared and will be recreated.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private void markLifecycleFailure(PlatformManagedProductServiceEntity service,
                                      String status,
                                      String message,
                                      String statusField,
                                      String messageField) {
        service.setStatus(status);
        ObjectNode details = mutableDetails(service);
        details.put(statusField, "FAILED");
        details.put(messageField, blankToFallback(message, "Managed product service operation failed."));
        details.put("lastFailureAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private ObjectNode mutableDetails(PlatformManagedProductServiceEntity service) {
        try {
            JsonNode parsed = hasText(service.getDetailsJson())
                ? objectMapper.readTree(service.getDetailsJson())
                : objectMapper.createObjectNode();
            return parsed.isObject() ? (ObjectNode) parsed.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String trimProjectName(String value) {
        String normalized = normalizeToken(value);
        int max = Math.max(8, provisioningProperties.projectNameMaxLength());
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String normalizeToken(String value) {
        if (!hasText(value)) {
            return "default";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String firstNonBlank(String left, String right) {
        return hasText(left) ? left.trim() : hasText(right) ? right.trim() : null;
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /** Generic immutable-service extension; checkpoints must commit before remote creation. */
    public interface OwnedDependencyCheckpoint {
        void guard();
        void creating(String kind);
        void created(String kind, String uuid);
        void volume(String mountPath, String uuid);
        void volumeReclamationPlanned(Map<String, String> volumeNamesByUuid);
        void volumesReclaimed(Set<String> volumeUuids);
    }

    public record OwnedDependencyResources(String applicationUuid, String databaseUuid,
                                          boolean applicationCreateIntent, boolean databaseCreateIntent,
                                          Map<String, String> volumes,
                                          Map<String, String> volumeReclamationPlan,
                                          Set<String> reclaimedVolumeUuids) {
        public OwnedDependencyResources {
            volumes = volumes == null ? Map.of() : Map.copyOf(volumes);
            volumeReclamationPlan = volumeReclamationPlan == null ? Map.of() : Map.copyOf(volumeReclamationPlan);
            reclaimedVolumeUuids = reclaimedVolumeUuids == null ? Set.of() : Set.copyOf(reclaimedVolumeUuids);
        }

        public OwnedDependencyResources(String applicationUuid, String databaseUuid,
                boolean applicationCreateIntent, boolean databaseCreateIntent,
                Map<String, String> volumes) {
            this(applicationUuid, databaseUuid, applicationCreateIntent, databaseCreateIntent,
                volumes, Map.of(), Set.of());
        }

        public OwnedDependencyResources(String applicationUuid, String databaseUuid,
                boolean applicationCreateIntent, boolean databaseCreateIntent,
                Map<String, String> volumes, Set<String> reclaimedVolumeUuids) {
            this(applicationUuid, databaseUuid, applicationCreateIntent, databaseCreateIntent,
                volumes, reclaimedVolumeUuids.stream().collect(java.util.stream.Collectors.toMap(
                    value -> value, value -> value)), reclaimedVolumeUuids);
        }
    }
    public record OwnedDependencyResult(String applicationUuid, String databaseUuid, String baseUrl) {}

    public record OwnedLifecycleResource(String kind, String uuid, String name, String marker,
                                         String status, JsonNode raw, List<CoolifyApplicationStorageSummary> storages) {
        public OwnedLifecycleResource { storages = List.copyOf(storages); }
    }

    /** Creation receipts, graph identity and live parent placement are rechecked on every observation. */
    public List<OwnedLifecycleResource> inspectOwnedLifecycleResources(CoolifyConnection connection, String graphId,
            String serviceRef, String slot, OwnedDependencyResources resources) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        if (resources.applicationCreateIntent() && !hasText(resources.applicationUuid())
            || resources.databaseCreateIntent() && !hasText(resources.databaseUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        var result = new ArrayList<OwnedLifecycleResource>();
        if (hasText(resources.applicationUuid())) {
            var app = authority.application(owner, resources.applicationUuid(), true);
            authority.applicationVolumes(owner, resources.applicationUuid(), resources.volumes(), true);
            result.add(new OwnedLifecycleResource(OwnedDependencyOwnership.APPLICATION, resources.applicationUuid(),
                serviceRef, owner.marker(), app.map(CoolifyApplicationSummary::status).orElse("ABSENT"),
                app.map(CoolifyApplicationSummary::raw).orElse(null), app.isPresent()
                    ? coolifyApiClient.listApplicationStorages(connection, resources.applicationUuid()) : List.of()));
        }
        if (hasText(resources.databaseUuid())) {
            var db = authority.database(owner, resources.databaseUuid(), true);
            authority.databaseVolumes(owner, resources.databaseUuid(), true);
            result.add(new OwnedLifecycleResource(OwnedDependencyOwnership.DATABASE, resources.databaseUuid(),
                serviceRef + "-db", owner.marker(), db.map(CoolifyDatabaseSummary::status).orElse("ABSENT"),
                db.map(CoolifyDatabaseSummary::raw).orElse(null), db.isPresent()
                    ? coolifyApiClient.listDatabaseStorages(connection, resources.databaseUuid()) : List.of()));
        }
        return List.copyOf(result);
    }

    public OwnedLifecycleResource inspectOwnedLifecycleResource(CoolifyConnection connection, String graphId,
        String serviceRef, String slot, OwnedDependencyResources resources, String uuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        String kind = ownedLifecycleKind(resources, uuid);
        if (OwnedDependencyOwnership.DATABASE.equals(kind)) {
            var db = authority.database(owner, uuid, true);
            return new OwnedLifecycleResource(kind, uuid, serviceRef + "-db", owner.marker(),
                db.map(CoolifyDatabaseSummary::status).orElse("ABSENT"), db.map(CoolifyDatabaseSummary::raw).orElse(null),
                db.isPresent() ? coolifyApiClient.listDatabaseStorages(connection, uuid) : List.of());
        }
        var app = authority.application(owner, uuid, true);
        return new OwnedLifecycleResource(kind, uuid, serviceRef, owner.marker(),
            app.map(CoolifyApplicationSummary::status).orElse("ABSENT"), app.map(CoolifyApplicationSummary::raw).orElse(null),
            app.isPresent() ? coolifyApiClient.listApplicationStorages(connection, uuid) : List.of());
    }

    public ManagedResourceOwnershipService.DeletionPreparation ownedLifecycleDeletionPreparation(CoolifyConnection connection,
        String graphId, String serviceRef, String slot, OwnedDependencyResources resources, String uuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        String kind = ownedLifecycleKind(resources, uuid);
        return authority.lifecycleDeletionPreparation(owner, kind, uuid);
    }

    public boolean prepareOwnedLifecycleDeletion(ManagedResourceOwnershipService.DeletionPreparation preparation, Runnable guard) {
        return requireOwnedDependencyAuthority().prepareLifecycleDeletion(preparation, guard);
    }

    public JsonNode readbackOwnedLifecycleDeletion(CoolifyConnection connection, String graphId, String serviceRef,
        String slot, OwnedDependencyResources resources, String uuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        return authority.lifecycleDeletionReadback(owner, ownedLifecycleKind(resources, uuid), uuid);
    }

    private String ownedLifecycleKind(OwnedDependencyResources resources, String uuid) {
        boolean database = uuid != null && uuid.equals(resources.databaseUuid());
        boolean application = uuid != null && uuid.equals(resources.applicationUuid());
        if (database == application) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_EFFECT_INVALID");
        return database ? OwnedDependencyOwnership.DATABASE : OwnedDependencyOwnership.APPLICATION;
    }

    /** Single dispatch only. The caller must have durably claimed its exact effect before entering. */
    public CoolifyActionResponse dispatchOwnedLifecycleResource(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, OwnedDependencyResources resources, String uuid, String action, boolean reclaimOwnedData) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        boolean database = uuid != null && uuid.equals(resources.databaseUuid());
        boolean application = uuid != null && uuid.equals(resources.applicationUuid());
        if (database == application || !Set.of("STOP", "START", "DELETE").contains(action))
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_EFFECT_INVALID");
        String kind = database ? OwnedDependencyOwnership.DATABASE : OwnedDependencyOwnership.APPLICATION;
        boolean present = database ? authority.database(owner, uuid, true).isPresent()
            : authority.application(owner, uuid, true).isPresent();
        if (!present) {
            if ("DELETE".equals(action)) return new CoolifyActionResponse("Already absent", null, null);
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_RESOURCE_ABSENT");
        }
        if ("DELETE".equals(action)) {
            if (reclaimOwnedData) authority.beginVolumeDeletion(owner, resources.applicationUuid(),
                resources.volumes(), resources.databaseUuid());
            authority.beginDeletion(owner, kind, uuid);
            return database ? coolifyApiClient.deleteDatabase(connection, uuid, true, false, false, false)
                : coolifyApiClient.delete(connection, uuid, true, false, false, false);
        } else if ("STOP".equals(action)) {
            return database ? coolifyApiClient.stopDatabase(connection, uuid)
                : coolifyApiClient.stop(connection, uuid, false);
        } else {
            return database ? coolifyApiClient.startDatabase(connection, uuid)
                : coolifyApiClient.start(connection, uuid, false, true);
        }
    }

    /** Resource receipt finalization is separate from physical volume-reclamation evidence. */
    public void finishOwnedLifecycleResource(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, OwnedDependencyResources resources, String uuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        boolean database = uuid != null && uuid.equals(resources.databaseUuid());
        if (!database && (uuid == null || !uuid.equals(resources.applicationUuid())))
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_EFFECT_INVALID");
        if (database ? authority.database(owner, uuid, true).isPresent() : authority.application(owner, uuid, true).isPresent())
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_ABSENCE_UNCONFIRMED");
        authority.deleted(owner, database ? OwnedDependencyOwnership.DATABASE : OwnedDependencyOwnership.APPLICATION, uuid);
    }

    public void finishOwnedLifecycleVolumes(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, OwnedDependencyResources resources, String uuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        if (uuid != null && uuid.equals(resources.databaseUuid())) {
            authority.volumesDeleted(owner, null, Map.of(), uuid);
        } else if (uuid != null && uuid.equals(resources.applicationUuid())) {
            authority.volumesDeleted(owner, uuid, resources.volumes(), null);
        } else throw new ResponseStatusException(CONFLICT, "DEPENDENCY_LIFECYCLE_EFFECT_INVALID");
    }

    public CoolifyConnection ownedDependencyConnection(String targetProfileId, String environmentIntent) {
        ensureCoolifySupportAvailable();
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_MISSING"));
        if (!profile.isActive() || profile.getProviderType() != com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY
            || !environmentIntent.equals(profile.getEnvironmentName())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_INVALID");
        }
        CoolifyConnection connection = coolifyTargetProfileResolver.requireConnection(profile);
        CoolifyTargetProfileConfig config = connection.config();
        TargetIntent intent = switch (environmentIntent) {
            case "staging" -> TargetIntent.CUSTOMER_STAGING;
            case "production" -> TargetIntent.CUSTOMER_PRODUCTION;
            default -> throw new ResponseStatusException(CONFLICT, "DEPENDENCY_ENVIRONMENT_UNSUPPORTED");
        };
        TargetIntent otherIntent = intent == TargetIntent.CUSTOMER_STAGING
            ? TargetIntent.CUSTOMER_PRODUCTION : TargetIntent.CUSTOMER_STAGING;
        var trusted = customerSelfServiceProperties.trustedServers().get(intent);
        var other = customerSelfServiceProperties.trustedServers().get(otherIntent);
        if (trusted == null || other == null || !hasText(trusted.uuid()) || !hasText(trusted.address())
            || !hasText(trusted.reportedAddress()) || !hasText(other.uuid()) || !hasText(other.address())
            || !hasText(other.reportedAddress())
            || trusted.uuid().equals(other.uuid()) || trusted.address().equals(other.address())
            || !trusted.uuid().equals(config.serverUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_SERVER_PLACEMENT_CONFLICT");
        }
        // Read actual placement. A profile name is not server evidence.
        JsonNode server = coolifyApiClient.getServer(connection, config.serverUuid());
        if (server == null || !trusted.reportedAddress().equals(server.path("ip").asText())
            || !trusted.uuid().equals(server.path("uuid").asText())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_SERVER_PLACEMENT_CONFLICT");
        }
        CoolifyDestinationPlacement placement = coolifyApiClient.resolveDestinationPlacement(
            connection, config.serverUuid(), config.destinationUuid())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DESTINATION_UNVERIFIED"));
        if (!config.serverUuid().equals(placement.serverUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DESTINATION_CONFLICT");
        }
        if (!hasText(config.projectUuid()) || !hasText(config.environmentUuid()) || !config.forceHttps()
            || !hasText(config.defaultPublicDomainSuffix()) || config.autogenerateDomain()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TRUSTED_TLS_DOMAIN_REQUIRED");
        }
        return connection;
    }

    public OwnedDependencyResult reconcileOwnedDependency(CoolifyConnection connection, String graphId,
            String serviceRef, com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, java.util.function.Function<String, List<CoolifyEnvVar>> environment,
            OwnedDependencyCheckpoint checkpoint) {
        return reconcileOwnedDependency(connection, graphId, serviceRef, spec, resources, environment, checkpoint, false);
    }

    public OwnedDependencyResult reconcileExistingOwnedDependency(CoolifyConnection connection, String graphId,
            String serviceRef, com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, java.util.function.Function<String, List<CoolifyEnvVar>> environment,
            OwnedDependencyCheckpoint checkpoint) {
        requireExistingDependencyResources(connection, graphId, serviceRef, spec, resources);
        return reconcileOwnedDependency(connection, graphId, serviceRef, spec, resources, environment, checkpoint, true);
    }

    private OwnedDependencyResult reconcileOwnedDependency(CoolifyConnection connection, String graphId,
            String serviceRef, com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, java.util.function.Function<String, List<CoolifyEnvVar>> environment,
            OwnedDependencyCheckpoint checkpoint, boolean existingOnly) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, spec.slot());
        authority.requireSpec(owner, spec);
        authority.requireResources(owner, resources);
        if (resources.applicationCreateIntent() && !hasText(resources.applicationUuid())
            || resources.databaseCreateIntent() && !hasText(resources.databaseUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        if (spec.database() == null && hasText(resources.databaseUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SPEC_DRIFT");
        }
        if (hasText(resources.applicationUuid())) {
            authority.application(owner, resources.applicationUuid());
            var recorded = authority.applicationVolumes(owner, resources.applicationUuid(), resources.volumes(), false);
            authority.requireLiveVolumes(owner, resources.applicationUuid(), recorded, false);
        }
        if (hasText(resources.databaseUuid())) {
            authority.database(owner, resources.databaseUuid());
            var recorded = authority.databaseVolumes(owner, resources.databaseUuid(), false);
            authority.requireLiveVolumes(owner, resources.databaseUuid(), recorded, true);
        }
        var config = connection.config();
        String marker = "ManagedDependency:" + graphId + ":" + spec.slot();
        String dbUuid = resources.databaseUuid();
        if (spec.database() != null) {
            CoolifyDatabaseRuntimeSettings databaseCapacity = CoolifyDatabaseCapacity.of(
                spec.database().capacity().milliCpu(),
                spec.database().capacity().memoryMiB()
            );
            String name = serviceRef + "-db";
            CoolifyDatabaseSummary db = null;
            if (hasText(dbUuid)) {
                db = authority.database(owner, dbUuid);
            }
            if (db == null) {
                if (existingOnly) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
                checkpoint.guard();
                checkpoint.creating("DATABASE");
                List<CoolifyEnvVar> values = environment.apply(null);
                String password = values.stream().filter(v -> "__DATABASE_PASSWORD".equals(v.key()))
                    .map(CoolifyEnvVar::value).findFirst().orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SECRET_MISSING"));
                var databaseRequest = new CoolifyCreatePostgresDatabaseRequest(
                    config.projectUuid(), config.serverUuid(), config.environmentName(), config.environmentUuid(),
                    config.destinationUuid(), name, marker, spec.database().image(), "dependency", password, "dependency",
                    false, false, databaseCapacity);
                db = authority.createDatabase(owner, () -> coolifyApiClient.createPostgresDatabase(connection, databaseRequest));
                dbUuid = db.uuid();
                checkpoint.created("DATABASE", dbUuid);
            }
            authority.database(owner, dbUuid);
            if (!spec.database().image().equals(db.raw().path("image").asText())
                || !"dependency".equals(db.postgresUser()) || !"dependency".equals(db.postgresDatabase())
                || db.raw().path("is_public").asBoolean(true)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            }
            CoolifyDatabaseCapacity.requireReadback(db, databaseCapacity, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            checkpoint.guard();
            var currentDatabase = authority.database(owner, dbUuid);
            String databaseState = upper(currentDatabase.status()).split(":", 2)[0];
            if (java.util.Set.of("EXITED", "STOPPED").contains(databaseState)) {
                coolifyApiClient.startDatabase(connection, dbUuid);
            } else if (!java.util.Set.of("RUNNING", "STARTING", "RESTARTING").contains(databaseState)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_STATE_UNVERIFIED");
            }
            awaitOwnedDatabase(owner, dbUuid);
        }
        String suffix = config.defaultPublicDomainSuffix().replaceFirst("^\\.", "");
        String domain = "https://" + serviceRef + "." + suffix;
        var image = ownedDependencyImage(spec.image());
        var request = new CoolifyCreateDockerImageApplicationRequest(config.projectUuid(), config.serverUuid(),
            config.environmentName(), config.environmentUuid(), image.repository(), image.hashTag(), Integer.toString(spec.port()),
            config.destinationUuid(), serviceRef, marker, domain, true, spec.healthPath(), Integer.toString(spec.port()),
            ownedDependencyRuntimeSettings(spec),
            false, true, false);
        CoolifyApplicationSummary app = null;
        String appUuid = resources.applicationUuid();
        if (hasText(appUuid)) {
            app = authority.application(owner, appUuid);
        }
        if (app == null) {
            if (existingOnly) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
            checkpoint.guard();
            checkpoint.creating("APPLICATION");
            app = authority.createApplication(owner, () -> coolifyApiClient.createDockerImageApplication(connection, request));
            appUuid = app.uuid();
            checkpoint.created("APPLICATION", appUuid);
        }
        authority.application(owner, appUuid);
        if (!image.repository().equals(app.imageRepository()) || !image.hashTag().equals(app.imageTag()) || !domain.equals(app.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        }
        var recordedVolumes = new java.util.LinkedHashMap<>(resources.volumes());
        if (!spec.volumes().stream().map(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Volume::mountPath)
            .collect(java.util.stream.Collectors.toSet()).containsAll(recordedVolumes.keySet())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        for (var volume : spec.volumes()) {
            checkpoint.guard();
            if (existingOnly && !hasText(recordedVolumes.get(volume.mountPath())))
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
            var storage = authority.volume(owner, appUuid, volume.mountPath(), recordedVolumes.get(volume.mountPath()));
            if (!recordedVolumes.containsKey(volume.mountPath())) checkpoint.volume(volume.mountPath(), storage.uuid());
            recordedVolumes.put(volume.mountPath(), storage.uuid());
        }
        authority.requireLiveVolumes(owner, appUuid, authority.applicationVolumes(owner, appUuid, recordedVolumes, false), false);
        checkpoint.guard();
        authority.application(owner, appUuid);
        coolifyApiClient.updateDockerImageApplication(connection, appUuid, request);
        checkpoint.guard();
        authority.application(owner, appUuid);
        coolifyApiClient.updateEnvironmentVariables(connection, appUuid, environment.apply(dbUuid).stream()
            .filter(v -> !"__DATABASE_PASSWORD".equals(v.key())).toList());
        checkpoint.guard();
        authority.application(owner, appUuid);
        CoolifyActionResponse response = coolifyApiClient.start(connection, appUuid, true, true);
        if (response == null || !hasText(response.deploymentUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        CoolifyApplicationSummary observed = awaitOwnedDependencyDeployment(owner, appUuid, response.deploymentUuid());
        if (!"running:healthy".equalsIgnoreCase(observed.status()) || !image.repository().equals(observed.imageRepository())
            || !image.hashTag().equals(observed.imageTag())
            || !domain.equals(observed.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_HEALTH_READBACK_REQUIRED");
        }
        return new OwnedDependencyResult(appUuid, dbUuid, domain);
    }

    public record OwnedDependencyOperatorRestriction(String operatorId, String environmentKey,
            String previousValue, String restrictedValue, int remainingOperators) {
        @Override public String toString() {
            return "OwnedDependencyOperatorRestriction[operatorId=" + operatorId
                + ", remainingOperators=" + remainingOperators + ", configuration=***]";
        }
    }

    /** Reads a strict subset of existing credentials; it never resolves a desired enrollment or creates resources. */
    public OwnedDependencyOperatorRestriction planOwnedDependencyOperatorRemoval(CoolifyConnection connection,
            String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, String bindingId, String operatorId) {
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        var keys = spec.environment().entrySet().stream()
            .filter(entry -> entry.getValue().kind()
                == com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.ValueKind.OPERATORS)
            .map(Map.Entry::getKey).toList();
        if (keys.size() != 1 || !hasText(operatorId) || !hasText(bindingId)) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_REVOCATION_SCOPE_INVALID");
        }
        String current = coolifyApiClient.requireRuntimeEnvironmentValue(connection, resources.applicationUuid(), keys.getFirst());
        List<String> remaining = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String row : current.split(";", -1)) {
            String[] fields = row.split("\\|", -1);
            if (fields.length != 4 || !hasText(fields[0]) || !hasText(fields[1]) || fields[2].length() < 32
                || !bindingId.equals(fields[3]) || !ids.add(fields[0])) {
                throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_EVIDENCE_REQUIRED");
            }
            if (!operatorId.equals(fields[0])) remaining.add(row);
        }
        return new OwnedDependencyOperatorRestriction(operatorId, keys.getFirst(), current,
            String.join(";", remaining), remaining.size());
    }

    /** Changes only one existing inbox credential field, or stops that inbox after lifecycle disable. */
    public void applyOwnedDependencyOperatorRemoval(CoolifyConnection connection, String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, OwnedDependencyOperatorRestriction restriction,
            OwnedDependencyCheckpoint checkpoint) {
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        var keys = spec.environment().entrySet().stream().filter(entry -> entry.getValue().kind()
            == com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.ValueKind.OPERATORS)
            .map(Map.Entry::getKey).toList();
        var remaining = java.util.Arrays.stream(restriction.previousValue().split(";", -1))
            .filter(row -> !restriction.operatorId().equals(row.split("\\|", -1)[0])).toList();
        if (keys.size() != 1 || !keys.getFirst().equals(restriction.environmentKey())
            || restriction.remainingOperators() != remaining.size()
            || !String.join(";", remaining).equals(restriction.restrictedValue())) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_REMOVAL_ONLY_REQUIRED");
        }
        String current = coolifyApiClient.requireRuntimeEnvironmentValue(connection, resources.applicationUuid(),
            restriction.environmentKey());
        if (!current.equals(restriction.previousValue()) && !current.equals(restriction.restrictedValue())) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_CHANGED");
        }
        checkpoint.guard();
        if (restriction.remainingOperators() == 0) {
            disableOwnedDependency(connection, graphId, serviceRef, spec.slot(), resources.applicationUuid(), checkpoint);
            return;
        }
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, spec.slot());
        authority.application(owner, resources.applicationUuid());
        coolifyApiClient.updateEnvironmentVariables(connection, resources.applicationUuid(), List.of(
            new CoolifyEnvVar(restriction.environmentKey(), restriction.restrictedValue(), false, true, false, true)));
        checkpoint.guard();
        authority.application(owner, resources.applicationUuid());
        var started = coolifyApiClient.start(connection, resources.applicationUuid(), true, true);
        if (started == null || !hasText(started.deploymentUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        awaitOwnedDependencyDeployment(owner, resources.applicationUuid(), started.deploymentUuid());
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        verifyOwnedDependencyReady(connection, graphId, serviceRef, spec.slot(), resources.applicationUuid(),
            resources.databaseUuid(), resources.volumes());
        if (!restriction.restrictedValue().equals(coolifyApiClient.requireRuntimeEnvironmentValue(
            connection, resources.applicationUuid(), restriction.environmentKey()))) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_READBACK_FAILED");
        }
    }

    private void requireExistingInboxResources(CoolifyConnection connection, String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources) {
        if (spec.contract() != com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_EXISTING_RESOURCES_REQUIRED");
        }
        requireExistingDependencyResources(connection, graphId, serviceRef, spec, resources);
    }

    private void requireExistingDependencyResources(CoolifyConnection connection, String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        if (resources == null || !hasText(resources.applicationUuid())
            || (spec.database() == null) != !hasText(resources.databaseUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
        }
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, spec.slot());
        authority.requireSpec(owner, spec);
        authority.requireResources(owner, resources);
        var application = authority.application(owner, resources.applicationUuid());
        var image = ownedDependencyImage(spec.image());
        String domain = "https://" + serviceRef + "." + connection.config().defaultPublicDomainSuffix().replaceFirst("^\\.", "");
        if (!resources.applicationUuid().equals(application.uuid())
            || !image.repository().equals(application.imageRepository()) || !image.hashTag().equals(application.imageTag())
            || !domain.equals(application.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        }
        Map<String, String> volumes = resources.volumes() == null ? Map.of() : resources.volumes();
        if (!spec.volumes().stream().map(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Volume::mountPath)
                .collect(java.util.stream.Collectors.toSet()).equals(volumes.keySet())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        authority.requireLiveVolumes(owner, resources.applicationUuid(),
            authority.applicationVolumes(owner, resources.applicationUuid(), volumes, false), false);
        if (hasText(resources.databaseUuid())) {
            var database = authority.database(owner, resources.databaseUuid());
            authority.requireLiveVolumes(owner, resources.databaseUuid(),
                authority.databaseVolumes(owner, resources.databaseUuid(), false), true);
            if (!resources.databaseUuid().equals(database.uuid()) || !spec.database().image().equals(database.raw().path("image").asText())
                || !"dependency".equals(database.postgresUser()) || !"dependency".equals(database.postgresDatabase())
                || database.raw().path("is_public").asBoolean(true)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            }
        }
    }

    private record OwnedDependencyImage(String repository, String hashTag) {}
    private OwnedDependencyImage ownedDependencyImage(String reference) {
        com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.immutableImage(reference);
        int boundary = reference.lastIndexOf("@sha256:");
        // Coolify's Docker Image API stores digests as sha256-<hex> in the image tag/hash field.
        return new OwnedDependencyImage(reference.substring(0, boundary), "sha256-" + reference.substring(boundary + 8));
    }

    public void retireOwnedDependency(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, OwnedDependencyResources resources, OwnedDependencyCheckpoint checkpoint) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireResources(owner, resources);
        Set<String> applicationVolumeUuids = Set.copyOf(resources.volumes().values());
        if (resources.applicationCreateIntent() && !hasText(resources.applicationUuid())
            || resources.databaseCreateIntent() && !hasText(resources.databaseUuid())
            || !hasText(resources.applicationUuid()) && !resources.volumes().isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        if (applicationVolumeUuids.size() != resources.volumes().size()
            || resources.reclaimedVolumeUuids().stream().anyMatch(value -> !resources.volumeReclamationPlan().containsKey(value))
            || resources.volumeReclamationPlan().values().stream().anyMatch(value -> !hasText(value))
            || resources.volumeReclamationPlan().values().stream().distinct().count() != resources.volumeReclamationPlan().size()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
        }
        var application = hasText(resources.applicationUuid()) ? authority.application(owner, resources.applicationUuid(), true)
            : java.util.Optional.<CoolifyApplicationSummary>empty();
        var database = hasText(resources.databaseUuid()) ? authority.database(owner, resources.databaseUuid(), true)
            : java.util.Optional.<CoolifyDatabaseSummary>empty();
        var appVolumes = authority.applicationVolumes(owner, resources.applicationUuid(), resources.volumes(), true);
        var dbVolumes = hasText(resources.databaseUuid()) ? authority.databaseVolumes(owner, resources.databaseUuid(), true)
            : Map.<String, String>of();
        if (application.isPresent()) authority.requireLiveVolumes(owner, resources.applicationUuid(), appVolumes, false);
        if (database.isPresent()) authority.requireLiveVolumes(owner, resources.databaseUuid(), dbVolumes, true);
        var planned = new java.util.LinkedHashMap<>(appVolumes);
        for (var entry : dbVolumes.entrySet()) if (planned.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
        }
        Map<String, String> volumePlan = Map.copyOf(planned);
        if (volumePlan.values().stream().distinct().count() != volumePlan.size()
            || !resources.volumeReclamationPlan().isEmpty() && !resources.volumeReclamationPlan().equals(volumePlan)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
        }
        if (resources.volumeReclamationPlan().isEmpty() && !volumePlan.isEmpty()) {
            checkpoint.guard();
            checkpoint.volumeReclamationPlanned(volumePlan);
        }
        checkpoint.guard();
        authority.beginVolumeDeletion(owner, resources.applicationUuid(), resources.volumes(), resources.databaseUuid());
        if (hasText(resources.applicationUuid())) authority.beginDeletion(owner, OwnedDependencyOwnership.APPLICATION, resources.applicationUuid());
        if (hasText(resources.databaseUuid())) authority.beginDeletion(owner, OwnedDependencyOwnership.DATABASE, resources.databaseUuid());
        Set<String> databaseVolumeUuids = dbVolumes.keySet();
        Set<String> reclaimed = new java.util.HashSet<>();
        TargetIntent targetIntent = ownedDependencyTargetIntent(connection);

        if (hasText(resources.applicationUuid())) {
            if (application.isPresent()) {
                checkpoint.guard();
                authority.application(owner, resources.applicationUuid(), true);
                authority.requireLiveVolumes(owner, resources.applicationUuid(), appVolumes, false);
                coolifyApiClient.delete(connection, resources.applicationUuid(), true, true, false, false);
                awaitCoolifyApplicationDeletion(connection, resources.applicationUuid());
            }
            if (authority.application(owner, resources.applicationUuid(), true).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_DELETION_UNCONFIRMED");
            }
            hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                graphId + ":" + slot + ":application", "application", resources.applicationUuid());
            authority.deleted(owner, OwnedDependencyOwnership.APPLICATION, resources.applicationUuid());
            checkpoint.guard();
            authority.applicationVolumes(owner, resources.applicationUuid(), resources.volumes(), true);
            reclaimed.addAll(reclaimOwnedVolumes(targetIntent, connection, graphId + ":" + slot + ":application",
                volumePlan, applicationVolumeUuids));
            authority.volumesDeleted(owner, resources.applicationUuid(), resources.volumes(), null);
            if (!applicationVolumeUuids.isEmpty()) checkpoint.volumesReclaimed(Set.copyOf(reclaimed));
        }
        if (hasText(resources.databaseUuid())) {
            if (database.isPresent()) {
                checkpoint.guard();
                authority.database(owner, resources.databaseUuid(), true);
                authority.requireLiveVolumes(owner, resources.databaseUuid(), dbVolumes, true);
                coolifyApiClient.deleteDatabase(connection, resources.databaseUuid(), true, true, true, false);
                awaitCoolifyDatabaseDeletion(connection, resources.databaseUuid());
            }
            if (authority.database(owner, resources.databaseUuid(), true).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_DELETION_UNCONFIRMED");
            }
            hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                graphId + ":" + slot + ":database", "database", resources.databaseUuid());
            authority.deleted(owner, OwnedDependencyOwnership.DATABASE, resources.databaseUuid());
            checkpoint.guard();
            authority.databaseVolumes(owner, resources.databaseUuid(), true);
            reclaimed.addAll(reclaimOwnedVolumes(targetIntent, connection, graphId + ":" + slot + ":database",
                volumePlan, databaseVolumeUuids));
            authority.volumesDeleted(owner, resources.applicationUuid(), Map.of(), resources.databaseUuid());
            checkpoint.volumesReclaimed(Set.copyOf(reclaimed));
        }
        if (!reclaimed.equals(volumePlan.keySet())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
        }
    }

    /** Read-only reuse of a completed provisioning checkpoint. Drift never triggers a redeploy. */
    public OwnedDependencyResult verifyOwnedDependencyCheckpoint(CoolifyConnection connection, String graphId,
            String serviceRef, com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, java.util.function.Function<String, List<CoolifyEnvVar>> environment,
            String expectedBaseUrl, OwnedDependencyCheckpoint checkpoint) {
        requireExistingDependencyResources(connection, graphId, serviceRef, spec, resources);
        checkpoint.guard();
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, spec.slot());
        var app = authority.application(owner, resources.applicationUuid());
        var settings = ownedDependencyRuntimeSettings(spec);
        CoolifyApplicationCapacity.requireReadback(app, settings, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        if (!app.fqdn().equals(expectedBaseUrl) || !"dockerimage".equals(app.raw().path("build_pack").asText())
            || !Integer.toString(spec.port()).equals(app.raw().path("ports_exposes").asText())
            || !app.raw().path("health_check_enabled").isBoolean()
            || !app.raw().path("health_check_enabled").asBoolean()
            || !spec.healthPath().equals(app.raw().path("health_check_path").asText())
            || !Integer.toString(spec.port()).equals(app.raw().path("health_check_port").asText())
            || app.raw().path("health_check_interval").asInt(-1) != settings.healthCheckIntervalSeconds()
            || app.raw().path("health_check_timeout").asInt(-1) != settings.healthCheckTimeoutSeconds()
            || app.raw().path("health_check_retries").asInt(-1) != settings.healthCheckRetries()
            || app.raw().path("health_check_start_period").asInt(-1) != settings.healthCheckStartPeriodSeconds()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        }
        if (spec.database() != null) {
            CoolifyDatabaseCapacity.requireReadback(authority.database(owner, resources.databaseUuid()),
                CoolifyDatabaseCapacity.of(spec.database().capacity().milliCpu(), spec.database().capacity().memoryMiB()),
                "DEPENDENCY_DATABASE_SPEC_DRIFT");
        }
        var values = environment.apply(resources.databaseUuid()).stream()
            .filter(value -> !"__DATABASE_PASSWORD".equals(value.key())).toList();
        var keys = new java.util.HashSet<String>();
        for (var value : values) {
            if (value.preview() || !keys.add(value.key()) || !java.util.Objects.equals(value.value(),
                coolifyApiClient.requireRuntimeEnvironmentValue(connection, resources.applicationUuid(), value.key()))) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_ENVIRONMENT_READBACK_FAILED");
            }
        }
        var jobs = coolifyApiClient.applicationDeploymentHistory(connection, resources.applicationUuid());
        var ids = new java.util.HashSet<String>();
        if (jobs.isEmpty() || jobs.stream().anyMatch(job -> !hasText(job.deploymentUuid()) || !ids.add(job.deploymentUuid())
            || !Set.of("finished", "failed", "cancelled").contains(job.status())
            || !hasText(job.createdAt()) || !hasText(job.finishedAt()))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        try {
            var latest = jobs.stream().max(java.util.Comparator.comparing(job -> Instant.parse(job.createdAt()))).orElseThrow();
            var exact = coolifyApiClient.getDeployment(connection, latest.deploymentUuid()).orElseThrow();
            // Coolify exposes the native ID/UUID pair on the detailed job, not the application endpoint.
            String applicationId = exact.raw() == null ? null : exact.raw().path("application").path("id").asText(null);
            if (!latest.deploymentUuid().equals(exact.deploymentUuid())
                || !resources.applicationUuid().equals(exact.applicationUuid()) || !"finished".equals(exact.status())
                || jobs.stream().anyMatch(job -> job.applicationUuid() != null
                    ? !resources.applicationUuid().equals(job.applicationUuid())
                    : job.raw() == null || !hasText(applicationId)
                        || !applicationId.equals(job.raw().path("application_id").asText()))
                || !latest.createdAt().equals(exact.createdAt()) || !latest.finishedAt().equals(exact.finishedAt())
                || Instant.parse(exact.finishedAt()).isBefore(Instant.parse(exact.createdAt()))
                || Instant.parse(exact.finishedAt()).isAfter(Instant.now())) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException unproven) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        checkpoint.guard();
        verifyOwnedDependencyReady(connection, graphId, serviceRef, spec.slot(), resources.applicationUuid(),
            resources.databaseUuid(), resources.volumes());
        return new OwnedDependencyResult(resources.applicationUuid(), resources.databaseUuid(), expectedBaseUrl);
    }

    private CoolifyApplicationRuntimeSettings ownedDependencyRuntimeSettings(
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec) {
        return new CoolifyApplicationRuntimeSettings(10, 5, 18, 180,
            spec.capacity().memoryMiB() + "M", null, null,
            java.math.BigDecimal.valueOf(spec.capacity().milliCpu(), 3).toPlainString(), null);
    }

    /** Exact provider readback used after idempotent service initialization. */
    public void verifyOwnedDependencyReady(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, String applicationUuid, String databaseUuid, Map<String, String> volumes) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        if (!hasText(applicationUuid)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_READBACK_REQUIRED");
        }
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        authority.requireReadyResources(owner, applicationUuid, databaseUuid, volumes);
        var application = authority.application(owner, applicationUuid);
        if (!"running:healthy".equalsIgnoreCase(application.status())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_HEALTH_READBACK_REQUIRED");
        }
        Map<String, String> expectedVolumes = volumes == null ? Map.of() : Map.copyOf(volumes);
        authority.requireLiveVolumes(owner, applicationUuid,
            authority.applicationVolumes(owner, applicationUuid, expectedVolumes, false), false);
        if (hasText(databaseUuid)) {
            var database = authority.database(owner, databaseUuid);
            authority.requireLiveVolumes(owner, databaseUuid, authority.databaseVolumes(owner, databaseUuid, false), true);
            if (!"running:healthy".equalsIgnoreCase(database.status())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_NOT_READY");
            }
        }
    }

    /** Authoritative post-delete readback plus the durable pre-application-delete volume receipt. */
    public void verifyOwnedDependenciesReclaimed(CoolifyConnection connection, String graphId,
            List<com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyResource> resources) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        requireOwnedDependencyAuthority().requireReclamationInventory(graphId, resources);
        TargetIntent targetIntent = ownedDependencyTargetIntent(connection);
        for (var resource : resources) {
            if (resource == null || !hasText(resource.slot()) || !hasText(resource.serviceRef())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_RECLAMATION_EVIDENCE_REQUIRED");
            }
            var authority = requireOwnedDependencyAuthority();
            var owner = authority.context(connection, graphId, resource.serviceRef(), resource.slot());
            authority.requireReclaimedResources(owner, resource);
            if (hasText(resource.applicationUuid()) && authority.application(owner, resource.applicationUuid(), true).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_DELETION_UNCONFIRMED");
            }
            if (hasText(resource.applicationUuid())) {
                authority.requireDeleted(owner, OwnedDependencyOwnership.APPLICATION, resource.applicationUuid());
                hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                    graphId + ":" + resource.slot() + ":application", "application", resource.applicationUuid());
            }
            if (hasText(resource.databaseUuid()) && authority.database(owner, resource.databaseUuid(), true).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_DELETION_UNCONFIRMED");
            }
            if (hasText(resource.databaseUuid())) {
                authority.requireDeleted(owner, OwnedDependencyOwnership.DATABASE, resource.databaseUuid());
                hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                    graphId + ":" + resource.slot() + ":database", "database", resource.databaseUuid());
            }
            Set<String> applicationVolumeUuids = resource.volumes() == null
                ? Set.of() : Set.copyOf(resource.volumes().values());
            if (applicationVolumeUuids.size() != resource.volumes().size()
                || !resource.volumeReclamationPlan().keySet().containsAll(applicationVolumeUuids)
                || !resource.volumeReclamationPlan().keySet().equals(resource.reclaimedVolumeUuids())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
            }
            authority.requireVolumesDeleted(owner, resource.applicationUuid(), resource.volumes(), resource.databaseUuid());
            var expected = new java.util.LinkedHashMap<>(authority.applicationVolumes(owner, resource.applicationUuid(), resource.volumes(), true));
            if (hasText(resource.databaseUuid())) expected.putAll(authority.databaseVolumes(owner, resource.databaseUuid(), true));
            if (!expected.equals(resource.volumeReclamationPlan())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
            }
        }
    }

    private Set<String> reclaimOwnedVolumes(TargetIntent intent, CoolifyConnection connection, String reference,
            Map<String, String> plan, Set<String> volumeUuids) {
        if (volumeUuids.isEmpty()) return Set.of();
        var receipt = hostVolumeReclamationService.reclaimAndVerify(intent, connection.config().serverUuid(), reference,
            volumeUuids.stream().sorted().map(uuid ->
                new CoolifyHostVolumeReclamationService.Volume(uuid, plan.get(uuid), false)).toList());
        if (!receipt.reclaimedUuids().equals(volumeUuids)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
        }
        return receipt.reclaimedUuids();
    }

    private void awaitCoolifyDatabaseDeletion(CoolifyConnection connection, String databaseUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (coolifyApiClient.getDatabase(connection, databaseUuid).isEmpty()) return;
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Coolify database deletion.", error);
            }
        }
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_DELETION_UNCONFIRMED");
    }

    private TargetIntent ownedDependencyTargetIntent(CoolifyConnection connection) {
        var matches = customerSelfServiceProperties.trustedServers().entrySet().stream()
            .filter(entry -> entry.getValue() != null
                && connection.config().serverUuid().equals(entry.getValue().uuid()))
            .map(Map.Entry::getKey).toList();
        if (matches.size() != 1) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_AUTHORITY_UNAVAILABLE");
        }
        return matches.getFirst();
    }

    private OwnedDependencyOwnership requireOwnedDependencyAuthority() {
        if (ownedDependencyOwnership == null) throw OwnedDependencyOwnership.denied();
        return ownedDependencyOwnership;
    }

    private void awaitOwnedDatabase(OwnedDependencyOwnership.Context owner, String uuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        do {
            var db = requireOwnedDependencyAuthority().database(owner, uuid);
            if ("running:healthy".equalsIgnoreCase(db.status())) return;
            try {
                Thread.sleep(Math.max(250, productProvisioningProperties.pollInterval().toMillis()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_WAIT_INTERRUPTED");
            }
        } while (Instant.now().isBefore(deadline));
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_NOT_READY");
    }

    private CoolifyApplicationSummary awaitOwnedDependencyDeployment(OwnedDependencyOwnership.Context owner, String uuid, String deploymentUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        do {
            var application = requireOwnedDependencyAuthority().application(owner, uuid);
            var deployment = coolifyApiClient.getDeployment(owner.connection(), deploymentUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED"));
            if (!uuid.equals(deployment.applicationUuid()) || !deploymentUuid.equals(deployment.deploymentUuid())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_OWNERSHIP_CONFLICT");
            }
            if (coolifyDeploymentFailed(deployment)) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_FAILED");
            if (coolifyDeploymentFinished(deployment) && "running:healthy".equalsIgnoreCase(application.status())) return application;
            try {
                Thread.sleep(Math.max(250, productProvisioningProperties.pollInterval().toMillis()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_WAIT_INTERRUPTED");
            }
        } while (Instant.now().isBefore(deadline));
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_NOT_READY");
    }

    public String ownedDependencyDatabaseJdbc(CoolifyConnection connection, String databaseUuid) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.databaseContext(connection, databaseUuid);
        var database = authority.database(owner, databaseUuid);
        try {
            java.net.URI uri = java.net.URI.create(database.raw().path("internal_db_url").asText());
            if (!("postgres".equals(uri.getScheme()) || "postgresql".equals(uri.getScheme()))
                || !databaseUuid.equals(uri.getHost()) || uri.getPort() != 5432
                || !"/dependency".equals(uri.getRawPath()) || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
            return "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + "/dependency";
        } catch (RuntimeException error) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_ADDRESS_UNVERIFIED");
        }
    }

    public void disableOwnedDependency(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, String applicationUuid, OwnedDependencyCheckpoint checkpoint) {
        var authority = requireOwnedDependencyAuthority();
        var owner = authority.context(connection, graphId, serviceRef, slot);
        if (!hasText(applicationUuid)) {
            authority.requireNoApplication(owner);
            return;
        }
        var app = authority.application(owner, applicationUuid);
        String currentState = upper(app.status()).split(":", 2)[0];
        if (java.util.Set.of("EXITED", "STOPPED").contains(currentState)) return;
        checkpoint.guard();
        authority.application(owner, applicationUuid);
        coolifyApiClient.stop(connection, applicationUuid, false);
        var readback = authority.application(owner, applicationUuid);
        if (!applicationUuid.equals(readback.uuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DISABLE_UNCONFIRMED");
        }
        String state = upper(readback.status()).split(":", 2)[0];
        if (!java.util.Set.of("EXITED", "STOPPED").contains(state)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DISABLE_UNCONFIRMED");
        }
    }







    private record ReconciledRailwayService(
        String projectId,
        String environmentId,
        String serviceId,
        RailwayGraphqlClient.RailwayServiceInstanceSummary instance,
        String publicBaseUrl,
        String deploymentId
    ) {
    }

    private record CoolifyBinding(
        DeploymentTargetProfileEntity profile,
        CoolifyConnection connection
    ) {
    }
}
