import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';
// Using Context API

// Types
export interface ChatUser {
  id: number;
  name: string;
  avatar?: string;
  status?: 'online' | 'offline' | 'away';
  lastMessage?: string;
  lastMessageTime?: string;
}

export interface ChatHistory {
  id: number;
  from: string;
  message: string;
  to: string;
  time?: string;
}

export interface ChatMessage {
  id: number;
  sender: string;
  content: string;
  timestamp: string;
  type?: 'text' | 'image' | 'file';
}

/**
 * React Query hooks for chat data management
 */
export const useChatUsers = () => {
  return useQuery({
    queryKey: queryKeys.chat.users(),
    queryFn: async () => {
      logMigrationActivity('Chat users fetch', 'MARK_CHAT');
      const response = await axios.get('/api/chat/users');
      return response.data;
    },
    staleTime: 30 * 1000, // 30 seconds - users change frequently
  });
};

export const useChatUser = (userId: number) => {
  return useQuery({
    queryKey: queryKeys.chat.conversations(userId.toString()),
    queryFn: async () => {
      logMigrationActivity('Chat user fetch', 'MARK_CHAT', { userId });
      const response = await axios.get(`/api/chat/user/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 10 * 60 * 1000, // 10 minutes - user profile changes less frequently
  });
};

export const useChatConversations = (userId: string) => {
  return useQuery({
    queryKey: queryKeys.chat.conversations(userId),
    queryFn: async () => {
      logMigrationActivity('Chat conversations fetch', 'MARK_CHAT', { userId });
      const response = await axios.get(`/api/chat/conversations/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 1 * 60 * 1000, // 1 minute - conversations update frequently
  });
};

export const useChatMessages = (conversationId: string) => {
  return useQuery({
    queryKey: queryKeys.chat.messages(conversationId),
    queryFn: async () => {
      logMigrationActivity('Chat messages fetch', 'MARK_CHAT', { conversationId });
      const response = await axios.get(`/api/chat/messages/${conversationId}`);
      return response.data;
    },
  enabled: !!conversationId,
  staleTime: 30 * 1000, // 30 seconds - messages update very frequently
  refetchInterval: 5 * 1000, // Auto-refresh every 5 seconds for real-time feel
});
}

/**
 * Mutations for chat operations
 */
export const useSendMessageMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ conversationId, message }: { conversationId: string; message: ChatMessage }) => {
      logMigrationActivity('Send message', 'MARK_CHAT', { conversationId });
      const response = await axios.post(`/api/chat/messages/${conversationId}`, message);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate conversations and messages queries
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.all });
      logMigrationActivity('Message sent successfully', 'MARK_CHAT');
    },
    onError: (error) => {
      logMigrationActivity('Send message failed', 'MARK_CHAT', { error });
    },
  });
};

export const useCreateConversationMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ participants }: { participants: number[] }) => {
      logMigrationActivity('Create conversation', 'MARK_CHAT', { participants });
      const response = await axios.post('/api/chat/conversations', { participants });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate conversations queries
      queryClient.invalidateQueries({ queryKey: queryKeys.chat.all });
      logMigrationActivity('Conversation created successfully', 'MARK_CHAT');
    },
    onError: (error) => {
      logMigrationActivity('Create conversation failed', 'MARK_CHAT', { error });
    },
  });
};

/**
 * Combined chat management hook
 */
export const useChatManagement = () => {
  const sendMessageMutation = useSendMessageMutation();
  const createConversationMutation = useCreateConversationMutation();

  return {
    // Message operations
    sendMessage: (conversationId: string, message: ChatMessage) => {
      sendMessageMutation.mutate({ conversationId, message });
    },
    isSendingMessage: sendMessageMutation.isPending,
    sendMessageError: sendMessageMutation.error,

    // Conversation operations
    createConversation: (participants: number[]) => {
      createConversationMutation.mutate({ participants });
    },
    isCreatingConversation: createConversationMutation.isPending,
    createConversationError: createConversationMutation.error,
  };
};

export default useChatManagement;
