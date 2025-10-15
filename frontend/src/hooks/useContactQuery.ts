import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'utils/axios';
// Using Context API

// Types
export interface Contact {
  id: number;
  name: string;
  email: string;
  phone?: string;
  company?: string;
  job?: string;
  location?: string;
  avatar?: string;
  status?: 'active' | 'inactive';
  tags?: string[];
  notes?: string;
  lastContact?: string;
}

export interface ContactFormData {
  name: string;
  email: string;
  phone?: string;
  company?: string;
  job?: string;
  location?: string;
  tags?: string[];
  notes?: string;
}

/**
 * React Query hooks for contact data management
 */
export const useContacts = (filters?: Record<string, unknown>) => {
  return useQuery({
    queryKey: ['contacts', filters],
    queryFn: async () => {
      logMigrationActivity('Contacts list fetch', 'MARK_CONTACT');
      const response = await axios.get('/api/contacts', { params: filters });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useContactById = (contactId: string | number) => {
  return useQuery({
    queryKey: ['contact', contactId],
    queryFn: async () => {
      logMigrationActivity('Single contact fetch', 'MARK_CONTACT', { contactId });
      const response = await axios.get(`/api/contacts/${contactId}`);
      return response.data;
    },
    enabled: !!contactId,
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
};

export const useContactSearch = (searchTerm: string) => {
  return useQuery({
    queryKey: ['contacts', 'search', searchTerm],
    queryFn: async () => {
      logMigrationActivity('Contact search', 'MARK_CONTACT', { searchTerm });
      const response = await axios.get('/api/contacts/search', { 
        params: { q: searchTerm }
      });
      return response.data;
    },
    enabled: !!searchTerm && searchTerm.length >= 2, // Only search with 2+ characters
    staleTime: 1 * 60 * 1000, // 1 minute for search results
  });
};

/**
 * Mutations for contact operations
 */
export const useCreateContactMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (contactData: ContactFormData) => {
      logMigrationActivity('Create contact', 'MARK_CONTACT');
      const response = await axios.post('/api/contacts', contactData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate contacts queries
      queryClient.invalidateQueries({ queryKey: ['contacts'] });
      logMigrationActivity('Contact created successfully', 'MARK_CONTACT');
    },
    onError: (error) => {
      logMigrationActivity('Create contact failed', 'MARK_CONTACT', { error });
    },
  });
};

export const useUpdateContactMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ contactId, contactData }: { contactId: string | number; contactData: Partial<ContactFormData> }) => {
      logMigrationActivity('Update contact', 'MARK_CONTACT', { contactId });
      const response = await axios.put(`/api/contacts/${contactId}`, contactData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate contacts queries
      queryClient.invalidateQueries({ queryKey: ['contacts'] });
      logMigrationActivity('Contact updated successfully', 'MARK_CONTACT');
    },
    onError: (error) => {
      logMigrationActivity('Update contact failed', 'MARK_CONTACT', { error });
    },
  });
};

export const useDeleteContactMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (contactId: string | number) => {
      logMigrationActivity('Delete contact', 'MARK_CONTACT', { contactId });
      const response = await axios.delete(`/api/contacts/${contactId}`);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate contacts queries
      queryClient.invalidateQueries({ queryKey: ['contacts'] });
      logMigrationActivity('Contact deleted successfully', 'MARK_CONTACT');
    },
    onError: (error) => {
      logMigrationActivity('Delete contact failed', 'MARK_CONTACT', { error });
    },
  });
};

/**
 * Combined contact management hook
 */
export const useContactManagement = () => {
  const createContactMutation = useCreateContactMutation();
  const updateContactMutation = useUpdateContactMutation();
  const deleteContactMutation = useDeleteContactMutation();

  return {
    // Contact operations
    createContact: (contactData: ContactFormData) => {
      createContactMutation.mutate(contactData);
    },
    updateContact: (contactId: string | number, contactData: Partial<ContactFormData>) => {
      updateContactMutation.mutate({ contactId, contactData });
    },
    deleteContact: (contactId: string | number) => {
      deleteContactMutation.mutate(contactId);
    },
    
    // Loading states
    isCreatingContact: createContactMutation.isPending,
    isUpdatingContact: updateContactMutation.isPending,
    isDeletingContact: deleteContactMutation.isPending,
    
    // Error states
    createContactError: createContactMutation.error,
    updateContactError: updateContactMutation.error,
    deleteContactError: deleteContactMutation.error,
  };
};

export default useContactManagement;