package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentBootstrapService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceCatalogueService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerStagingConsumerConfigurationService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class ExperienceKitTestFixture {
    static final String CUSTOMER = "kit-customer";
    static final String OWNER = "kit-owner";
    static final String OFFER = "kit-offer";
    static final String TARGET = "customer-staging";
    static final String PROFILE = "dealership-workspace";
    static final String DEALERSHIP = "dealership-ai-experience";
    static final String GENERIC = "ai-customer-experience";
    static final String TEMPLATE = "mkp-template-simulated-dealership-experience-v1";
    static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    final ExperienceKitDefinitionRepository definitions = mock(ExperienceKitDefinitionRepository.class);
    final CustomerExperienceKitRepository kits = mock(CustomerExperienceKitRepository.class);
    final CustomerOfferEntitlementService access = mock(CustomerOfferEntitlementService.class);
    final CustomerSelfServiceCatalogueService catalogue = mock(CustomerSelfServiceCatalogueService.class);
    final CustomerDeploymentBootstrapService bootstrap = mock(CustomerDeploymentBootstrapService.class);
    final CustomerStagingConsumerConfigurationService staging = mock(CustomerStagingConsumerConfigurationService.class);
    final PlatformCustomerConsumerService consumers = mock(PlatformCustomerConsumerService.class);
    final AIWorkspaceInstallationService workspaces = mock(AIWorkspaceInstallationService.class);
    final AIWorkspaceInstallationRepository installations = mock(AIWorkspaceInstallationRepository.class);
    final MarketplacePluginVersionRepository versions = mock(MarketplacePluginVersionRepository.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final PlatformAuditService audit = mock(PlatformAuditService.class);
    final Map<String, AIWorkspaceInstallationEntity> appEntities = new HashMap<>();
    final Map<String, AIWorkspaceInstallationSummary> appSummaries = new HashMap<>();
    final WorkspaceProfile profile;
    final ExperienceKitDefinitionEntity dealership;
    final ExperienceKitDefinitionEntity generic;
    final ExperienceKitService service;
    CustomerExperienceKitEntity saved;
    int appSequence;

    ExperienceKitTestFixture() {
        profile = profile("dealership");
        dealership = definition(DEALERSHIP, "dealership", List.of(TEMPLATE,
            "mkp-template-dealership-external-systems-v1",
            "mkp-template-dealership-external-systems-managed-inbox-v1"));
        generic = definition(GENERIC, "generic-workspace", List.of("mkp-template-document-knowledge-assistant"));
        when(definitions.findAllByOrderBySortOrderAsc()).thenReturn(List.of(generic, dealership));
        when(definitions.findById(DEALERSHIP)).thenReturn(Optional.of(dealership));
        when(definitions.findById(GENERIC)).thenReturn(Optional.of(generic));
        when(access.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor(CUSTOMER, OWNER));
        var customer = new PlatformCustomerEntity(); customer.setId(CUSTOMER); customer.setStatus("ACTIVE");
        when(entityManager.find(PlatformCustomerEntity.class, CUSTOMER, LockModeType.PESSIMISTIC_WRITE)).thenReturn(customer);
        version("template-version", TEMPLATE);
        when(catalogue.offer(OFFER)).thenReturn(offer(OFFER, "template-version", profile, TARGET, TargetIntent.CUSTOMER_STAGING));
        when(bootstrap.bootstrap(eq(OFFER), any())).thenReturn(new CustomerDeploymentBootstrapContracts.Result(
            "kit-deployment", "kit-tenant", OFFER, "offer-fingerprint", TARGET, 7, "CONFIGURATION_ONLY", "CUSTOMER_POSTGRES"));
        when(consumers.createConsumer(eq(CUSTOMER), any())).thenReturn(consumer("kit-consumer"));
        when(workspaces.create(eq(CUSTOMER), any(), anyString())).thenAnswer(call -> {
            int sequence = ++appSequence;
            var app = workspace("app-entity-" + sequence, "awi-public-" + sequence, "DRAFT", call.getArgument(1));
            remember(app);
            return app;
        });
        when(installations.findById(anyString())).thenAnswer(call -> Optional.ofNullable(appEntities.get(call.getArgument(0))));
        when(workspaces.get(eq(CUSTOMER), anyString())).thenAnswer(call -> appSummaries.get(call.getArgument(1)));
        when(kits.saveAndFlush(any())).thenAnswer(call -> saved = call.getArgument(0));
        when(kits.findByCustomerIdAndOperationKey(eq(CUSTOMER), anyString())).thenAnswer(call ->
            saved != null && saved.getOperationKey().equals(call.getArgument(1)) ? Optional.of(saved) : Optional.empty());
        service = new ExperienceKitService(definitions, kits, access, catalogue, bootstrap, staging, consumers,
            workspaces, installations, versions, mapper, codec, entityManager, audit);
    }

    ExperienceKitContracts.CreateRequest request(String delivery, boolean human) {
        return new ExperienceKitContracts.CreateRequest(DEALERSHIP, " Northfield experience ", OFFER, TARGET, PROFILE,
            "CUSTOMER_POSTGRES", " Northfield AI ", "#2563eb", delivery, "northfield-ai",
            " https://dealer.example.test ", human, "kit-create-1", false, null);
    }

    ExperienceKitContracts.UpdateRequest update(String delivery, boolean human, long revision) {
        return new ExperienceKitContracts.UpdateRequest(" Updated experience ", " Updated AI ", "#18a999",
            delivery, "updated-ai", "https://updated.example.test", human, revision, 4L,
            saved != null && saved.getEmbeddedInstallationEntityId() != null ? 4L : null, false, null);
    }

    OfferSummary offer(String id, String templateVersion, WorkspaceProfile workspace, String target, TargetIntent intent) {
        return new OfferSummary(id, "Reviewed setup", "catalogue-template-label", templateVersion, "offer-fingerprint",
            new EntitlementSummary("grant", id, CUSTOMER, OWNER, EntitlementStatus.ACTIVE, 7, NOW.plusSeconds(3600)),
            List.of(new TargetSummary(target, intent, "CONFIGURATION_READY", Set.of(), null, List.of())),
            List.of(), List.of(), List.of(), Set.of(), List.of(), List.of(workspace), List.of());
    }

    WorkspaceProfile profile(String pack) {
        boolean dealer = "dealership".equals(pack);
        var interaction = mapper.createObjectNode();
        interaction.put("packCode", pack).put("packVersion", dealer ? "1.1.0" : "1.0.0")
            .put("connectionMode", "public-runtime-anonymous").put("connectionProfileCode", "runtime-anonymous-direct")
            .put("connectionProfileVersion", "1.0.0");
        interaction.set("connectionConfiguration", mapper.createObjectNode());
        var config = interaction.putObject("configuration");
        var identity = config.putObject(dealer ? "dealer" : "app").put("assistantLabel", "Original assistant");
        if (dealer) identity.put("id", "northfield-demo");
        var page = config.putObject("page").put("rootSelector", "main").put("contextLabel", "Current page");
        if (dealer) page.put("kind", "auto");
        var capabilities = config.putObject("capabilities");
        if (dealer) {
            capabilities.put("comparison", true).put("testDrive", false).put("callback", false);
            config.putObject("presentation").put("detailBasePath", "/vehicles")
                .putObject("actionNames").put("getVehicle", "dealership_get_vehicle");
        } else {
            capabilities.put("currentPage", true).put("handover", false);
        }
        var groups = config.putObject("toolGroups").put("initialScope", "default");
        var browse = groups.putObject("default").put("label", dealer ? "Browse stock" : "Browse knowledge").put("icon", "search");
        browse.putArray("tools").addObject().put("label", "Search").put("query", dealer ? "Search current stock." : "Search approved knowledge.")
            .put("position", "search").put("mode", "executor").put("icon", "search");
        var context = groups.putObject("contextual").put("label", "Current context").put("icon", "details")
            .put("availableWithoutAttachments", false);
        context.putArray("tools").addObject().put("label", "Details").put("query", "Summarize the attached context.")
            .put("position", "search").put("mode", "executor").put("icon", "details");
        var authentication = mapper.createObjectNode().put("interactionPolicy", "public-with-protected-capabilities")
            .put("profileCode", "supabase").put("profileVersion", "1.0.0");
        authentication.putObject("configuration").put("projectUrl", "https://auth.example.test")
            .put("publishableKey", "sb_publishable_test_only_not_a_real_key").putArray("methods")
            .add("email-password").add("magic-link");
        return new WorkspaceProfile(PROFILE, "Approved workspace", interaction,
            mapper.createObjectNode().put("darkMode", true), authentication);
    }

    ExperienceKitDefinitionEntity definition(String code, String pack, List<String> templates) {
        var entity = new ExperienceKitDefinitionEntity();
        ReflectionTestUtils.setField(entity, "code", code);
        ReflectionTestUtils.setField(entity, "version", "1.0.0");
        ReflectionTestUtils.setField(entity, "definitionJson", codec.write(new ExperienceKitContracts.Definition(
            code, "1.0.0", code, "summary", "description", pack, "Your AI", List.of("Knowledge"), null)));
        ReflectionTestUtils.setField(entity, "templatePluginIdsJson", codec.write(templates));
        return entity;
    }

    void version(String id, String plugin) {
        var version = new MarketplacePluginVersionEntity(); version.setId(id); version.setPluginId(plugin);
        when(versions.findById(id)).thenReturn(Optional.of(version));
    }

    PlatformConsumerSummary consumer(String id) {
        return new PlatformConsumerSummary(id, CUSTOMER, "Northfield AI", "Kit configuration", "ACTIVE",
            null, null, null, null, null, NOW, NOW, 3);
    }

    AIWorkspaceInstallationSummary workspace(String entity, String publicId, String status,
                                               CreateAIWorkspaceInstallationRequest request) {
        return new AIWorkspaceInstallationSummary(entity, publicId, CUSTOMER, request.consumerId(), request.displayName(),
            status, request.experiencePackCode(), request.experiencePackVersion(), request.connectionMode(),
            request.connectionProfileCode(), request.connectionProfileVersion(), request.connectionConfiguration(),
            request.allowedOrigins(), request.configuration(), request.deliveryMode(), request.appSlug(), null,
            request.interactionPolicy(), request.authProfileCode(), request.authProfileVersion(), request.authConfiguration(),
            List.of(), "kit-deployment", null, null, false, List.of(), 4, NOW, NOW, null, null);
    }

    void remember(AIWorkspaceInstallationSummary app) {
        var entity = new AIWorkspaceInstallationEntity(); entity.setId(app.id()); entity.setCustomerId(app.customerId());
        entity.setInstallationId(app.installationId());
        appEntities.put(entity.getId(), entity); appSummaries.put(app.installationId(), app);
    }
}
