// Modern Notifications Hook
// Pure Context API implementation

import { useNotifications } from 'contexts/NotificationContext';

/**
 * Modern Notifications Hook  
 * Provides comprehensive notification management using Context API
 */
export const useModernNotifications = () => {
  const notificationContext = useNotifications();

  return {
    // Notification operations
    showNotification: (options: {
      open: boolean;
      message: string;
      variant?: 'default' | 'alert';
      alert?: {
        color: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';
        variant: 'filled' | 'outlined';
      };
      close?: boolean;
    }) => {
      return notificationContext.showNotification(options);
    },

    closeNotification: () => {
      return notificationContext.closeNotification();
    },

    // Convenience methods
    showSuccess: (message: string) => {
      return notificationContext.showNotification({
        open: true,
        message,
        variant: 'alert',
        alert: { color: 'success', variant: 'filled' }
      });
    },

    showError: (message: string) => {
      return notificationContext.showNotification({
        open: true,
        message,
        variant: 'alert', 
        alert: { color: 'error', variant: 'filled' }
      });
    },

    showWarning: (message: string) => {
      return notificationContext.showNotification({
        open: true,
        message,
        variant: 'alert',
        alert: { color: 'warning', variant: 'filled' }
      });
    },

    showInfo: (message: string) => {
      return notificationContext.showNotification({
        open: true,
        message,
        variant: 'alert',
        alert: { color: 'info', variant: 'filled' }
      });
    },

    // Legacy compatibility methods
    openSnackbar: (options: any) => {
      notificationContext.showNotification({
        open: true,
        message: options.message || options.text || 'Notification',
        variant: options.variant || 'default',
        alert: options.alert || { color: 'info', variant: 'filled' }
      });
    },

    closeSnackbar: () => {
      notificationContext.closeNotification();
    },
  };
};

export default useModernNotifications;
