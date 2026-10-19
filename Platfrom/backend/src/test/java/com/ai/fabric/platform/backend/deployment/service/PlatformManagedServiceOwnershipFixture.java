package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real V197/V201 SQL and registry reads; all provider traffic and the vault stay local mocks. */
final class PlatformManagedServiceOwnershipFixture {
    final ObjectMapper mapper = new ObjectMapper();
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager transactions;
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final RailwayGraphqlClient railway = mock(RailwayGraphqlClient.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final PlatformManagedProductServiceService services = mock(PlatformManagedProductServiceService.class);
    final PlatformManagedProductServiceRepository repository = mock(PlatformManagedProductServiceRepository.class);
    final ShopifyStoreConnectionRepository stores = mock(ShopifyStoreConnectionRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final Map<String,CoolifyApplicationSummary> applications = new LinkedHashMap<>();
    final Map<String,List<CoolifyApplicationStorageSummary>> storages = new LinkedHashMap<>();
    final Map<String,CoolifyDeploymentSummary> deployments = new LinkedHashMap<>();
    final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.example.test","project","staging",
        "environment","server","destination","example.test",null,1,10,false,true,"8080","/health","8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(),"synthetic-private",config);
    final PlatformManagedServiceOwnershipJournal journal;
    final PlatformManagedServiceOwnership ownership;
    final PlatformManagedProductProvisioningService implementation;
    final PlatformManagedProductProvisioningService provisioning;
    int effects;
    String lastName;

    PlatformManagedServiceOwnershipFixture() {
        this(new DriverManagerDataSource("jdbc:h2:mem:shared-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa",""));
    }

    PlatformManagedServiceOwnershipFixture(DataSource source) {
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V197__managed_resource_creation_ownership.sql"),
            new ClassPathResource("db/migration/V201__platform_service_creation_ownership.sql")).execute(source);
        jdbc.execute("CREATE TABLE platform_managed_product_services(id VARCHAR PRIMARY KEY,service_ref VARCHAR,service_kind VARCHAR,status VARCHAR,base_url VARCHAR,private_network_url VARCHAR,railway_project_id VARCHAR,railway_environment_id VARCHAR,railway_service_id VARCHAR,details_json TEXT,secret_name VARCHAR,environment_scope VARCHAR,desired_replicas INTEGER,actual_replicas INTEGER)");
        jdbc.execute("CREATE TABLE deployment_target_profiles(id VARCHAR PRIMARY KEY,provider_type VARCHAR,active BOOLEAN,platform_services_allowed BOOLEAN,provider_config_json TEXT,resource_defaults_json TEXT,credential_ref_id VARCHAR,environment_name VARCHAR,source_strategy VARCHAR,network_policy_json TEXT)");
        jdbc.execute("CREATE TABLE deployment_managed_dependency_graphs(id VARCHAR,state_json TEXT)");
        jdbc.execute("CREATE TABLE deployment_provider_resource_handles(provider_resource_uuid VARCHAR,status VARCHAR,fqdn VARCHAR,metadata_json TEXT)");
        jdbc.execute("CREATE TABLE platform_deployment_releases(provisioning_details_json TEXT)");
        jdbc.execute("CREATE TABLE platform_deployment_managed_vector_resources(resource_reference VARCHAR,endpoint VARCHAR,details_json TEXT)");
        target.setId("target"); target.setProviderType(DeploymentProviderType.COOLIFY); target.setActive(true);
        target.setPlatformServicesAllowed(true); target.setEnvironmentName("staging"); target.setSourceStrategy("GIT_SOURCE");
        target.setProviderConfigJson("{}"); target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES(?,?,TRUE,TRUE,?,?,?,?,?,?)",target.getId(),"COOLIFY",
            target.getProviderConfigJson(),target.getResourceDefaultsJson(),target.getCredentialRefId(),target.getEnvironmentName(),target.getSourceStrategy(),target.getNetworkPolicyJson());
        jdbc.update("INSERT INTO platform_managed_product_services(id,service_ref,service_kind,status,details_json,secret_name,desired_replicas,actual_replicas) VALUES('registry','shared','MCP_EXECUTION_GATEWAY_SERVICE','CREATED','{}','MANAGED_PRODUCT_SHARED_API_KEY',1,0)");
        when(targets.findById("target")).thenReturn(Optional.of(target));
        when(targets.findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(DeploymentProviderType.COOLIFY)).thenReturn(List.of(target));
        when(resolver.requireConnection(target)).thenReturn(connection);
        when(services.requireService("shared")).thenAnswer(call -> row());
        when(services.updateDesiredReplicas(eq("shared"),any())).thenAnswer(call -> {
            jdbc.update("UPDATE platform_managed_product_services SET desired_replicas=? WHERE id='registry'",call.getArgument(1,Integer.class)); return null;
        });
        when(repository.save(any())).thenAnswer(call -> {
            var row = call.getArgument(0,PlatformManagedProductServiceEntity.class);
            jdbc.update("UPDATE platform_managed_product_services SET status=?,base_url=?,private_network_url=?,details_json=?,secret_name=?,environment_scope=?,desired_replicas=?,actual_replicas=? WHERE id=?",
                row.getStatus(),row.getBaseUrl(),row.getPrivateNetworkUrl(),row.getDetailsJson(),row.getSecretName(),row.getEnvironmentScope(),row.getDesiredReplicas(),row.getActualReplicas(),row.getId());
            return row;
        });
        when(secrets.isSecretPresent(anyString())).thenReturn(true);
        when(secrets.resolveSecret("MANAGED_PRODUCT_SHARED_API_KEY")).thenReturn("synthetic-api-key");
        when(api.listProjects(connection)).thenAnswer(call -> { outsideTransaction(); return List.of(new CoolifyProjectSummary("project","shared",null,mapper.createObjectNode().put("uuid","project").put("id",10))); });
        when(api.getEnvironment(connection,"project","environment")).thenAnswer(call -> {
            outsideTransaction(); var raw = mapper.createObjectNode().put("uuid","environment").put("id",20).put("project_id",10);
            var inventory = raw.putArray("applications");
            applications.values().forEach(app -> inventory.add(mapper.createObjectNode().put("uuid",app.uuid()).put("name",app.name()).put("environment_id",20)));
            return Optional.of(new CoolifyEnvironmentSummary("environment","staging","project",null,raw));
        });
        when(api.getServer(connection,"server")).thenAnswer(call -> { outsideTransaction(); return mapper.createObjectNode().put("uuid","server"); });
        when(api.resolveDestinationPlacement(connection,"server","destination")).thenAnswer(call -> { outsideTransaction(); return Optional.of(new CoolifyDestinationPlacement("destination","server","LOCAL",mapper.createObjectNode())); });
        when(api.listApplications(connection)).thenAnswer(call -> { outsideTransaction(); return List.copyOf(applications.values()); });
        when(api.getApplication(eq(connection),anyString())).thenAnswer(call -> { outsideTransaction(); return Optional.ofNullable(applications.get(call.getArgument(1))); });
        when(api.listApplicationStorages(eq(connection),anyString())).thenAnswer(call -> { outsideTransaction(); return storages.getOrDefault(call.getArgument(1),List.of()); });
        when(api.createPublicApplication(eq(connection),any())).thenAnswer(call -> {
            var request = call.getArgument(1,CoolifyCreatePublicApplicationRequest.class);
            return create(request.name(),request.domains(),null);
        });
        when(api.createDockerImageApplication(eq(connection),any())).thenAnswer(call -> {
            var request = call.getArgument(1,CoolifyCreateDockerImageApplicationRequest.class);
            String uuid = create(request.name(),request.domains(),request.imageRepository());
            applications.put(uuid, image(applications.get(uuid),request.imageRepository(),request.imageTag()));
            return uuid;
        });
        doAnswer(call -> { outsideTransaction(); assertPending("MUTATION_PENDING"); return null; }).when(api).updatePublicApplication(eq(connection),anyString(),any());
        doAnswer(call -> {
            outsideTransaction(); assertPending("MUTATION_PENDING");
            var request = call.getArgument(2,CoolifyCreateDockerImageApplicationRequest.class);
            String uuid = call.getArgument(1);
            applications.put(uuid,image(applications.get(uuid),request.imageRepository(),request.imageTag()));
            return null;
        }).when(api).updateDockerImageApplication(eq(connection),anyString(),any());
        doAnswer(call -> { outsideTransaction(); assertPending("MUTATION_PENDING"); return null; }).when(api).updateEnvironmentVariables(eq(connection),anyString(),anyList());
        when(api.start(eq(connection),anyString(),anyBoolean(),anyBoolean())).thenAnswer(call -> action(call.getArgument(1)));
        when(api.restart(eq(connection),anyString())).thenAnswer(call -> action(call.getArgument(1)));
        when(api.getDeployment(eq(connection),anyString())).thenAnswer(call -> { outsideTransaction(); return Optional.ofNullable(deployments.get(call.getArgument(1))); });
        when(api.delete(eq(connection),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean())).thenAnswer(call -> {
            outsideTransaction(); assertPending("DELETE_PENDING"); effects++; applications.remove(call.getArgument(1));
            return new CoolifyActionResponse("deleted",null,mapper.createObjectNode());
        });
        journal = new PlatformManagedServiceOwnershipJournal(jdbc,mapper,transactions);
        var readback = new CoolifyResourceOwnership(new ManagedResourceOwnershipService(jdbc,mapper,transactions),mapper,mock(DeploymentRepository.class),jdbc,transactions);
        ownership = new PlatformManagedServiceOwnership(jdbc,mapper,journal,readback,api,transactions);
        implementation = new PlatformManagedProductProvisioningService(
            new PlatformProvisioningProperties(null,null,null,"example/shared","main","staging","workspace",null,null,null,null,null,null,64,null,null,false,false,60000,Duration.ofMillis(1),Duration.ofSeconds(1)),
            new PlatformProductProvisioningProperties(null,null,null,null,null,null,null,null,null,Duration.ofMillis(1),Duration.ofSeconds(1)),
            new PlatformDeliveryProperties("https://platform.example.test",true,Duration.ofDays(1)),railway,resolver,api,targets,secrets,repository,stores,services,mock(PlatformAuditService.class),mapper);
        implementation.setSharedServiceOwnership(ownership);
        var proxy = new ProxyFactory(implementation);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        provisioning = (PlatformManagedProductProvisioningService) proxy.getProxy();
    }

    PlatformManagedProductServiceEntity row() {
        return jdbc.queryForObject("SELECT * FROM platform_managed_product_services WHERE id='registry'",(rs,n) -> {
            var value = new PlatformManagedProductServiceEntity(); value.setId(rs.getString("id")); value.setServiceRef(rs.getString("service_ref"));
            value.setServiceKind(rs.getString("service_kind")); value.setStatus(rs.getString("status")); value.setDetailsJson(rs.getString("details_json"));
            value.setBaseUrl(rs.getString("base_url")); value.setPrivateNetworkUrl(rs.getString("private_network_url"));
            value.setRailwayProjectId(rs.getString("railway_project_id")); value.setRailwayEnvironmentId(rs.getString("railway_environment_id")); value.setRailwayServiceId(rs.getString("railway_service_id"));
            value.setSecretName(rs.getString("secret_name")); value.setEnvironmentScope(rs.getString("environment_scope"));
            value.setDesiredReplicas(rs.getInt("desired_replicas")); value.setActualReplicas(rs.getInt("actual_replicas"));
            return value;
        });
    }
    PlatformManagedServiceOwnership.Context context() { return ownership.context(row(),target,connection,lastName); }
    void details(String json) { jdbc.update("UPDATE platform_managed_product_services SET details_json=? WHERE id='registry'",json); }
    void standaloneWorkspace() {
        jdbc.update("UPDATE platform_managed_product_services SET service_kind='STANDALONE_AI_WORKSPACE_SERVICE' WHERE id='registry'");
        details(mapper.createObjectNode().put("providerType","COOLIFY").put("imageRepository","registry.example.test/workspace")
            .put("imageTag","a".repeat(40)).put("sourceCommit","a".repeat(40))
            .put("imageDigest","sha256:"+"b".repeat(64)).toString());
    }
    CoolifyApplicationSummary image(CoolifyApplicationSummary app, String repository, String hash) {
        var raw = ((com.fasterxml.jackson.databind.node.ObjectNode) app.raw()).deepCopy()
            .put("docker_registry_image_name",repository).put("docker_registry_image_tag",hash);
        return new CoolifyApplicationSummary(app.uuid(),app.name(),app.fqdn(),app.status(),repository,hash,raw);
    }
    void assertPending(String state) { assertThat(journalState()).isEqualTo(state); }
    String journalState() { return jdbc.queryForObject("SELECT state FROM platform_managed_service_ownership ORDER BY generation DESC LIMIT 1",String.class); }
    void outsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    String create(String name, String domain, String image) {
        outsideTransaction(); assertPending("CREATE_PENDING");
        assertThat(row().getDetailsJson()).contains("targetProfileId").contains("target");
        effects++; lastName = name; String uuid = "application-"+effects;
        applications.put(uuid,app(uuid,name,domain,image)); return uuid;
    }
    CoolifyApplicationSummary app(String uuid,String name,String domain,String image) {
        var raw = mapper.createObjectNode().put("uuid",uuid).put("name",name).put("description","Managed product service shared")
            .put("environment_id",20).put("server_uuid","server").put("destination_uuid","destination")
            .put("limits_memory","256M").put("limits_memory_swap","256M").put("limits_memory_reservation","64M").put("limits_cpus","0.250");
        return new CoolifyApplicationSummary(uuid,name,domain,"running:healthy",image,"1",raw);
    }
    CoolifyActionResponse action(String app) {
        outsideTransaction(); assertPending("MUTATION_PENDING");
        String id = "deployment-"+(deployments.size()+1);
        deployments.put(id,new CoolifyDeploymentSummary(id,lastName,app,"FINISHED",null,null,null,null,"finished",mapper.createObjectNode()));
        return new CoolifyActionResponse("started",id,mapper.createObjectNode());
    }
}
