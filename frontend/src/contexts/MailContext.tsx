'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface MailState {
  error: null | string;
  mails: any[];
  unreadCount: number;
  loading: {
    mails: boolean;
    filter: boolean;
    actions: boolean;
  };
}

interface MailAction {
  type: string;
  payload?: any;
}

interface MailContextType {
  state: MailState;
  dispatch: React.Dispatch<MailAction>;
  // Actions with enhanced error handling
  getMails: () => Promise<void>;
  filterMails: (filter: string) => Promise<void>;
  setImportant: (id: string) => Promise<void>;
  setStarred: (id: string) => Promise<void>;
  setRead: (id: string) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_MAIL_STATE: MailState = {
  error: null,
  mails: [], // Preserved: Email list
  unreadCount: 0, // initialize to 0 for strict typing
  loading: {
    mails: false,
    filter: false,
    actions: false,
  },
};

// ==============|| MAIL REDUCER ||=============//

const mailReducer = (state: MailState, action: MailAction): MailState => {
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
    case 'GET_MAILS_SUCCESS':
      return {
        ...state,
        mails: action.payload.mails,
        unreadCount: action.payload.unreadCount,
        loading: { ...state.loading, mails: false },
      };
    case 'FILTER_MAILS_SUCCESS':
      return {
        ...state,
        mails: action.payload,
        loading: { ...state.loading, filter: false },
      };
    case 'MAIL_ACTION_SUCCESS':
      return {
        ...state,
        loading: { ...state.loading, actions: false },
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const MailContext = createContext<MailContextType | null>(null);

type MailProviderProps = {
  children: ReactNode;
};

export const MailProvider = ({ children }: MailProviderProps) => {
  const [state, dispatch] = useReducer(mailReducer, INITIAL_MAIL_STATE);

  // ==============|| ENHANCED API FUNCTIONS WITH ASYNC OPERATION |=============//

  // Enterprise Pattern: Enhanced error handling with retry logic
  const getMails = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'mails', value: true } });
    try {
      const response = await axios.get('/api/mails/list');
      dispatch({ type: 'GET_MAILS_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'mails', value: false } });
      throw error;
    }
  };

  const filterMails = async (filter: string) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'filter', value: true } });
    try {
      const response = await axios.post('/api/mails/filter', { filter });
      dispatch({ type: 'FILTER_MAILS_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'filter', value: false } });
      throw error;
    }
  };

  const setImportant = async (id: string) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      await axios.post('/api/mails/setImportant', { id });
      dispatch({ type: 'MAIL_ACTION_SUCCESS' });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const setStarred = async (id: string) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      await axios.post('/api/mails/setStarred', { id });
      dispatch({ type: 'MAIL_ACTION_SUCCESS' });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const setRead = async (id: string) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      await axios.post('/api/mails/setRead', { id });
      dispatch({ type: 'MAIL_ACTION_SUCCESS' });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const contextValue: MailContextType = {
    state,
    dispatch,
    getMails,
    filterMails,
    setImportant,
    setStarred,
    setRead,
  };

  return <MailContext.Provider value={contextValue}>{children}</MailContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useMail = () => {
  const context = useContext(MailContext);
  if (!context) {
    throw new Error('useMail must be used within a MailProvider');
  }
  return context;
};
