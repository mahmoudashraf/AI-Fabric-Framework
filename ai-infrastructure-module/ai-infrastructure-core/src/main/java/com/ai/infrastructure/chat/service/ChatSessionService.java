package com.ai.infrastructure.chat.service;

/**
 * Chat session contract exposed to the core orchestrator.
 * <p>
 * Implementations live in the chat-session module. The interface stays in the
 * core module to avoid circular dependencies while still allowing the
 * orchestrator to collaborate with the chat subsystem when it is on the
 * classpath.
 */
public interface ChatSessionService {

    /**
     * Loads and formats conversation history for a conversation owned by the
     * provided identifier. Implementations should enforce access control and
     * degrade gracefully (empty string) when history is unavailable.
     *
     * @param conversationId conversation identifier supplied by the client
     * @param ownerId        owner identifier (userId or sessionId)
     * @return formatted history to prepend to the current query, or an empty
     * string when no history is available
     */
    String getConversationContext(String conversationId, String ownerId);

    /**
     * Records a new user/assistant turn to the conversation. Implementations
     * should create the conversation if it does not yet exist, enforce
     * ownership, and fail-closed on access violations while not impacting the
     * user response pipeline.
     *
     * @param conversationId conversation identifier supplied by the client
     * @param ownerId        owner identifier (userId or sessionId)
     * @param userQuery      raw user query (unenriched)
     * @param aiResponse     assistant response to persist
     */
    void recordTurn(String conversationId, String ownerId, String userQuery, String aiResponse);
}
