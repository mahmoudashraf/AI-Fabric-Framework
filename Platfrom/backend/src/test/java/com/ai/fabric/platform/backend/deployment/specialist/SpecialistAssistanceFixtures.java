package com.ai.fabric.platform.backend.deployment.specialist;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

public final class SpecialistAssistanceFixtures {
    private SpecialistAssistanceFixtures() {}

    public static ObjectNode behavior(ObjectMapper mapper) {
        ObjectNode behavior = (ObjectNode) new DeploymentBehaviorCatalogService(mapper).defaultConfig("CONVERSATIONAL");
        behavior.withArray("executionExtensions").add(SpecialistAssistanceCompiler.EXTENSION);
        try {
            behavior.set("specialistAssistance", mapper.readTree("""
                {
                  "enabled": true,
                  "areas": [
                    {"id":"setup","name":"Product setup","purpose":"Explain product setup and integration.",
                     "instructions":"Use approved product setup documentation. State missing information.",
                     "vectorSpaces":["product-guide"],"readActions":[]},
                    {"id":"account","name":"Account status","purpose":"Read the caller-authorized account status.",
                     "instructions":"Use the authorized current account status and distinguish observations from suggestions.",
                     "vectorSpaces":[],"readActions":["get_account_status"]}
                  ],
                  "resolutions":[{"id":"investigate","name":"Setup investigation","objective":"Investigate the user's setup and current account state.",
                     "areaIds":["setup","account"],"allowParallel":true}],
                  "limits":{"maxDecisions":4,"maxWorkerInvocations":2,"maxParallelWorkers":2,"deadlineSeconds":75}
                }
                """));
            return behavior;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public static SpecialistAssistanceCompiler.Authority authority() {
        return new SpecialistAssistanceCompiler.Authority(Map.of("get_account_status", Set.of("account:read")), Set.of("product-guide"));
    }
}
