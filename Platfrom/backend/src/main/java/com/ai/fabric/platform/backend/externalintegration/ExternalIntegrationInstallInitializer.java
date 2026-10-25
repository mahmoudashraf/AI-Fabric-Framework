package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.conflict;

/** Creates the reviewed symbolic install configuration once, never a provider account or target credential. */
@Service
public class ExternalIntegrationInstallInitializer {
    private final ExternalIntegrationCompositionService composition;
    private final DeploymentMarketplacePluginInstallRepository installs;
    private final DeploymentMarketplaceInstallService installation;
    private final ObjectMapper mapper;

    public ExternalIntegrationInstallInitializer(ExternalIntegrationCompositionService composition,
        DeploymentMarketplacePluginInstallRepository installs, DeploymentMarketplaceInstallService installation, ObjectMapper mapper) {
        this.composition = composition; this.installs = installs; this.installation = installation; this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(DeploymentEntity deployment) {
        var profiles = composition.bootstrapProfiles(deployment);
        var current = installs.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        for (var profile : profiles) {
            var matches = current.stream().filter(install -> profile.pluginId().equals(install.getPluginId())).toList();
            if (matches.size() != 1) throw conflict("COMPILER_PROFILE_INSTALL_REQUIRED");
            var original = matches.getFirst();
            if (!"DISABLED".equals(original.getStatus()) && !"ENABLED".equals(original.getStatus())) {
                throw conflict("COMPILER_PROFILE_INSTALL_STATE_INVALID");
            }
            var symbolic = composition.compileInput(profiles, original);
            try {
                installation.updateInstallForTrustedCallerWithoutDraftSync(deployment, original.getId(),
                    new UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", mapper.readTree(symbolic.getConfigJson()),
                        mapper.readTree(symbolic.getSecretRefsJson())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw conflict("COMPILER_PROFILE_INSTALL_INVALID");
            }
        }
    }
}
