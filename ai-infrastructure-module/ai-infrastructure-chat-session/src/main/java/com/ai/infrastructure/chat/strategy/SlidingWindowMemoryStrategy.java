package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SlidingWindowMemoryStrategy implements MemoryStrategy {

    @Override
    public List<ChatTurn> prune(List<ChatTurn> history, int limit) {
        if (history == null || history.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (history.size() <= limit) {
            return Collections.unmodifiableList(history);
        }
        int startIndex = Math.max(0, history.size() - limit);
        return Collections.unmodifiableList(history.subList(startIndex, history.size()));
    }

    @Override
    public String toPromptContext(List<ChatTurn> prunedHistory) {
        if (prunedHistory == null || prunedHistory.isEmpty()) {
            return "";
        }
        String context = prunedHistory.stream()
            .map(ChatTurn::toPromptFormat)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
        return context != null ? context : "";
    }

    @Override
    public String getStrategyName() {
        return "SLIDING_WINDOW";
    }
}
