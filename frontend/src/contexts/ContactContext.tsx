'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// types
import { UserProfile } from 'types/user-profile';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface ContactState {
  error: null | string;
  contacts: UserProfile[];
}

interface ContactAction {
  type: string;
  payload?: any;
}

interface ContactContextType {
  state: ContactState;
  dispatch: React.Dispatch<ContactAction>;
  // Actions
  getContacts: () => Promise<void>;
  modifyContact: (contact: UserProfile) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_CONTACT_STATE: ContactState = {
  error: null,
  contacts: [], // Preserved: Contact list will be populated from API
};

// ==============|| CONTACT REDUCER ||=============//

const contactReducer = (state: ContactState, action: ContactAction): ContactState => {
  switch (action.type) {
    case 'HAS_ERROR':
      return {
        ...state,
        error: action.payload,
      };
    case 'GET_CONTACTS_SUCCESS':
      return {
        ...state,
        contacts: action.payload,
      };
    case 'MODIFY_CONTACT_SUCCESS':
      return {
        ...state,
        contacts: action.payload,
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const ContactContext = createContext<ContactContextType | null>(null);

type ContactProviderProps = {
  children: ReactNode;
};

export const ContactProvider = ({ children }: ContactProviderProps) => {
  const [state, dispatch] = useReducer(contactReducer, INITIAL_CONTACT_STATE);

  // ==============|| PRESERVED API FUNCTIONS |=============//

  // All API endpoints preserved exactly as in Redux slice
  const getContacts = async () => {
    try {
      const response = await axios.get('/api/contact/list');
      dispatch({ type: 'GET_CONTACTS_SUCCESS', payload: response.data.contacts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const modifyContact = async (contact: UserProfile) => {
    try {
      const response = await axios.post('/api/contact/modify', contact);
      dispatch({ type: 'MODIFY_CONTACT_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const contextValue: ContactContextType = {
    state,
    dispatch,
    getContacts,
    modifyContact,
  };

  return <ContactContext.Provider value={contextValue}>{children}</ContactContext.Provider>;
};

// ==============|| CUSTOM HOOK ||============://

export const useContact = () => {
  const context = useContext(ContactContext);
  if (!context) {
    throw new Error('useContact must be used within a ContactProvider');
  }
  return context;
};
