package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryMemoryStrategyTest {

    private AICoreService llmService;
    private SummaryMemoryStrategy strategy;

    @BeforeEach
    void setUp() {
        llmService = mock(AICoreService.class);
        strategy = new SummaryMemoryStrategy(llmService);
    }

    @Test
    void shouldSummarizeOlderTurns() {
        when(llmService.generateContent(any(AIGenerationRequest.class)))
            .thenReturn(AIGenerationResponse.builder().content("summary").build());

        List<ChatTurn> turns = buildTurns(5);

        String processed = strategy.processHistory(turns);

        assertThat(processed).contains("Previous Conversation Summary")
            .contains("summary")
            .contains("Recent Exchanges");

        ArgumentCaptor<AIGenerationRequest> captor = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(llmService, times(1)).generateContent(captor.capture());
        assertThat(captor.getValue().getGenerationType()).isEqualTo("SUMMARY");
    }

    @Test
    void shouldSkipSummarizationForShortHistory() {
        List<ChatTurn> turns = buildTurns(2);

        String processed = strategy.processHistory(turns);

        assertThat(processed).contains("User: q1");
        verify(llmService, times(0)).generateContent(any());
    }

    @Test
    void shouldPruneToSummaryTurn() {
        when(llmService.generateContent(any(AIGenerationRequest.class)))
            .thenReturn(AIGenerationResponse.builder().content("summary").build());

        List<ChatTurn> turns = buildTurns(6);

        List<ChatTurn> pruned = strategy.prune(turns, 4);

        assertThat(pruned).hasSize(4);
        assertThat(pruned.getFirst().getAiResponse()).contains("summary");
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
