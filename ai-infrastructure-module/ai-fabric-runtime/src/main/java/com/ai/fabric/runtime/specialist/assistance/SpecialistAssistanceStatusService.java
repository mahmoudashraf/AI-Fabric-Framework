package com.ai.fabric.runtime.specialist.assistance;

import ai.fabric.execution.chain.SpecialistChainId;
import ai.fabric.execution.chain.SpecialistChainRegistry;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SpecialistAssistanceStatusService {
    private final SpecialistAssistanceBundle bundle;
    private final ObjectProvider<SpecialistRegistry> specialists;
    private final ObjectProvider<SpecialistChainRegistry> chains;
    public SpecialistAssistanceStatusService(SpecialistAssistanceBundle bundle,
        ObjectProvider<SpecialistRegistry> specialists, ObjectProvider<SpecialistChainRegistry> chains) {
        this.bundle = bundle; this.specialists = specialists; this.chains = chains;
    }
    public Map<String, Object> status() {
        if (!bundle.enabled()) return Map.of("enabled", false, "ready", true);
        var specialistRegistry = specialists.getIfAvailable();
        var chainRegistry = chains.getIfAvailable();
        boolean ready = true;
        for (var invocation : bundle.content().path("invocations")) {
            if ("CHAIN".equals(invocation.path("targetType").asText())) {
                ready &= chainRegistry != null && chainRegistry.find(SpecialistChainId.parse(invocation.path("targetRef").asText())).isPresent();
            } else {
                ready &= specialistRegistry != null && specialistRegistry.find(SpecialistId.parse(invocation.path("targetRef").asText())).isPresent();
            }
        }
        return Map.of("enabled", true, "ready", ready, "contractVersion", SpecialistAssistanceBundle.CONTRACT,
            "contentHash", bundle.contentHash(), "invocations", bundle.invocationCatalogue());
    }
}
