package com.ai.fabric.runtime.review;

import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import java.util.List;

/** Constructed by the application after its authorized lookup, never bound from HTTP JSON. */
public record ReviewProposalInput(String question, List<OrchestrationAttachment> verifiedAttachments) {
    public ReviewProposalInput {
        verifiedAttachments = verifiedAttachments == null ? List.of() : List.copyOf(verifiedAttachments);
    }
}
