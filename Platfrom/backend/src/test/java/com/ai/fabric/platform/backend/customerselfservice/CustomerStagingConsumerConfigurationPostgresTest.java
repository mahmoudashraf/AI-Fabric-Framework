package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.customerselfservice.entity.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerConfigurationContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual application, V206 PostgreSQL schema, authorization, binding/projection, JPA locks and rollback.
 * Only immutable-composition/placement probes are synthetic; no provider, model or hosted connection. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerStagingConsumerConfigurationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl); p.add("spring.datasource.username",POSTGRES::getUsername);
        p.add("spring.datasource.password",POSTGRES::getPassword); p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.flyway.locations",()->"classpath:db/migration"); p.add("spring.flyway.target",()->"206");
    }
    @Autowired CustomerStagingConsumerConfigurationService service;
    @Autowired PlatformCustomerConsumerService genericConsumers;
    @Autowired CustomerOfferCodec codec;
    @Autowired CustomerSelfServiceOperationBoundary offerBoundary;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformTenantRepository tenants;
    @Autowired PlatformConsumerRepository consumers;
    @Autowired PlatformConsumerBindingHistoryRepository history;
    @Autowired AIWorkspaceInstallationRepository apps;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired DeploymentProviderCredentialRepository credentials;
    @Autowired DeploymentProviderResourceHandleRepository handles;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository entitlements;
    @Autowired CustomerOfferDeploymentRepository associations;
    @Autowired CustomerEntitlementCommandRepository commands;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;
    @MockBean CustomerReviewedCompositionService composition;
    @MockBean CustomerTargetPlacementService placement;
    private String customerId,ownerId,email,tenantId,offerId,depId,consumerId,consumerEntityId,appId,grantId,draftId;
    private Instant now;

    @BeforeEach void fixture() {
        now=Instant.now(); String suffix=UUID.randomUUID().toString().substring(0,8);
        customerId="customer-"+suffix; ownerId="owner-"+suffix; email=suffix+"@test.invalid";
        tenantId="tenant-"+suffix;offerId="offer-"+suffix;depId="deployment-"+suffix;
        consumerId="public-app-"+suffix;consumerEntityId="consumer-"+suffix;appId="awi-"+suffix;
        grantId="grant-"+suffix;draftId="draft-"+suffix;
        var customer=new PlatformCustomerEntity();customer.setId(customerId);customer.setName("Customer");customer.setSlug(suffix);
        customer.setStatus("ACTIVE");customer.setCreatedAt(now);customer.setUpdatedAt(now);customers.saveAndFlush(customer);
        var user=new PlatformUserEntity();user.setId(ownerId);user.setEmail(email);user.setDisplayName("Owner");
        user.setPasswordHash("synthetic-not-a-password");user.setRole("CUSTOMER_ADMIN");user.setStatus("ACTIVE");
        user.setCustomerId(customerId);user.setCreatedAt(now);user.setUpdatedAt(now);users.saveAndFlush(user);
        var tenant=new PlatformTenantEntity();tenant.setId(tenantId);tenant.setCustomerId(customerId);tenant.setName("Tenant");
        tenant.setSlug(suffix);tenant.setStatus("ACTIVE");tenant.setCreatedAt(now);tenant.setUpdatedAt(now);tenants.saveAndFlush(tenant);
        var credential=new DeploymentProviderCredentialEntity();credential.setId("PRIVATE_CREDENTIAL_REF");
        credential.setName("Synthetic local test credential");credential.setProviderType(DeploymentProviderType.COOLIFY);
        credential.setSecretRef("SYNTHETIC_NEVER_RESOLVED_TEST_REFERENCE");credential.setStatus("ACTIVE");
        credential.setCreatedAt(now);credential.setUpdatedAt(now);credentials.saveAndFlush(credential);
        var profile=CustomerOfferTestFixtures.profile("staging-target","synthetic-server");profile.setName("Staging");
        profile.setCreatedAt(now);profile.setUpdatedAt(now);targets.saveAndFlush(profile);
        var definition=reviewedDefinition();
        offers.saveAndFlush(new CustomerOfferEntity(offerId,codec.write(definition),codec.hash(definition),"synthetic-review","admin",now));
        var grant=new CustomerOfferEntitlementEntity();grant.setId(grantId);grant.setCustomerId(customerId);grant.setOfferId(offerId);
        grant.setOwnerUserId(ownerId);grant.setStatus(EntitlementStatus.ACTIVE);grant.setRevision(4);grant.setExpiresAt(now.plusSeconds(3600));
        grant.setCreatedAt(now);grant.setUpdatedAt(now);grant.setInvitationReference("synthetic-invitation");entitlements.saveAndFlush(grant);
        var dep=new DeploymentEntity();dep.setId(depId);dep.setName("New configuration");dep.setCustomerId(customerId);dep.setTenantId(tenantId);
        dep.setTemplateId(definition.templateId());dep.setBehaviorType("AI_ONLY");dep.setEnvironmentName("staging");dep.setStatus("DRAFT");
        dep.setCreatedAt(now);dep.setUpdatedAt(now);deployments.saveAndFlush(dep);
        var draft=new DeploymentDraftEntity();draft.setId(draftId);draft.setDeploymentId(depId);draft.setStatus("DRAFT");
        draft.setRevisionNumber(1);draft.setActionsConfigJson("{}");draft.setEntityConfigJson("{}");
        draft.setRoutingConfigJson("{}");draft.setPromptConfigJson("{}");draft.setProviderConfigJson("{}");
        draft.setSecurityConfigJson("{\"corsAllowedOrigins\":\"https://manual.test.invalid\",\"unrelated\":true}");
        draft.setCreatedAt(now);draft.setUpdatedAt(now);drafts.saveAndFlush(draft);
        dep.setActiveDraftId(draftId);deployments.saveAndFlush(dep);
        associations.saveAndFlush(new CustomerOfferDeploymentEntity(depId,customerId,tenantId,offerId,"staging-target",
            codec.hash(definition),4,"bootstrap-"+suffix,now));
        var consumer=new PlatformConsumerEntity();consumer.setId(consumerEntityId);consumer.setCustomerId(customerId);
        consumer.setConsumerId(consumerId);consumer.setDisplayName("Original app consumer");consumer.setDescription("Preserve this");
        consumer.setStatus("ACTIVE");consumer.setBindingRevision(2);consumer.setCreatedAt(now);consumer.setUpdatedAt(now);consumers.saveAndFlush(consumer);
        createApp(appId,"https://app.test.invalid");
        when(placement.requireFreshEvidence(any(),any())).thenReturn(new CustomerPlacementEvidenceEntity("synthetic-placement",
            "staging-target",TargetIntent.CUSTOMER_STAGING,"staging-hash","server","127.0.0.1","destination",now.minusSeconds(1),now.plusSeconds(60)));
        authenticate();
    }

    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private void authenticate(){CustomerOfferTestFixtures.authenticate(email,PlatformRole.CUSTOMER_ADMIN,"SESSION");}
    private Request request(){return new Request(consumerId,2,4,"configure-existing",List.of(new DraftWorkspaceExpectation(appId,0)));}
    private PlatformConsumerSummary configure(){return service.configure(offerId,depId,request());}

    @Test void configurationOnlyAssignmentProjectsExactOriginsAndPreservesEveryAppField() throws Exception {
        String original=appRow();String offer=offers.findById(offerId).orElseThrow().getDefinitionJson();
        var result=configure();
        assertThat(result.consumerId()).isEqualTo(consumerId);assertThat(result.boundDeploymentId()).isEqualTo(depId);
        assertThat(result.boundReleaseId()).isNull();assertThat(result.boundTargetProfileId()).isEqualTo("staging-target");
        assertThat(result.bindingRevision()).isEqualTo(3);assertThat(appRow()).isEqualTo(original);
        var security=new com.fasterxml.jackson.databind.ObjectMapper().readTree(drafts.findById(draftId).orElseThrow().getSecurityConfigJson());
        assertThat(security.path("corsAllowedOrigins").asText()).isEqualTo("https://manual.test.invalid,https://app.test.invalid");
        assertThat(security.path("aiWorkspaceManagedCorsOrigins").get(0).asText()).isEqualTo("https://app.test.invalid");
        assertThat(security.path("unrelated").asBoolean()).isTrue();
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).hasSize(1);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isPresent();
        assertThat(offers.findById(offerId).orElseThrow().getDefinitionJson()).isEqualTo(offer);
        assertThat(entitlements.findById(grantId).orElseThrow().getRevision()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_deployment_releases WHERE deployment_id=?",Long.class,depId)).isZero();
    }

    @Test void identicalRetryReturnsExactReceiptWithoutAnotherProjectionHistoryAuditOrRevision() {
        var first=configure();String draft=jdbc.queryForObject("SELECT row_to_json(d)::text FROM platform_deployment_drafts d WHERE id=?",String.class,draftId);
        long audits=jdbc.queryForObject("SELECT count(*) FROM platform_audit_events",Long.class);
        assertThat(configure()).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT row_to_json(d)::text FROM platform_deployment_drafts d WHERE id=?",String.class,draftId)).isEqualTo(draft);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_events",Long.class)).isEqualTo(audits);
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).hasSize(1);
    }

    @Test void changedPayloadForTheSameKeyIsConflictNotAnotherBinding() {
        configure();var changed=new Request(consumerId,3,4,request().operationKey(),request().expectedDraftWorkspaces());
        assertThatThrownBy(()->service.configure(offerId,depId,changed)).isInstanceOf(CustomerSelfServiceException.class);
        assertThat(consumers.findById(consumerEntityId).orElseThrow().getBindingRevision()).isEqualTo(3);
    }

    @Test void existingDifferentCommandUsingSameEntitlementKeyConflicts() {
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-"+UUID.randomUUID(),grantId,request().operationKey(),
            "different-command","BOOTSTRAP_CONFIG",email,"{}",now));
        assertThatThrownBy(this::configure).isInstanceOf(CustomerSelfServiceException.class);assertUnbound();
    }

    @ParameterizedTest @ValueSource(strings={"revoked","expired","owner-disabled","customer-disabled"})
    void completedRetryStillRequiresCurrentEligibleOwnerAndEntitlement(String fault) {
        configure();faultAuthority(fault);assertThatThrownBy(this::configure).isInstanceOf(CustomerSelfServiceException.class);
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"revoked","expired","owner-disabled","customer-disabled"})
    void firstCommandRequiresCurrentEligibleOwnerAndEntitlement(String fault) {
        faultAuthority(fault);assertThatThrownBy(this::configure).isInstanceOf(CustomerSelfServiceException.class);assertUnbound();
    }

    @ParameterizedTest @ValueSource(strings={"binding-revision","entitlement-revision","workspace-revision","missing-workspace","extra-workspace"})
    void exactExpectedRevisionsAndFullWorkspaceSetAreMandatory(String fault) {
        var r=switch(fault){
            case "binding-revision"->new Request(consumerId,1,4,"configure-existing",request().expectedDraftWorkspaces());
            case "entitlement-revision"->new Request(consumerId,2,3,"configure-existing",request().expectedDraftWorkspaces());
            case "workspace-revision"->new Request(consumerId,2,4,"configure-existing",List.of(new DraftWorkspaceExpectation(appId,1)));
            case "missing-workspace"->new Request(consumerId,2,4,"configure-existing",List.of());
            case "extra-workspace"->new Request(consumerId,2,4,"configure-existing",List.of(new DraftWorkspaceExpectation(appId,0),new DraftWorkspaceExpectation("nonexistent-app",0)));
            default->throw new AssertionError(fault);
        };
        assertThatThrownBy(()->service.configure(offerId,depId,r)).isInstanceOf(CustomerSelfServiceException.class);assertUnbound();
    }

    @ParameterizedTest @ValueSource(strings={"active-app","activated-before","disabled-app","disabled-before","bound-deployment","bound-target","consumer-disabled","archived","runtime","connector","published-draft","managed-handle"})
    void configurationRouteNeverMovesAnAssignmentOrAllowsRuntimeWorkspaceOrProviderEffects(String fault) {
        switch(fault){
            case "active-app"->jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE' WHERE id=?",appId);
            case "activated-before"->jdbc.update("UPDATE ai_workspace_installations SET activated_at=current_timestamp WHERE id=?",appId);
            case "disabled-app"->jdbc.update("UPDATE ai_workspace_installations SET status='DISABLED' WHERE id=?",appId);
            case "disabled-before"->jdbc.update("UPDATE ai_workspace_installations SET disabled_at=current_timestamp WHERE id=?",appId);
            case "bound-deployment"->jdbc.update("UPDATE platform_consumers SET bound_deployment_id=? WHERE id=?",depId,consumerEntityId);
            case "bound-target"->jdbc.update("UPDATE platform_consumers SET bound_target_profile_id='staging-target' WHERE id=?",consumerEntityId);
            case "consumer-disabled"->jdbc.update("UPDATE platform_consumers SET status='DISABLED' WHERE id=?",consumerEntityId);
            case "archived"->jdbc.update("UPDATE platform_deployments SET archived_at=current_timestamp WHERE id=?",depId);
            case "runtime"->jdbc.update("UPDATE platform_deployments SET runtime_base_url='https://runtime.test.invalid' WHERE id=?",depId);
            case "connector"->jdbc.update("UPDATE platform_deployments SET connector_base_url='https://connector.test.invalid' WHERE id=?",depId);
            case "published-draft"->jdbc.update("UPDATE platform_deployment_drafts SET status='PUBLISHED' WHERE id=?",draftId);
            case "managed-handle"->handle("RUNTIME_APPLICATION","CREATING","{}");
            default->throw new AssertionError(fault);
        }
        assertThatThrownBy(this::configure).isInstanceOf(RuntimeException.class);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).isEmpty();
    }

    @Test void configurationOnlyCustomerManagedS3BindingIsPreservedNotMisclassifiedAsProvisionedRuntime() {
        var h=handle("EXTERNAL_DOCUMENT_STORAGE_BINDING","CONFIGURED",
            "{\"storageOwnership\":\"CUSTOMER_MANAGED\",\"readOnly\":true,\"deleteSourceOnRemoval\":false}");
        String before=jdbc.queryForObject("SELECT row_to_json(h)::text FROM deployment_provider_resource_handles h WHERE id=?",String.class,h.getId());
        configure();assertThat(jdbc.queryForObject("SELECT row_to_json(h)::text FROM deployment_provider_resource_handles h WHERE id=?",String.class,h.getId())).isEqualTo(before);
    }

    @Test void projectionFailureRollsBackBindingHistoryCommandAndDraftAsOneTransaction() {
        jdbc.update("UPDATE platform_deployment_drafts SET security_config_json='{\"corsAllowedOrigins\":\"https://manual.test.invalid\",\"aiWorkspaceManagedCorsOrigins\":7}' WHERE id=?",draftId);
        String app=appRow(),draft=drafts.findById(draftId).orElseThrow().getSecurityConfigJson();
        assertThatThrownBy(this::configure).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            e->assertThat(e.getReason()).isEqualTo("AI Workspace origin projection metadata requires reconciliation."));assertUnbound();
        assertThat(appRow()).isEqualTo(app);assertThat(drafts.findById(draftId).orElseThrow().getSecurityConfigJson()).isEqualTo(draft);
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).isEmpty();
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
    }

    @Test void completeTwoDraftWorkspaceSetProjectsBothOriginsWithoutEditingEitherApp() throws Exception {
        String other=appId+"-other";createApp(other,"https://second.test.invalid");String before=appRow();
        var result=service.configure(offerId,depId,new Request(consumerId,2,4,"configure-two",List.of(
            new DraftWorkspaceExpectation(other,0),new DraftWorkspaceExpectation(appId,0))));
        assertThat(result.bindingRevision()).isEqualTo(3);assertThat(appRow()).isEqualTo(before);
        var security=codec.read(drafts.findById(draftId).orElseThrow().getSecurityConfigJson(),com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(security.path("corsAllowedOrigins").asText()).contains("https://app.test.invalid","https://second.test.invalid");
        assertThat(apps.findById(other).orElseThrow().getRowVersion()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void concurrentSameOrDifferentKeysCannotAssignTwice(boolean sameKey) throws Exception {
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->concurrent(start,request()));
            var r=sameKey?request():new Request(consumerId,2,4,"second-key",request().expectedDraftWorkspaces());
            var second=pool.submit(()->concurrent(start,r));start.countDown();var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            if(sameKey){assertThat(results).allMatch(x->x instanceof PlatformConsumerSummary);assertThat(results.get(0)).isEqualTo(results.get(1));}
            else{assertThat(results).filteredOn(x->x instanceof PlatformConsumerSummary).hasSize(1);assertThat(results).filteredOn(x->x instanceof CustomerSelfServiceException).hasSize(1);}
        }
        assertThat(consumers.findById(consumerEntityId).orElseThrow().getBindingRevision()).isEqualTo(3);
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"consumer","deployment","app","entitlement"})
    void firstLevelCachedRowsCannotHideASeparatelyCommittedChangeBeforeTheLock(String fault) {
        var tx=new TransactionTemplate(manager);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            consumers.findById(consumerEntityId).orElseThrow();deployments.findById(depId).orElseThrow();
            apps.findById(appId).orElseThrow();entitlements.findById(grantId).orElseThrow();
            try(var pool=Executors.newSingleThreadExecutor()){
                pool.submit(()->{switch(fault){
                    case "consumer"->jdbc.update("UPDATE platform_consumers SET bound_deployment_id=? WHERE id=?",depId,consumerEntityId);
                    case "deployment"->jdbc.update("UPDATE platform_deployments SET runtime_base_url='https://changed.test.invalid' WHERE id=?",depId);
                    case "app"->jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE' WHERE id=?",appId);
                    case "entitlement"->jdbc.update("UPDATE platform_customer_offer_entitlements SET status='REVOKED' WHERE id=?",grantId);
                    default->throw new AssertionError(fault);
                }}).get(15,TimeUnit.SECONDS);
            }catch(Exception e){throw new IllegalStateException(e);}
            configure();
        })).isInstanceOf(RuntimeException.class);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
    }

    @Test void genericBindingRouteStillRejectsReviewedOfferAssignment() {
        assertThatThrownBy(()->genericConsumers.updateBinding(customerId,consumerId,
            new UpdatePlatformConsumerBindingRequest(depId,null,"staging-target","must not bypass",2L,"generic-key")))
            .isInstanceOf(RuntimeException.class);assertUnbound();
    }

    @ParameterizedTest @ValueSource(strings={"foreign-consumer","foreign-deployment","foreign-app","wrong-offer","production-bootstrap"})
    void currentOwnedAssociationCustomerAndBootstrapStagingIntentAreNotCallerSelectable(String fault) {
        var foreign=new PlatformCustomerEntity();foreign.setId(customerId+"-foreign");foreign.setName("Foreign");
        foreign.setSlug(customerId+"-foreign");foreign.setStatus("ACTIVE");foreign.setCreatedAt(now);foreign.setUpdatedAt(now);customers.saveAndFlush(foreign);
        switch(fault){
            case "foreign-consumer"->jdbc.update("UPDATE platform_consumers SET customer_id=? WHERE id=?",foreign.getId(),consumerEntityId);
            case "foreign-deployment"->jdbc.update("UPDATE platform_deployments SET customer_id=? WHERE id=?",foreign.getId(),depId);
            case "foreign-app"->jdbc.update("UPDATE ai_workspace_installations SET customer_id=? WHERE id=?",foreign.getId(),appId);
            case "wrong-offer"->{ }
            case "production-bootstrap"->{
                var p=CustomerOfferTestFixtures.profile("production-target","synthetic-production");p.setName("Production");p.setCreatedAt(now);p.setUpdatedAt(now);targets.saveAndFlush(p);
                jdbc.update("UPDATE platform_customer_offer_deployments SET bootstrap_target_profile_id='production-target' WHERE deployment_id=?",depId);
            }
            default->throw new AssertionError(fault);
        }
        assertThatThrownBy(()->service.configure(fault.equals("wrong-offer")?"unrelated-offer":offerId,depId,request()))
            .isInstanceOf(CustomerSelfServiceException.class);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).isEmpty();
    }

    @Test void anotherActiveCustomerAdminInTheSameCustomerIsNotTheEntitledOwner() {
        var user=new PlatformUserEntity();String email2="other-"+email;user.setId(ownerId+"-other");user.setEmail(email2);
        user.setDisplayName("Other admin");user.setPasswordHash("synthetic");user.setCustomerId(customerId);
        user.setRole("CUSTOMER_ADMIN");user.setStatus("ACTIVE");user.setCreatedAt(now);user.setUpdatedAt(now);users.saveAndFlush(user);
        CustomerOfferTestFixtures.authenticate(email2,PlatformRole.CUSTOMER_ADMIN,"SESSION");
        assertThatThrownBy(this::configure).isInstanceOf(CustomerSelfServiceException.class);assertUnbound();
    }

    @Test void customerApiKeyIsNotAnOwnerSessionEvenWithCustomerAdminRole() {
        CustomerOfferTestFixtures.authenticate(email,PlatformRole.CUSTOMER_ADMIN,"API_KEY");
        assertThatThrownBy(this::configure).isInstanceOf(CustomerSelfServiceException.class);assertUnbound();
    }

    @ParameterizedTest @ValueSource(strings={"origin","interaction-profile","theme-profile","auth-profile"})
    void storedDraftMustPassNormalWorkspaceSettingAndAuthenticationAuthorityForTheDestination(String fault) {
        switch(fault){
            case "origin"->jdbc.update("UPDATE ai_workspace_installations SET allowed_origins_json='[\"https://not-approved.test.invalid\"]' WHERE id=?",appId);
            case "interaction-profile"->jdbc.update("UPDATE ai_workspace_installations SET connection_configuration_json='{\"differentProfile\":true}' WHERE id=?",appId);
            case "theme-profile"->jdbc.update("UPDATE ai_workspace_installations SET configuration_json='{\"preserve\":true,\"theme\":{\"unapproved\":true}}' WHERE id=?",appId);
            case "auth-profile"->jdbc.update("UPDATE ai_workspace_installations SET auth_configuration_json='{\"unapprovedAuth\":true}' WHERE id=?",appId);
            default->throw new AssertionError(fault);
        }
        String app=appRow(),draft=drafts.findById(draftId).orElseThrow().getSecurityConfigJson();
        long audits=jdbc.queryForObject("SELECT count(*) FROM platform_audit_events",Long.class);
        assertThatThrownBy(this::configure).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            e->assertThat(e.code()).isEqualTo(CustomerSelfServiceException.Code.SETTING_FORBIDDEN));
        assertUnbound();assertThat(appRow()).isEqualTo(app);
        assertThat(drafts.findById(draftId).orElseThrow().getSecurityConfigJson()).isEqualTo(draft);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_events",Long.class)).isEqualTo(audits);
    }

    @Test void publishedImmutableVersionBlocksConfigurationEvenWithANewEditableDraftAndNoActiveVersion() {
        var publishedDraft=drafts.findById(draftId).orElseThrow();publishedDraft.setStatus("PUBLISHED");drafts.saveAndFlush(publishedDraft);
        var version=new DeploymentVersionEntity();org.springframework.beans.BeanUtils.copyProperties(publishedDraft,version);
        version.setId("version-"+UUID.randomUUID());version.setSourceDraftId(draftId);version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");version.setConfigHash("1".repeat(64));version.setPublishedAt(now);
        version.setActionsArtifactYaml("{}");version.setEntityArtifactYaml("{}");version.setRoutingArtifactYaml("{}");version.setManifestJson("{}");
        versions.saveAndFlush(version);
        var next=new DeploymentDraftEntity();org.springframework.beans.BeanUtils.copyProperties(publishedDraft,next);
        next.setId(draftId+"-next");next.setRevisionNumber(2);next.setStatus("DRAFT");drafts.saveAndFlush(next);
        var deployment=deployments.findById(depId).orElseThrow();deployment.setActiveDraftId(next.getId());deployments.saveAndFlush(deployment);
        assertThat(deployment.getActiveVersionId()).isNull();assertThat(deployment.getStatus()).isEqualTo("DRAFT");
        String app=appRow(),security=next.getSecurityConfigJson();
        assertThatThrownBy(this::configure).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            e->assertThat(e.code()).isEqualTo(CustomerSelfServiceException.Code.RELEASE_IN_PROGRESS));
        assertUnbound();assertThat(appRow()).isEqualTo(app);
        assertThat(drafts.findById(next.getId()).orElseThrow().getSecurityConfigJson()).isEqualTo(security);
        assertThat(commands.findByEntitlementIdAndOperationKey(grantId,request().operationKey())).isEmpty();
        assertThat(history.findByConsumerEntityIdOrderByCreatedAtDesc(consumerEntityId)).isEmpty();
    }

    private Definition reviewedDefinition() {
        var base=CustomerOfferTestFixtures.definition();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var interaction=mapper.createObjectNode().put("packCode","synthetic-pack").put("packVersion","1")
            .put("connectionMode",AIWorkspaceConnectionMode.values()[0].name()).put("connectionProfileCode","synthetic-profile")
            .put("connectionProfileVersion","1").put("deliveryMode",AIWorkspaceDeliveryMode.values()[0].name());
        interaction.set("connectionConfiguration",mapper.createObjectNode().put("preserve",true));
        interaction.set("configuration",mapper.createObjectNode().put("preserve",true));
        var auth=mapper.createObjectNode().put("profileCode","synthetic-auth").put("profileVersion","1")
            .put("interactionPolicy",AIWorkspaceInteractionPolicy.values()[0].name());
        auth.set("configuration",mapper.createObjectNode().put("preserve",true));
        var settings=List.of(new SettingRule(Setting.DEPLOYMENT_NAME,ValueType.TEXT,100,Set.of()),
            new SettingRule(Setting.APP_TITLE,ValueType.TEXT,100,Set.of()),
            new SettingRule(Setting.INTERACTION_PROFILE_ID,ValueType.CHOICE,100,Set.of(offerBoundary.profileId("workspace",interaction))),
            new SettingRule(Setting.THEME_PROFILE_ID,ValueType.CHOICE,100,Set.of(offerBoundary.profileId("theme",null))),
            new SettingRule(Setting.AUTH_PROFILE_ID,ValueType.CHOICE,100,Set.of(offerBoundary.profileId("auth",auth))),
            new SettingRule(Setting.APP_DOMAIN,ValueType.CHOICE,255,Set.of("https://app.test.invalid","https://second.test.invalid")));
        return new Definition(base.schemaVersion(),base.name(),base.templateId(),base.template(),base.plugins(),base.artifacts(),
            base.targets(),base.operations(),settings,base.bindingSlots(),base.documentAudiences(),base.admissionPrerequisites(),
            base.limits(),base.topology(),base.approvedPolicyRef(),base.operatingPolicyRef(),base.identityPolicyRef(),base.storagePolicyRef());
    }

    private Object concurrent(CountDownLatch start,Request request)throws Exception{
        start.await();authenticate();try{return service.configure(offerId,depId,request);}catch(RuntimeException e){return e;}finally{SecurityContextHolder.clearContext();}
    }
    private void faultAuthority(String fault){switch(fault){
        case "revoked"->jdbc.update("UPDATE platform_customer_offer_entitlements SET status='REVOKED' WHERE id=?",grantId);
        case "expired"->jdbc.update("UPDATE platform_customer_offer_entitlements SET expires_at=? WHERE id=?",java.sql.Timestamp.from(now.minusSeconds(1)),grantId);
        case "owner-disabled"->jdbc.update("UPDATE platform_users SET status='DISABLED' WHERE id=?",ownerId);
        case "customer-disabled"->jdbc.update("UPDATE platform_customers SET status='DISABLED' WHERE id=?",customerId);
        default->throw new AssertionError(fault);
    }}
    private void assertUnbound(){var c=consumers.findById(consumerEntityId).orElseThrow();assertThat(c.getBoundDeploymentId()).isNull();assertThat(c.getBindingRevision()).isEqualTo(2);}
    private String appRow(){return jdbc.queryForObject("SELECT row_to_json(a)::text FROM ai_workspace_installations a WHERE id=?",String.class,appId);}
    private DeploymentProviderResourceHandleEntity handle(String kind,String status,String metadata){
        var h=new DeploymentProviderResourceHandleEntity();h.setId("handle-"+UUID.randomUUID());h.setDeploymentId(depId);h.setTargetProfileId("staging-target");
        h.setProviderType(DeploymentProviderType.COOLIFY);h.setResourceKind(kind);h.setStatus(status);h.setProviderResourceUuid("synthetic-resource");
        h.setMetadataJson(metadata);h.setCreatedAt(now);h.setUpdatedAt(now);return handles.saveAndFlush(h);
    }
    private void createApp(String id,String origin){
        var app=new AIWorkspaceInstallationEntity();app.setId(id);app.setInstallationId("public-"+id);app.setCustomerId(customerId);
        app.setConsumerEntityId(consumerEntityId);app.setDisplayName("Existing draft");app.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        app.setExperiencePackCode("synthetic-pack");app.setExperiencePackVersion("1");app.setConnectionMode(AIWorkspaceConnectionMode.values()[0]);
        app.setConnectionProfileCode("synthetic-profile");app.setConnectionProfileVersion("1");app.setDeliveryMode(AIWorkspaceDeliveryMode.values()[0]);
        app.setInteractionPolicy(AIWorkspaceInteractionPolicy.values()[0]);app.setAuthProfileCode("synthetic-auth");app.setAuthProfileVersion("1");
        app.setAuthConfigurationJson("{\"preserve\":true}");app.setConnectionConfigurationJson("{\"preserve\":true}");
        app.setAllowedOriginsJson("[\""+origin+"\"]");app.setConfigurationJson("{\"preserve\":true}");app.setCreatedAt(now);app.setUpdatedAt(now);apps.saveAndFlush(app);
    }
}
