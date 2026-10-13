package com.ai.fabric.runtime.handover;

import ai.fabric.chat.control.ConversationScope;
import ai.fabric.chat.control.ConversationTurnLease;
import ai.fabric.intent.orchestration.conversation.ApprovedConversationSnapshot;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;

public record RuntimeConversationTurn(
    ConversationScope scope,
    ConversationTurnLease lease,
    OrchestrationTurnGuard guard,
    ApprovedConversationSnapshot conversationSnapshot
) {}
