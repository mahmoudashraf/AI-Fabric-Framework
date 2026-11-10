package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ManagedTaskFixtures {
    private ManagedTaskFixtures() { }
    public static ObjectNode behavior(ObjectMapper mapper) throws Exception {
        ObjectNode behavior = (ObjectNode) new DeploymentBehaviorCatalogService(mapper).defaultConfig("CONVERSATIONAL");
        behavior.set("managedTasks", mapper.readTree("""
            {"enabled":true,"tasks":[{"code":"assess-request","name":"Assess request","description":"Assess the supplied support request.",
              "instructions":"Explain the next useful step based on the supplied observations. Use null where the answer is unknown.",
              "inputSchema":{"type":"object","properties":{"request":{"type":"string"}},"required":["request"],"additionalProperties":false},
              "outputMode":"JSON","outputSchema":{"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"],"additionalProperties":false},
              "vectorSpaces":[],"readActions":[],"deadlineSeconds":45,"maxOutputTokens":1200}]}
            """));
        return behavior;
    }
}
