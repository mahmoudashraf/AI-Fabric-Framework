package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.*;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyRetirementAdapter;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.retirement.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.deployment.retirement.ProviderHostCommandRunner;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.mockito.Mockito.*;

/** Exercise real graph, creation receipts and deletion journal before testing reclamation evidence. */
final class OwnedReclamationVerificationFixture {
    final CoolifyConnection connection;
    final OwnedDependencyOwnershipFixture ownership;
    final PlatformManagedProductProvisioningService service;
    final List<ManagedDependencyRetirementAdapter.Resource> resources;
    OwnedReclamationVerificationFixture(boolean proven) {
        var mapper = new ObjectMapper();
        var api = mock(CoolifyApiClient.class);
        var targets = mock(DeploymentTargetProfileRepository.class);
        var resolver = mock(CoolifyTargetProfileResolver.class);
        var config = new CoolifyTargetProfileConfig("https://coolify.example.test","project","staging","environment","server","destination","example.test",null,1,10,false,true,"8080","/health","8080");
        connection = new CoolifyConnection(config.baseUrl(),"synthetic",config);
        ownership = new OwnedDependencyOwnershipFixture(api,targets,resolver,mapper,connection);
        ownership.graph(ManagedDependencyFixtures.http("inbox",false),"managed-inbox",
            new PlatformManagedProductProvisioningService.OwnedDependencyResources(null,null,false,false,Map.of()));
        var context = ownership.context();
        ownership.authority.createApplication(context,() -> {
            ownership.applications.put("application",new CoolifyApplicationSummary("application","managed-inbox",null,"running:healthy",null,null,
                ownership.raw("application","managed-inbox","ManagedDependency:graph:inbox")));
            return "application";
        });
        ownership.checkpoint.created("APPLICATION","application");
        var volume = ownership.authority.volume(context,"application","/app/data",null);
        ownership.checkpoint.volume("/app/data",volume.uuid());
        Map<String,String> volumes = Map.of("/app/data",volume.uuid());
        Map<String,String> plan = Map.of(volume.uuid(),volume.name());
        ownership.authority.beginVolumeDeletion(context,"application",volumes,null);
        ownership.authority.beginDeletion(context,OwnedDependencyOwnership.APPLICATION,"application");
        ownership.applications.clear();
        ownership.authority.deleted(context,OwnedDependencyOwnership.APPLICATION,"application");
        ownership.authority.volumesDeleted(context,"application",volumes,null);
        ownership.checkpoint.volumeReclamationPlanned(plan);
        if (proven) ownership.checkpoint.volumesReclaimed(Set.of(volume.uuid()));
        resources = List.of(new ManagedDependencyRetirementAdapter.Resource("inbox","managed-inbox","application",null,
            volumes,plan,proven?Set.of(volume.uuid()):Set.of()));
        var placement = new CustomerSelfServiceProperties(Map.of(TargetIntent.CUSTOMER_STAGING,
            new CustomerSelfServiceProperties.TrustedServer("server","192.0.2.1","host.docker.internal")),60);
        service = new PlatformManagedProductProvisioningService(mock(PlatformProvisioningProperties.class),mock(PlatformProductProvisioningProperties.class),
            mock(PlatformDeliveryProperties.class),mock(RailwayGraphqlClient.class),resolver,api,targets,mock(PlatformSecretService.class),
            mock(PlatformManagedProductServiceRepository.class),mock(ShopifyStoreConnectionRepository.class),mock(PlatformManagedProductServiceService.class),
            mock(PlatformAuditService.class),mapper,placement,new CoolifyHostVolumeReclamationService((intent,server,command) -> new ProviderHostCommandRunner.Result(0,"")));
        service.setOwnedDependencyOwnership(ownership.authority);
    }
}
