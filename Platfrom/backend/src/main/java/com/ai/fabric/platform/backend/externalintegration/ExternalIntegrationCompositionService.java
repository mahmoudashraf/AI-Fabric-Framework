package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Profile;
import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.conflict;

/** Target-independent aliases are compiled without changing the persisted install or binding a business account. */
@Service
public class ExternalIntegrationCompositionService {
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerOfferRepository offers;
    private final ExternalIntegrationPolicy policy;
    private final ExternalIntegrationCompilerResolver compiler;
    private final CustomerOfferCodec codec;
    private final ObjectMapper mapper;

    public ExternalIntegrationCompositionService(CustomerOfferDeploymentRepository associations, CustomerOfferRepository offers,
        ExternalIntegrationPolicy policy, ExternalIntegrationCompilerResolver compiler, CustomerOfferCodec codec, ObjectMapper mapper) {
        this.associations = associations; this.offers = offers; this.policy = policy;
        this.compiler = compiler; this.codec = codec; this.mapper = mapper;
    }

    public List<Profile> profiles(DeploymentEntity deployment) {
        var association = associations.findById(deployment.getId());
        if (association.isEmpty()) return List.of();
        var owned = association.filter(value -> deployment.getCustomerId().equals(value.getCustomerId())
            && deployment.getTenantId().equals(value.getTenantId())).orElseThrow(() -> conflict("OFFER_SCOPE_REQUIRED"));
        var offer = offers.findById(owned.getOfferId()).filter(value -> owned.getOfferFingerprint().equals(value.getFingerprint()))
            .orElseThrow(() -> conflict("OFFER_CHANGED"));
        Definition definition = codec.read(offer.getDefinitionJson(), Definition.class);
        if (!offer.getFingerprint().equals(codec.hash(definition))) throw conflict("OFFER_CHANGED");
        policy.validateDefinition(definition);
        return definition.externalIntegrationProfiles();
    }

    public DeploymentMarketplacePluginInstallEntity compileInput(List<Profile> profiles, DeploymentMarketplacePluginInstallEntity install) {
        var matches = profiles.stream().filter(profile -> profile.pluginId().equals(install.getPluginId())).toList();
        if (matches.isEmpty()) return install;
        if (matches.size() != 1 || !matches.getFirst().pluginVersionId().equals(install.getPluginVersionId())) {
            throw conflict("COMPILER_PROFILE_MISMATCH");
        }
        var inputs = compiler.installInputs(matches.getFirst(), codec.tree(install.getConfigJson()), codec.tree(install.getSecretRefsJson()));
        var copy = new DeploymentMarketplacePluginInstallEntity();
        copy.setId(install.getId()); copy.setDeploymentId(install.getDeploymentId()); copy.setPluginId(install.getPluginId());
        copy.setPluginVersionId(install.getPluginVersionId()); copy.setStatus(install.getStatus());
        copy.setCreatedAt(install.getCreatedAt()); copy.setUpdatedAt(install.getUpdatedAt());
        copy.setConfigJson(codec.write(inputs.config())); copy.setSecretRefsJson(codec.write(inputs.secretRefs()));
        return copy;
    }

    public ArrayNode evidence(DeploymentEntity deployment) {
        ArrayNode result = mapper.createArrayNode();
        profiles(deployment).stream().sorted(java.util.Comparator.comparing(Profile::id))
            .forEach(profile -> result.add(compiler.compilerEvidence(profile)));
        return result;
    }
}
