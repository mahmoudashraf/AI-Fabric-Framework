package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;

import java.util.List;

public interface MemoryStrategy {

    List<ChatTurn> prune(List<ChatTurn> history, int limit);

    String toPromptContext(List<ChatTurn> prunedHistory);

    String getStrategyName();
}

