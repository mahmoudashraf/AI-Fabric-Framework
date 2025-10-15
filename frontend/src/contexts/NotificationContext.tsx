'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// Types
export interface Notification {
  id: string;
  message: string;
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info' | 'alert';
  // Optional open flag for legacy call sites; context rendering ignores it and manages visibility internally
  open?: boolean;
  anchorOrigin?: {
    vertical: 'top' | 'bottom';
    horizontal: 'left' | 'center' | 'right';
  };
  alert?: {
    color: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';
    variant: 'filled' | 'outlined' | 'standard';
  };
  transition?: 'Fade' | 'Slide' | 'Grow';
  close?: boolean;
  actionButton?: boolean;
  maxStack?: number;
  dense?: boolean;
  iconVariant?: 'hide' | 'default';
}

interface NotificationState {
  notifications: Notification[];
  action: boolean; // Triggers re-render for snackbar state updates
}

type NotificationAction =
  | { type: 'SHOW_NOTIFICATION'; payload: Notification }
  | { type: 'REMOVE_NOTIFICATION'; payload: string }
  | { type: 'CLEAR_ALL_NOTIFICATIONS' }
  | { type: 'TOGGLE_ACTION' };

// Context type
interface NotificationContextType {
  state: NotificationState;
  showNotification: (notification: Omit<Notification, 'id'>) => void;
  // Legacy compatibility helper
  showNotifications: (notification: Omit<Notification, 'id'>) => void;
  removeNotification: (id: string) => void;
  // Close the first (oldest) notification if any
  closeNotification: () => void;
  clearAllNotifications: () => void;
}

// Context
export const NotificationContext = createContext<NotificationContextType | null>(null);

// Initial state
const initialState: NotificationState = {
  notifications: [],
  action: false,
};

// Reducer
function notificationReducer(state: NotificationState, action: NotificationAction): NotificationState {
  switch (action.type) {
    case 'SHOW_NOTIFICATION':
      const newNotification = {
        ...action.payload,
        id: action.payload.id || `notification-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        anchorOrigin: action.payload.anchorOrigin || {
          vertical: 'bottom',
          horizontal: 'right',
        },
        variant: action.payload.variant || 'default',
        alert: action.payload.alert || {
          color: 'primary',
          variant: 'filled',
        },
        transition: action.payload.transition || 'Fade',
        close: action.payload.close !== false,
        maxStack: action.payload.maxStack || 3,
        dense: action.payload.dense || false,
        iconVariant: action.payload.iconVariant || 'hide',
        actionButton: action.payload.actionButton || false,
      };

      return {
        ...state,
        notifications: [...state.notifications, newNotification].slice(-(state.notifications.length >= (newNotification.maxStack || 3) ? 1 : 0)),
        action: !state.action, // Toggle to trigger re-render
      };

    case 'REMOVE_NOTIFICATION':
      return {
        ...state,
        notifications: state.notifications.filter(n => n.id !== action.payload),
        action: !state.action,
      };

    case 'CLEAR_ALL_NOTIFICATIONS':
      return {
        ...state,
        notifications: [],
        action: !state.action,
      };

    case 'TOGGLE_ACTION':
      return {
        ...state,
        action: !state.action,
      };

    default:
      return state;
  }
}

// Provider component
export const NotificationProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(notificationReducer, initialState);

  const showNotification = (notification: Omit<Notification, 'id'>) => {
    dispatch({
      type: 'SHOW_NOTIFICATION',
      payload: notification as Notification,
    });
  };

  const removeNotification = (id: string) => {
    dispatch({
      type: 'REMOVE_NOTIFICATION',
      payload: id,
    });
  };

  const closeNotification = () => {
    const first = state.notifications[0];
    if (first) {
      dispatch({ type: 'REMOVE_NOTIFICATION', payload: first.id });
    }
  };

  const clearAllNotifications = () => {
    dispatch({
      type: 'CLEAR_ALL_NOTIFICATIONS',
    });
  };

  const value: NotificationContextType = {
    state,
    showNotification,
    showNotifications: (n) => showNotification(n),
    removeNotification,
    closeNotification,
    clearAllNotifications,
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
};

// Hook for using notifications
export const useNotifications = (): NotificationContextType => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
};

// Convenience hook for snackbar functionality (compatible with existing Redux API)
export const useSnackbar = () => {
  const { showNotification, removeNotification } = useNotifications();

  const openSnackbar = (notification: Omit<Notification, 'id'>) => {
    showNotification(notification);
  };

  const closeSnackbar = (id: string) => {
    removeNotification(id);
  };

  // Legacy compatibility - show the first notification and auto-generate ID
  const showFirstSnackbar = (message: string, options?: Partial<Notification>) => {
    showNotification({
      ...options,
      message,
    });
  };

  return {
    openSnackbar,
    closeSnackbar,
    showSnackbar: showFirstSnackbar, // Simple API
  };
};

export default NotificationProvider;
