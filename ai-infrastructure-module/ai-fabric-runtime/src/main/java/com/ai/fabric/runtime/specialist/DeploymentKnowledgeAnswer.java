package com.ai.fabric.runtime.specialist;

public record DeploymentKnowledgeAnswer(
    Status status,
    String answer
) {
    public enum Status {
        ANSWERED,
        INSUFFICIENT_EVIDENCE
    }
}
