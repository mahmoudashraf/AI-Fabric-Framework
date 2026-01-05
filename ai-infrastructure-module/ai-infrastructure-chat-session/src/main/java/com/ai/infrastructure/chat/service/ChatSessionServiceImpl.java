package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.config.ChatSessionProperties;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.ChatTurn;
import com.ai.infrastructure.chat.domain.SessionStatus;
import com.ai.infrastructure.chat.exception.AccessDeniedException;
import com.ai.infrastructure.chat.exception.SessionExpiredException;
import com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import com.ai.infrastructure.chat.strategy.MemoryStrategy;
import com.ai.infrastructure.chat.strategy.SlidingWindowMemoryStrategy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnMissingBean(ChatSessionService.class)
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionStorageProvider storageProvider;
    private final ChatSessionAccessControlPolicy accessPolicy;
    private final MemoryStrategy memoryStrategy;
    private final ChatSessionProperties properties;
    private final Clock clock;
    private final Cache<String, String> contextCache;

    public ChatSessionServiceImpl(ChatSessionStorageProvider storageProvider,
                                  ChatSessionAccessControlPolicy accessPolicy,
                                  List<MemoryStrategy> strategies,
                                  ChatSessionProperties properties,
                                  Clock clock) {
        this.storageProvider = storageProvider;
        this.accessPolicy = accessPolicy;
        this.memoryStrategy = resolveStrategy(strategies, properties);
        this.properties = properties;
        this.clock = clock;
        this.contextCache = Caffeine.newBuilder()
            .maximumSize(properties.getHotCacheSize())
            .expireAfterAccess(properties.getTtlMinutes(), TimeUnit.MINUTES)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String getConversationContext(String conversationId, String ownerId) {
        if (!StringUtils.hasText(conversationId)) {
            return "";
        }

        String cached = contextCache.getIfPresent(conversationId);
        if (cached != null) {
            return cached;
        }

        ChatSession session = loadConversation(conversationId, ownerId, false);
        if (session == null) {
            return "";
        }

        if (!accessPolicy.canUserViewHistory(ownerId, conversationId)) {
            throw new AccessDeniedException("User not permitted to view conversation history");
        }

        List<ChatTurn> pruned = memoryStrategy.prune(session.getTurns(), properties.getDefaultWindowSize());
        String history = memoryStrategy.processHistory(pruned);

        contextCache.put(conversationId, history);
        return history;
    }

    @Override
    @Transactional
    public void recordTurn(String conversationId, String ownerId, String userQuery, String aiResponse) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        if (!StringUtils.hasText(aiResponse)) {
            log.debug("Skipping turn recording for {} - empty AI response", conversationId);
            return;
        }

        ChatSession session = loadConversation(conversationId, ownerId, true);

        ChatTurn turn = ChatTurn.builder()
            .userQuery(userQuery)
            .aiResponse(aiResponse)
            .timestamp(LocalDateTime.now(clock))
            .build();

        session.addTurn(turn, clock);
        storageProvider.save(session);
        contextCache.invalidate(conversationId);
    }

    private ChatSession loadConversation(String conversationId, String ownerId, boolean createIfMissing) {
        return storageProvider.findById(conversationId)
            .map(session -> validateSession(session, ownerId))
            .orElseGet(() -> {
                if (!createIfMissing) {
                    if (!accessPolicy.canUserCreateConversation(ownerId)) {
                        throw new AccessDeniedException("User not permitted to create conversation");
                    }
                    return null;
                }
                if (!accessPolicy.canUserCreateConversation(ownerId)) {
                    throw new AccessDeniedException("User not permitted to create conversation");
                }
                ChatSession session = ChatSession.newSession(conversationId, ownerId, clock);
                return storageProvider.save(session);
            });
    }

    private ChatSession validateSession(ChatSession session, String ownerId) {
        if (!session.isOwnedBy(ownerId) && !accessPolicy.canUserAccessConversation(ownerId, session.getId())) {
            throw new AccessDeniedException("User not permitted to access conversation");
        }

        try {
            session.ensureActive(properties.getTtlMinutes(), clock);
        } catch (SessionExpiredException ex) {
            storageProvider.save(markExpired(session));
            throw ex;
        }

        return session;
    }

    private ChatSession markExpired(ChatSession session) {
        session.setStatus(SessionStatus.EXPIRED);
        return session;
    }

    private MemoryStrategy resolveStrategy(List<MemoryStrategy> strategies, ChatSessionProperties props) {
        if (strategies == null || strategies.isEmpty()) {
            return new SlidingWindowMemoryStrategy();
        }
        return strategies.stream()
            .filter(Objects::nonNull)
            .filter(strategy -> strategy.getStrategyName().equalsIgnoreCase(props.getMemoryStrategy(ChatSessionProperties.MemoryStrategyType.SLIDING_WINDOW).name()))
            .findFirst()
            .orElse(strategies.get(0));
    }
}
