package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.OfferSummary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.WorkspaceProfile;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Kit metadata links canonical resources; creation never publishes, applies or activates them. */
@Service
public class ExperienceKitService {
    private final ExperienceKitDefinitionRepository definitions;
    private final CustomerExperienceKitRepository kits;
    private final CustomerOfferEntitlementService access;
    private final CustomerSelfServiceCatalogueService catalogue;
    private final CustomerDeploymentBootstrapService bootstrap;
    private final CustomerStagingConsumerConfigurationService stagingConsumers;
    private final PlatformCustomerConsumerService consumers;
    private final AIWorkspaceInstallationService workspaces;
    private final AIWorkspaceInstallationRepository installations;
    private final MarketplacePluginVersionRepository templateVersions;
    private final ObjectMapper mapper;
    private final CustomerOfferCodec codec;
    private final EntityManager entityManager;
    private final PlatformAuditService audit;

    public ExperienceKitService(ExperienceKitDefinitionRepository definitions, CustomerExperienceKitRepository kits,
        CustomerOfferEntitlementService access, CustomerSelfServiceCatalogueService catalogue,
        CustomerDeploymentBootstrapService bootstrap, CustomerStagingConsumerConfigurationService stagingConsumers,
        PlatformCustomerConsumerService consumers, AIWorkspaceInstallationService workspaces,
        AIWorkspaceInstallationRepository installations, MarketplacePluginVersionRepository templateVersions,
        ObjectMapper mapper, CustomerOfferCodec codec, EntityManager entityManager, PlatformAuditService audit) {
        this.definitions=definitions; this.kits=kits; this.access=access; this.catalogue=catalogue;
        this.bootstrap=bootstrap; this.stagingConsumers=stagingConsumers; this.consumers=consumers;
        this.workspaces=workspaces; this.installations=installations; this.templateVersions=templateVersions;
        this.mapper=mapper; this.codec=codec; this.entityManager=entityManager; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public List<Definition> publicCatalogue() {
        return definitions.findAllByOrderBySortOrderAsc().stream().map(this::definition).toList();
    }

    @Transactional(readOnly=true)
    public List<CatalogueEntry> customerCatalogue() {
        access.requireCustomerActor();
        var offers = catalogue.catalogue();
        return definitions.findAllByOrderBySortOrderAsc().stream()
            .map(d -> new CatalogueEntry(definition(d), offers.stream().filter(o -> matches(d,o)).toList())).toList();
    }

    @Transactional(readOnly=true)
    public List<Summary> list() {
        var actor=access.requireCustomerActor();
        return kits.findByCustomerIdOrderByCreatedAtDesc(actor.customerId()).stream().map(this::summary).toList();
    }

    @Transactional(readOnly=true)
    public Summary get(String id) {
        var actor=access.requireCustomerActor();
        return summary(kits.findByCustomerIdAndId(actor.customerId(),id).orElseThrow(this::notFound));
    }

    @Transactional
    public Summary create(CreateRequest request) {
        var actor=access.requireCustomerActor();
        // Serialize configuration-only creation per owner boundary, including duplicate concurrent requests.
        var customer=entityManager.find(PlatformCustomerEntity.class,actor.customerId(),LockModeType.PESSIMISTIC_WRITE);
        if(customer==null || !"ACTIVE".equals(customer.getStatus())) throw notFound();
        String fingerprint=codec.hash(Map.of("request",request,"ownerUserId",actor.userId()));
        var previous=kits.findByCustomerIdAndOperationKey(actor.customerId(),request.operationKey()).orElse(null);
        if(previous!=null) {
            if(!fingerprint.equals(previous.getRequestFingerprint())) throw conflict("This operation key belongs to another kit setup.");
            return summary(previous);
        }
        var definition=definitions.findById(request.kitCode()).orElseThrow(this::notFound);
        var offer=catalogue.offer(request.offerId());
        if(!matches(definition,offer)) throw badRequest("Select a template belonging to this experience.");
        if(offer.targets().stream().noneMatch(t->request.targetProfileId().equals(t.id()) && t.intent()==TargetIntent.CUSTOMER_STAGING))
            throw badRequest("Start this kit on one of its available customer-staging targets.");
        var profile=offer.workspaceProfiles().stream().filter(p->p.id().equals(request.workspaceProfileId())).findFirst()
            .orElseThrow(()->badRequest("Select an available workspace profile."));
        var d=definition(definition);
        if(!d.experiencePackCode().equals(profile.interaction().path("packCode").asText()))
            throw badRequest("The workspace profile does not match this kit.");
        String id="kit-"+UUID.randomUUID();
        var created=bootstrap.bootstrap(request.offerId(),new CustomerDeploymentBootstrapContracts.Request(
            request.targetProfileId(),request.name().trim(),offer.entitlement().revision(),id,request.aiStateStorageMode()));
        var consumer=consumers.createConsumer(actor.customerId(),new CreatePlatformConsumerRequest(
            id,request.workspaceName().trim(),"AI experience kit configuration",null,null));
        stagingConsumers.configure(request.offerId(),created.deploymentId(),new CustomerStagingConsumerConfigurationContracts.Request(
            consumer.consumerId(),consumer.bindingRevision(),request.targetProfileId(),id+"-bind",List.of()));
        String mode=primaryMode(request.deliveryMode());
        String slug="embedded".equals(mode)?null:slug(request.appSlug(),id);
        var input=profileRequest(consumer.consumerId(),profile,request.workspaceName(),request.brandColor(),
            mode,slug,request.websiteOrigin(),request.humanSupportEnabled());
        var app=workspaces.create(actor.customerId(),input,id+"-app");
        AIWorkspaceInstallationSummary embedded=null;
        if("both".equals(request.deliveryMode())) embedded=workspaces.create(actor.customerId(),
            profileRequest(consumer.consumerId(),profile,request.workspaceName(),request.brandColor(),
                "embedded",null,request.websiteOrigin(),request.humanSupportEnabled()),id+"-embed");
        var now=Instant.now(); var kit=new CustomerExperienceKitEntity();
        kit.setId(id); kit.setCustomerId(actor.customerId()); kit.setKitCode(d.code()); kit.setKitVersion(d.version());
        kit.setName(request.name().trim()); kit.setDeploymentId(created.deploymentId()); kit.setConsumerId(consumer.consumerId());
        kit.setInstallationEntityId(app.id()); kit.setEmbeddedInstallationEntityId(embedded==null?null:embedded.id());
        kit.setDeliveryMode(request.deliveryMode()); kit.setOfferId(request.offerId()); kit.setTargetProfileId(request.targetProfileId());
        kit.setHumanSupportEnabled(request.humanSupportEnabled()); kit.setOperationKey(request.operationKey());
        kit.setRequestFingerprint(fingerprint); kit.setCreatedAt(now); kit.setUpdatedAt(now);
        kits.saveAndFlush(kit);
        audit.record("CUSTOMER_EXPERIENCE_KIT_CREATED","EXPERIENCE_KIT",id,Map.of(
            "kitCode",d.code(),"deploymentId",created.deploymentId(),"consumerId",consumer.consumerId(),
            "installationId",app.installationId(),"deliveryMode",request.deliveryMode(),"creationStatus","CONFIGURATION_ONLY"));
        return summary(kit);
    }

    @Transactional
    public Summary update(String id,UpdateRequest request) {
        var actor=access.requireCustomerActor();
        var kit=kits.findOwnedForUpdate(actor.customerId(),id).orElseThrow(this::notFound);
        if(kit.getRevision()!=request.expectedRevision()) throw conflict("This experience changed. Reload before saving.");
        var current=workspace(kit.getCustomerId(),kit.getInstallationEntityId());
        if(current==null) throw conflict("The linked Engage draft was removed. Open Engage apps to review its configuration.");
        var embedded=workspace(kit.getCustomerId(),kit.getEmbeddedInstallationEntityId());
        if(!Objects.equals(request.expectedWorkspaceRevision(),current.rowVersion())
            || embedded!=null && !Objects.equals(request.expectedEmbeddedWorkspaceRevision(),embedded.rowVersion())
            || embedded==null && request.expectedEmbeddedWorkspaceRevision()!=null)
            throw conflict("An Engage configuration changed. Reload this experience before saving.");
        boolean live="ACTIVE".equals(current.status()) || embedded!=null && "ACTIVE".equals(embedded.status());
        if(live) {
            if(!unchangedAppearance(kit,current,embedded,request))
                throw conflict("An Engage app is live. Use its existing editor to explicitly disable it before changing appearance or delivery.");
            // A kit/team preference is not permission to mutate or disable a live app or Desk service.
            saveMetadata(kit,request);
            return summary(kit);
        }
        String mode=primaryMode(request.deliveryMode());
        String slug="embedded".equals(mode)?null:slug(request.appSlug(),kit.getId());
        var input=existingRequest(current,request.workspaceName(),request.brandColor(),mode,slug,
            request.websiteOrigin(),request.humanSupportEnabled());
        updateWorkspace(kit.getCustomerId(),current,input);
        if("both".equals(request.deliveryMode())) {
            var embeddedInput=existingRequest(embedded==null?current:embedded,request.workspaceName(),request.brandColor(),"embedded",null,
                request.websiteOrigin(),request.humanSupportEnabled());
            if(embedded==null) {
                embedded=workspaces.create(kit.getCustomerId(),embeddedInput,kit.getId()+"-embed-"+kit.getRevision());
                kit.setEmbeddedInstallationEntityId(embedded.id());
            } else updateWorkspace(kit.getCustomerId(),embedded,embeddedInput);
        } else if(embedded!=null && !"DISABLED".equals(embedded.status())) {
            workspaces.disable(kit.getCustomerId(),embedded.installationId());
        }
        saveMetadata(kit,request);
        return summary(kit);
    }

    private void saveMetadata(CustomerExperienceKitEntity kit,UpdateRequest request) {
        kit.setName(request.name().trim()); kit.setHumanSupportEnabled(request.humanSupportEnabled());
        kit.setDeliveryMode(request.deliveryMode()); kit.setUpdatedAt(Instant.now()); kits.saveAndFlush(kit);
        audit.record("CUSTOMER_EXPERIENCE_KIT_UPDATED","EXPERIENCE_KIT",kit.getId(),Map.of(
            "customerId",kit.getCustomerId(),"revision",kit.getRevision(),"deliveryMode",kit.getDeliveryMode()));
    }

    private boolean unchangedAppearance(CustomerExperienceKitEntity kit,AIWorkspaceInstallationSummary app,
        AIWorkspaceInstallationSummary embedded,UpdateRequest request) {
        if(!kit.getDeliveryMode().equals(request.deliveryMode()) || !app.displayName().equals(request.workspaceName().trim())
            || !app.configuration().path("theme").path("primaryColor").asText().equalsIgnoreCase(request.brandColor())) return false;
        if(!"embedded".equals(request.deliveryMode()) && !Objects.equals(app.appSlug(),slug(request.appSlug(),kit.getId()))) return false;
        if(!"standalone".equals(request.deliveryMode())) {
            var website="both".equals(request.deliveryMode())?embedded:app;
            return website!=null && website.allowedOrigins().contains(request.websiteOrigin());
        }
        return true;
    }

    private CreateAIWorkspaceInstallationRequest profileRequest(String consumerId,WorkspaceProfile profile,
        String name,String color,String mode,String slug,String origin,boolean human) {
        var interaction=profile.interaction(); var authentication=profile.authentication();
        var config=object(interaction.path("configuration")).deepCopy();
        if(profile.theme()!=null && profile.theme().isObject()) config.set("theme",profile.theme().deepCopy());
        shape(config,text(interaction,"packCode"),name,color,human);
        return new CreateAIWorkspaceInstallationRequest(consumerId,name.trim(),text(interaction,"packCode"),
            text(interaction,"packVersion"),text(interaction,"connectionMode"),text(interaction,"connectionProfileCode"),
            text(interaction,"connectionProfileVersion"),interaction.path("connectionConfiguration"),origins(mode,origin),
            config,mode,slug,List.of(),text(authentication,"interactionPolicy"),text(authentication,"profileCode"),
            text(authentication,"profileVersion"),authentication.path("configuration"));
    }

    private CreateAIWorkspaceInstallationRequest existingRequest(AIWorkspaceInstallationSummary app,String name,
        String color,String mode,String slug,String origin,boolean human) {
        var config=object(app.configuration()).deepCopy(); shape(config,app.experiencePackCode(),name,color,human);
        return new CreateAIWorkspaceInstallationRequest(app.consumerId(),name.trim(),app.experiencePackCode(),
            app.experiencePackVersion(),app.connectionMode(),app.connectionProfileCode(),app.connectionProfileVersion(),
            app.connectionConfiguration(),existingOrigins(app,mode,origin),config,mode,slug,
            app.domains().stream().filter(d->"CUSTOMER_CUSTOM".equals(d.type())).map(AIWorkspaceDomainSummary::hostname).toList(),
            app.interactionPolicy(),app.authProfileCode(),app.authProfileVersion(),app.authConfiguration());
    }

    private void updateWorkspace(String customer,AIWorkspaceInstallationSummary app,CreateAIWorkspaceInstallationRequest r) {
        workspaces.update(customer,app.installationId(),new UpdateAIWorkspaceInstallationRequest(r.consumerId(),r.displayName(),
            r.experiencePackCode(),r.experiencePackVersion(),r.connectionMode(),r.connectionProfileCode(),r.connectionProfileVersion(),
            r.connectionConfiguration(),r.allowedOrigins(),r.configuration(),r.deliveryMode(),r.appSlug(),r.customDomains(),
            r.interactionPolicy(),r.authProfileCode(),r.authProfileVersion(),r.authConfiguration(),app.rowVersion()));
    }

    private void shape(ObjectNode config,String packCode,String name,String color,boolean human) {
        String identityKey=switch(packCode) {
            case "generic-workspace" -> "app";
            case "dealership" -> "dealer";
            default -> throw conflict("Use the Engage editor for this experience pack's appearance.");
        };
        var identity=object(config.get(identityKey)).deepCopy(); identity.put("assistantLabel",name.trim()); config.set(identityKey,identity);
        var theme=object(config.get("theme")).deepCopy(); theme.put("primaryColor",color); config.set("theme",theme);
        if("generic-workspace".equals(packCode)) {
            var capabilities=object(config.get("capabilities")).deepCopy(); capabilities.put("handover",human); config.set("capabilities",capabilities);
        }
    }

    private List<String> origins(String mode,String origin) {
        if(!"embedded".equals(mode)) return List.of();
        if(origin==null || origin.isBlank()) throw badRequest("Enter the website origin for the embedded workspace.");
        return List.of(origin.trim());
    }

    private List<String> existingOrigins(AIWorkspaceInstallationSummary app,String mode,String origin) {
        var requested=origins(mode,origin);
        if("embedded".equals(mode) && "embedded".equalsIgnoreCase(app.deliveryMode()) && app.allowedOrigins().size()>1) {
            if(!app.allowedOrigins().contains(requested.getFirst()))
                throw conflict("This app has multiple website origins. Review their changes in the Engage editor.");
            return app.allowedOrigins();
        }
        return requested;
    }

    private String primaryMode(String mode) { return "both".equals(mode)?"standalone":mode; }
    private String slug(String value,String id) { return value==null||value.isBlank()?"workspace-"+id.substring(4,16):value.trim(); }

    private boolean matches(ExperienceKitDefinitionEntity definition,OfferSummary offer) {
        var version=templateVersions.findById(offer.templateVersionId()).orElse(null);
        if(version==null) return false;
        try { return mapper.readValue(definition.getTemplatePluginIdsJson(),new TypeReference<List<String>>(){}).contains(version.getPluginId()); }
        catch(Exception e) { throw new IllegalStateException("Invalid kit template catalogue",e); }
    }

    private Definition definition(ExperienceKitDefinitionEntity entity) { return codec.read(entity.getDefinitionJson(),Definition.class); }
    private ObjectNode object(JsonNode node) { return node!=null&&node.isObject()?(ObjectNode)node:mapper.createObjectNode(); }
    private String text(JsonNode node,String field) {
        if(!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw conflict("The selected workspace profile is incomplete.");
        return node.path(field).asText();
    }
    private AIWorkspaceInstallationSummary workspace(String customer,String entityId) {
        if(entityId==null) return null;
        var entity=installations.findById(entityId).orElse(null);
        if(entity==null) return null;
        if(!customer.equals(entity.getCustomerId())) throw notFound();
        return workspaces.get(customer,entity.getInstallationId());
    }
    private Summary summary(CustomerExperienceKitEntity k) {
        var app=workspace(k.getCustomerId(),k.getInstallationEntityId());
        var embedded=workspace(k.getCustomerId(),k.getEmbeddedInstallationEntityId());
        return new Summary(k.getId(),k.getKitCode(),k.getKitVersion(),k.getName(),k.getDeploymentId(),k.getConsumerId(),
            app==null?null:app.installationId(),k.getOfferId(),k.getTargetProfileId(),k.isHumanSupportEnabled(),k.getRevision(),
            k.getCreatedAt(),k.getUpdatedAt(),app,embedded==null?null:embedded.installationId(),embedded,k.getDeliveryMode());
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(NOT_FOUND,"Experience not found."); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(CONFLICT,message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(BAD_REQUEST,message); }
}
