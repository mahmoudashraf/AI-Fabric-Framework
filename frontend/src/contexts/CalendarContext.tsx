'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';
import { FormikValues } from 'formik';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface CalendarState {
  error: null | string;
  events: any[];
  loading: {
    events: boolean;
    actions: boolean;
  };
}

interface CalendarAction {
  type: string;
  payload?: any;
}

interface CalendarContextType {
  state: CalendarState;
  dispatch: React.Dispatch<CalendarAction>;
  // Actions
  getEvents: () => Promise<void>;
  addEvent: (event: FormikValues) => Promise<void>;
  updateEvent: (event: FormikValues) => Promise<void>;
  removeEvent: (eventId: string) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_CALENDAR_STATE: CalendarState = {
  error: null,
  events: [], // Preserved: Calendar events will be populated from API
  loading: {
    events: false,
    actions: false,
  },
};

// ==============|| CALENDAR REDUCER ||=============//

const calendarReducer = (state: CalendarState, action: CalendarAction): CalendarState => {
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
    case 'GET_EVENTS_SUCCESS':
      return {
        ...state,
        events: action.payload,
        loading: { ...state.loading, events: false },
      };
    case 'ADD_EVENT_SUCCESS':
      return {
        ...state,
        events: action.payload,
        loading: { ...state.loading, actions: false },
      };
    case 'UPDATE_EVENT_SUCCESS':
      return {
        ...state,
        events: action.payload,
        loading: { ...state.loading, actions: false },
      };
    case 'REMOVE_EVENT_SUCCESS':
      return {
        ...state,
        events: action.payload,
        loading: { ...state.loading, actions: false },
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const CalendarContext = createContext<CalendarContextType | null>(null);

type CalendarProviderProps = {
  children: ReactNode;
};

export const CalendarProvider = ({ children }: CalendarProviderProps) => {
  const [state, dispatch] = useReducer(calendarReducer, INITIAL_CALENDAR_STATE);

  // ==============|| ENHANCED API FUNCTIONS WITH LOADING STATES |=============//

  // Enterprise Pattern: Enhanced error handling with loading states
  const getEvents = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'events', value: true } });
    try {
      const response = await axios.get('/api/calendar/events');
      dispatch({ type: 'GET_EVENTS_SUCCESS', payload: response.data.events });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'events', value: false } });
      throw error;
    }
  };

  const addEvent = async (event: FormikValues) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/calendar/events/add', event);
      dispatch({ type: 'ADD_EVENT_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const updateEvent = async (event: FormikValues) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/calendar/events/update', event);
      dispatch({ type: 'UPDATE_EVENT_SUCCESS', payload: response.data.events });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const removeEvent = async (eventId: string) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/calendar/events/delete', { eventId });
      dispatch({ type: 'REMOVE_EVENT_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const contextValue: CalendarContextType = {
    state,
    dispatch,
    getEvents,
    addEvent,
    updateEvent,
    removeEvent,
  };

  return <CalendarContext.Provider value={contextValue}>{children}</CalendarContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useCalendar = () => {
  const context = useContext(CalendarContext);
  if (!context) {
    throw new Error('useCalendar must be used within a CalendarProvider');
  }
  return context;
};
