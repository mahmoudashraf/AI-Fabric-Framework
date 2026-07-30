package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DeploymentPocSpecialistQueryRequest {

    @NotBlank
    @Size(max = 2000)
    private final String question;

    @JsonCreator
    public DeploymentPocSpecialistQueryRequest(
        @JsonProperty("question") String question
    ) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    @JsonAnySetter
    public void rejectUnknownField(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException(
            "Unexpected field in deployment specialist request."
        );
    }
}
