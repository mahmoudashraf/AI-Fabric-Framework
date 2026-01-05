package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;

import java.util.List;

public interface MemoryStrategy {

    String processHistory(List<ChatTurn> history);

    List<ChatTurn> prune(List<ChatTurn> history, int limit);

    String getStrategyName();
}
