import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';

// Migration logging function
const logMigrationActivity = (action: string, context: string, data?: any) => {
  console.log(`[${context}] ${action}`, data || '');
};

// Types
export interface CalendarEvent {
  id: string | number;
  title: string;
  start: string;
  end: string;
  allDay?: boolean;
  color?: string;
  location?: string;
  description?: string;
  attendees?: Array<{
    id: number;
    name: string;
    email: string;
    status?: 'accepted' | 'declined' | 'tentative';
  }>;
  recurring?: {
    type: 'daily' | 'weekly' | 'monthly' | 'yearly';
    interval: number;
    endDate?: string;
  };
}

export interface EventFormData {
  title: string;
  start: Date;
  end: Date;
  allDay?: boolean;
  color?: string;
  location?: string;
  description?: string;
  attendees?: string[];
}

/**
 * React Query hooks for calendar data management
 */
export const useCalendarEvents = (filters?: Record<string, unknown>) => {
  return useQuery({
    queryKey: queryKeys.calendar.events(filters),
    queryFn: async () => {
      logMigrationActivity('Calendar events fetch', 'MARK_CALENDAR');
      const response = await axios.get('/api/calendar/events', { params: filters });
      return response.data;
    },
    staleTime: 2 * 60 * 1000, // 2 minutes - events can change frequently
  });
};

export const useUpcomingEvents = (days: number = 7) => {
  const now = new Date();
  const futureDate = new Date();
  futureDate.setDate(now.getDate() + days);
  
  const filters = {
    start: now.toISOString(),
    end: futureDate.toISOString(),
    upcoming: true
  };

  return useCalendarEvents(filters);
};

export const useEventById = (eventId: string | number) => {
  return useQuery({
    queryKey: ['event', eventId],
    queryFn: async () => {
      logMigrationActivity('Single event fetch', 'MARK_CALENDAR', { eventId });
      const response = await axios.get(`/api/calendar/events/${eventId}`);
      return response.data;
    },
    enabled: !!eventId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * Mutations for calendar operations
 */
export const useCreateEventMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (eventData: EventFormData) => {
      logMigrationActivity('Create event', 'MARK_CALENDAR');
      const response = await axios.post('/api/calendar/events', eventData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate calendar events queries
      queryClient.invalidateQueries({ queryKey: queryKeys.calendar.all });
      logMigrationActivity('Event created successfully', 'MARK_CALENDAR');
    },
    onError: (error) => {
      logMigrationActivity('Create event failed', 'MARK_CALENDAR', { error });
    },
  });
};

export const useUpdateEventMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ eventId, eventData }: { eventId: string | number; eventData: Partial<EventFormData> }) => {
      logMigrationActivity('Update event', 'MARK_CALENDAR', { eventId });
      const response = await axios.put(`/api/calendar/events/${eventId}`, eventData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate calendar events queries
      queryClient.invalidateQueries({ queryKey: queryKeys.calendar.all });
      logMigrationActivity('Event updated successfully', 'MARK_CALENDAR');
    },
    onError: (error) => {
      logMigrationActivity('Update event failed', 'MARK_CALENDAR', { error });
    },
  });
};

export const useDeleteEventMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (eventId: string | number) => {
      logMigrationActivity('Delete event', 'MARK_CALENDAR', { eventId });
      const response = await axios.delete(`/api/calendar/events/${eventId}`);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate calendar events queries
      queryClient.invalidateQueries({ queryKey: queryKeys.calendar.all });
      logMigrationActivity('Event deleted successfully', 'MARK_CALENDAR');
    },
    onError: (error) => {
      logMigrationActivity('Delete event failed', 'MARK_CALENDAR', { error });
    },
  });
};

/**
 * Combined calendar management hook
 */
export const useCalendarManagement = () => {
  const createEventMutation = useCreateEventMutation();
  const updateEventMutation = useUpdateEventMutation();
  const deleteEventMutation = useDeleteEventMutation();

  return {
    // Event operations
    createEvent: (eventData: EventFormData) => {
      createEventMutation.mutate(eventData);
    },
    updateEvent: (eventId: string | number, eventData: Partial<EventFormData>) => {
      updateEventMutation.mutate({ eventId, eventData });
    },
    deleteEvent: (eventId: string | number) => {
      deleteEventMutation.mutate(eventId);
    },
    
    // Loading states
    isCreatingEvent: createEventMutation.isPending,
    isUpdatingEvent: updateEventMutation.isPending,
    isDeletingEvent: deleteEventMutation.isPending,
    
    // Error states
    createEventError: createEventMutation.error,
    updateEventError: updateEventMutation.error,
    deleteEventError: deleteEventMutation.error,
  };
};

export default useCalendarManagement;
