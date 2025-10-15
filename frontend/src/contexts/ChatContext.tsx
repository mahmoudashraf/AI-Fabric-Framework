'use client';

import React, { createContext, useContext, useReducer, ReactNode, useRef, useCallback, useEffect } from 'react';

// types
import { ChatHistory } from 'types/chat';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface ChatState {
  error: null | string;
  chats: ChatHistory [];
  user: any;
  users: any[];
  loading: {
    user: boolean;
    chats: boolean;
    users: boolean;
    insert: boolean;
  };
}

interface ChatAction {
  type: string;
  payload?: any;
}

interface ChatContextType {
  state: ChatState;
  dispatch: React.Dispatch<ChatAction>;
  // Actions with enhanced error handling
  getUser: (id: number) => Promise<void>;
  getUserChats: (user: string | undefined) => Promise<void>;
  insertChat: (chat: ChatHistory) => Promise<void>;
  getUsers: () => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_CHAT_STATE: ChatState = {
  error: null,
  chats: [], // Preserved: Chat history
  user: {}, // Preserved: Current user data
  users: [], // Preserved: Users list
  loading: {
    user: false,
    chats: false,
    users: false,
    insert: false,
  },
};

// ==============|| CHAT REDUCER ||=============//

const chatReducer = (state: ChatState, action: ChatAction): ChatState => {
  switch (action.type) {
    case 'HAS_ERROR':
      return {
        ...state,
        error: action.payload,
      };
    case 'SET_LOADING':
      return {
        ...state,
        loading: { ...state.loading, [action.payload.key]: action.payload.value },
      };
    case 'GET_USER_SUCCESS':
      return {
        ...state,
        user: action.payload,
        loading: { ...state.loading, user: false },
      };
    case 'GET_USER_CHATS_SUCCESS':
      return {
        ...state,
        chats: action.payload,
        loading: { ...state.loading, chats: false },
      };
    case 'GET_USERS_SUCCESS':
      return {
        ...state,
        users: action.payload,
        loading: { ...state.loading, users: false },
      };
    case 'INSERT_CHAT_SUCCESS':
      return {
        ...state,
        loading: { ...state.loading, insert: false },
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const ChatContext = createContext<ChatContextType | null>(null);

type ChatProviderProps = {
  children: ReactNode;
};

export const ChatProvider = ({ children }: ChatProviderProps) => {
  const [state, dispatch] = useReducer(chatReducer, INITIAL_CHAT_STATE);
  
  // Request deduplication and throttling
  const requestCache = useRef<Map<string, Promise<any>>>(new Map());
  const lastRequestTime = useRef<Map<string, number>>(new Map());
  const REQUEST_THROTTLE_MS = 1000; // 1 second throttle
  const MAX_RETRIES = 3;
  const RETRY_DELAY_MS = 1000; // Base delay for exponential backoff

  // Helper function for retry with exponential backoff
  const retryWithBackoff = useCallback(async function<T>(
    requestFn: () => Promise<T>,
    retries = MAX_RETRIES
  ): Promise<T> {
    try {
      return await requestFn();
    } catch (error) {
      if (retries > 0) {
        const delay = RETRY_DELAY_MS * Math.pow(2, MAX_RETRIES - retries);
        await new Promise(resolve => setTimeout(resolve, delay));
        return retryWithBackoff(requestFn, retries - 1);
      }
      throw error;
    }
  }, []);

  // Helper function for throttled requests
  const throttledRequest = useCallback(async function<T>(
    key: string,
    requestFn: () => Promise<T>,
    forceRefresh = false
  ): Promise<T> {
    const now = Date.now();
    const lastTime = lastRequestTime.current.get(key) || 0;
    
    // Check if request is throttled
    if (!forceRefresh && now - lastTime < REQUEST_THROTTLE_MS) {
      // Return cached promise if available
      const cachedPromise = requestCache.current.get(key);
      if (cachedPromise) {
        return cachedPromise;
      }
    }
    
    // Check if request is already in progress
    const existingPromise = requestCache.current.get(key);
    if (existingPromise && !forceRefresh) {
      return existingPromise;
    }
    
    // Create new request
    const promise = requestFn().finally(() => {
      // Clean up cache after request completes
      requestCache.current.delete(key);
      lastRequestTime.current.set(key, now);
    });
    
    // Cache the promise
    requestCache.current.set(key, promise);
    
    return promise;
  }, []);

  // ==============|| ENHANCED API FUNCTIONS WITH THROTTLING |=============//

  // Enterprise Pattern: Enhanced error handling with retry logic and throttling
  const getUser = useCallback(async (id: number, forceRefresh = false) => {
    const cacheKey = `getUser-${id}`;
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'user', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.post('/api/chat/users/id', { id })
        );
        dispatch({ type: 'GET_USER_SUCCESS', payload: response.data });
        return response.data;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'user', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const getUserChats = useCallback(async (user: string | undefined, forceRefresh = false) => {
    if (!user) return;
    
    const cacheKey = `getUserChats-${user}`;
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'chats', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.post('/api/chat/filter', { user })
        );
        dispatch({ type: 'GET_USER_CHATS_SUCCESS', payload: response.data });
        return response.data;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'chats', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  const insertChat = useCallback(async (chat: ChatHistory) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'insert', value: true } });
    try {
      await axios.post('/api/chat/insert', chat);
      dispatch({ type: 'INSERT_CHAT_SUCCESS' });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'insert', value: false } });
      throw error;
    }
  }, []);

  const getUsers = useCallback(async (forceRefresh = false) => {
    const cacheKey = 'getUsers';
    
    return throttledRequest(cacheKey, async () => {
      dispatch({ type: 'SET_LOADING', payload: { key: 'users', value: true } });
      try {
        const response = await retryWithBackoff(() => 
          axios.get('/api/chat/users')
        );
        dispatch({ type: 'GET_USERS_SUCCESS', payload: response.data.users });
        return response.data.users;
      } catch (error) {
        dispatch({ type: 'HAS_ERROR', payload: error });
        dispatch({ type: 'SET_LOADING', payload: { key: 'users', value: false } });
        throw error;
      }
    }, forceRefresh);
  }, [throttledRequest]);

  // Cleanup effect to clear request cache on unmount
  useEffect(() => {
    return () => {
      requestCache.current.clear();
      lastRequestTime.current.clear();
    };
  }, []);

  const contextValue: ChatContextType = {
    state,
    dispatch,
    getUser,
    getUserChats,
    insertChat,
    getUsers,
  };

  return <ChatContext.Provider value={contextValue}>{children}</ChatContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useChat = () => {
  const context = useContext(ChatContext);
  if (!context) {
    throw new Error('useChat must be used within a ChatProvider');
  }
  return context;
};
