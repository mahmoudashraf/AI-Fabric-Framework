package com.ai.infrastructure.chat.spi;

public interface ChatSessionAccessControlPolicy {

    boolean canUserCreateConversation(String ownerId);

    boolean canUserAccessConversation(String requestingUser, String conversationId);

    boolean canUserDeleteConversation(String requestingUser, String conversationId);

    boolean canUserViewHistory(String requestingUser, String conversationId);
}
