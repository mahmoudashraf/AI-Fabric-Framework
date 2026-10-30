package com.ai.fabric.runtime.web.dto;

import java.time.Instant;

/** A read-only view of an accepted turn; never an instruction to replay it. */
public record CompletedConversationTurnResponse(
    String userMessageId,
    String aiMessageId,
    ChatQueryResponse response,
    Instant completedAt
) { }
