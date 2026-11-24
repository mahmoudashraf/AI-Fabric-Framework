package com.ai.fabric.runtime.web.dto;

import ai.fabric.intent.actiondraft.ActionDraftSubmission;
import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ChatQueryRequest {

    @NotBlank
    private String query;

    private String conversationId;
    private String position;
    private String mode;
    private Map<String, Object> context;
    private List<OrchestrationAttachment> attachments;
    private ActionDraftSubmission actionDraftSubmission;
    private Map<String, String> promptPreview;
    /** Optional backend authoring reference; accepted only with dedicated verified authoring scope. */
    private ConfigurationWork configurationWork;
    public record ConfigurationWork(String sessionId, String turnId, String task, String profileId,
                                    String releaseId, String bundleHash, boolean previewOnly, int maxCorrections,
                                    boolean goalMode, String authorityReference, String goalId, java.time.Instant authorityDeadline,
                                    long requestRevision, long cancellationEpoch, int segment) {
        public ConfigurationWork(String sessionId, String turnId, String task, String profileId,
            String releaseId, String bundleHash, boolean previewOnly, int maxCorrections) {
            this(sessionId, turnId, task, profileId, releaseId, bundleHash, previewOnly, maxCorrections,
                false, null, null, null, 0, 0, 0);
        }
        public ConfigurationWork renewed(long revision, long epoch, int nextSegment, java.time.Instant deadline) {
            return new ConfigurationWork(sessionId, turnId, task, profileId, releaseId, bundleHash, previewOnly,
                maxCorrections, goalMode, authorityReference, goalId, deadline, revision, epoch, nextSegment);
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    private final Map<String, Object> unexpectedFields = new LinkedHashMap<>();

    @JsonAnySetter
    void captureUnexpectedField(String name, Object value) {
        unexpectedFields.put(name, value);
    }
}
