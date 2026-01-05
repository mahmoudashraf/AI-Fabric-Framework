package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "memory-strategy",
    havingValue = "SLIDING_WINDOW",
    matchIfMissing = true
)
public class SlidingWindowMemoryStrategy implements MemoryStrategy {

    private static final String STRATEGY_NAME = "SLIDING_WINDOW";

    @Override
    public String processHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        return history.stream()
            .map(ChatTurn::toPromptFormat)
            .collect(Collectors.joining("\n\n"));
    }

    @Override
    public List<ChatTurn> prune(List<ChatTurn> history, int limit) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        if (history.size() <= limit) {
            return history;
        }

        int startIndex = history.size() - limit;
        List<ChatTurn> pruned = history.subList(startIndex, history.size());

        log.debug("Sliding window pruned {} turns (kept last {})", history.size() - pruned.size(), limit);

        return pruned;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
}
