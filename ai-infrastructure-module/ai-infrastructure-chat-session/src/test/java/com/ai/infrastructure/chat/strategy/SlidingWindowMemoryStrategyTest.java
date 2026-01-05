package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowMemoryStrategyTest {

    private final SlidingWindowMemoryStrategy strategy = new SlidingWindowMemoryStrategy();

    @Test
    void shouldKeepRecentTurnsOnly() {
        List<ChatTurn> turns = buildTurns(6);

        List<ChatTurn> pruned = strategy.prune(turns, 3);

        assertThat(pruned).hasSize(3);
        assertThat(pruned.getFirst().getUserQuery()).isEqualTo("q4");
        assertThat(pruned.getLast().getUserQuery()).isEqualTo("q6");
    }

    @Test
    void shouldHandleWindowLargerThanHistory() {
        List<ChatTurn> turns = buildTurns(2);

        List<ChatTurn> pruned = strategy.prune(turns, 5);

        assertThat(pruned).hasSize(2);
    }

    @Test
    void shouldFormatTurnsCorrectly() {
        List<ChatTurn> turns = buildTurns(2);

        String formatted = strategy.processHistory(turns);

        assertThat(formatted).contains("User: q1")
            .contains("Assistant: a2");
    }

    @Test
    void shouldHandleEmptyHistory() {
        List<ChatTurn> turns = new ArrayList<>();

        List<ChatTurn> pruned = strategy.prune(turns, 3);

        assertThat(pruned).isEmpty();
        assertThat(strategy.processHistory(turns)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForNullHistory() {
        assertThat(strategy.processHistory(null)).isEmpty();
    }

    private List<ChatTurn> buildTurns(int count) {
        List<ChatTurn> turns = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= count; i++) {
            turns.add(ChatTurn.builder()
                .userQuery("q" + i)
                .aiResponse("a" + i)
                .timestamp(now.plusSeconds(i))
                .build());
        }
        return turns;
    }
}
