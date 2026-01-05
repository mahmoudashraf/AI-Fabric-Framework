package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "memory-strategy",
    havingValue = "SUMMARY"
)
public class SummaryMemoryStrategy implements MemoryStrategy {

    private static final String STRATEGY_NAME = "SUMMARY";
    private static final int RECENT_TURNS_TO_KEEP_VERBATIM = 3;

    private final AICoreService llmService;

    @Override
    public String processHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        if (history.size() <= RECENT_TURNS_TO_KEEP_VERBATIM) {
            return formatTurns(history);
        }

        int splitIndex = history.size() - RECENT_TURNS_TO_KEEP_VERBATIM;
        List<ChatTurn> olderTurns = history.subList(0, splitIndex);
        List<ChatTurn> recentTurns = history.subList(splitIndex, history.size());

        String summary = generateSummary(olderTurns);
        String recentContext = formatTurns(recentTurns);

        return String.format(
            "Previous Conversation Summary:%n%s%n%nRecent Exchanges:%n%s",
            summary,
            recentContext
        );
    }

    @Override
    public List<ChatTurn> prune(List<ChatTurn> history, int limit) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        if (history.size() <= limit) {
            return history;
        }

        int splitIndex = history.size() - (limit - 1);
        List<ChatTurn> olderTurns = history.subList(0, splitIndex);
        List<ChatTurn> recentTurns = history.subList(splitIndex, history.size());

        String summary = generateSummary(olderTurns);

        ChatTurn summaryTurn = ChatTurn.builder()
            .userQuery("[Summary of earlier conversation]")
            .aiResponse(summary)
            .timestamp(olderTurns.getFirst().getTimestamp())
            .entityIds(new ArrayList<>())
            .build();

        List<ChatTurn> result = new ArrayList<>();
        result.add(summaryTurn);
        result.addAll(recentTurns);

        log.debug("Summarized {} older turns into 1 summary turn (keeping {} recent)",
            olderTurns.size(), recentTurns.size());

        return result;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    private String formatTurns(List<ChatTurn> turns) {
        return turns.stream()
            .map(ChatTurn::toPromptFormat)
            .collect(Collectors.joining("\n\n"));
    }

    private String generateSummary(List<ChatTurn> turns) {
        String conversationText = formatTurns(turns);

        String summarizationPrompt = String.format(
            "Summarize the following conversation concisely, focusing on key topics and decisions:%n%n%s",
            conversationText
        );

        try {
            AIGenerationRequest request = AIGenerationRequest.builder()
                .entityId("conversation-summary")
                .entityType("CHAT_SESSION")
                .generationType("SUMMARY")
                .prompt(summarizationPrompt)
                .systemPrompt("You are a conversation summarizer. Create concise summaries.")
                .maxTokens(200)
                .temperature(0.3)
                .build();

            AIGenerationResponse response = llmService.generateContent(request);
            String summary = response.getContent();

            log.debug("Generated summary for {} turns (length: {})",
                turns.size(), summary != null ? summary.length() : 0);

            return summary != null ? summary : "";

        } catch (Exception ex) {
            log.error("Failed to generate summary: {}. Falling back to truncation.", ex.getMessage());
            return turns.isEmpty()
                ? ""
                : String.format("Earlier: %s ... %s",
                    turns.getFirst().toPromptFormat(),
                    turns.getLast().toPromptFormat());
        }
    }
}
