import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';
// Using Context API

// Types
export interface UserProfile {
  id?: number;
  name?: string;
  avatar?: string;
  [key: string]: unknown;
}

export interface UserS1 {
  id: number;
  name: string;
  avatar: string;
  status: string;
  [key: string]: unknown;
}

export interface UserS2 {
  id: number;
  name: string;
  avatar: string;
  email: string;
  [key: string]: unknown;
}

export interface Reply {
  id: number;
  profile: number;
  data: {
    comment: string;
    data?: {
      profile: string;
    };
  };
}

export interface FriendRequest {
  id: number;
  name: string;
  avatar: string;
  status: string;
}

export interface Post {
  id: number;
  content: string;
  profile: string;
  media?: string;
}

/**
 * React Query hooks for user data management
 */
export const useUserProfile = (id: number) => {
  return useQuery({
    queryKey: queryKeys.user.profile(id.toString()),
    queryFn: async () => {
      logMigrationActivity('User profile fetch', 'MARK_USER');
      const response = await axios.get(`/api/user/profile/${id}`);
      return response.data;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUsersS1 = () => {
  return useQuery({
    queryKey: queryKeys.user.list(),
    queryFn: async () => {
      logMigrationActivity('Users S1 list fetch', 'MARK_USER');
      const response = await axios.get('/api/user/list-style1');
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUsersS2 = () => {
  return useQuery({
    queryKey: queryKeys.user.listS2(),
    queryFn: async () => {
      logMigrationActivity('Users S2 list fetch', 'MARK_USER');
      const response = await axios.get('/api/user/list-style2');
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUserFollowers = (userId: number) => {
  return useQuery({
    queryKey: queryKeys.user.followers(userId.toString()),
    queryFn: async () => {
      logMigrationActivity('User followers fetch', 'MARK_USER');
      const response = await axios.get(`/api/user/followers/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUserFriends = (userId: number) => {
  return useQuery({
    queryKey: queryKeys.user.friends(userId.toString()),
    queryFn: async () => {
      logMigrationActivity('User friends fetch', 'MARK_USER');
      const response = await axios.get(`/api/user/friends/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUserGallery = (userId: number) => {
  return useQuery({
    queryKey: queryKeys.user.gallery(userId.toString()),
    queryFn: async () => {
      logMigrationActivity('User gallery fetch', 'MARK_USER');
      const response = await axios.get(`/api/user/gallery/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 10 * 60 * 1000, // 10 minutes - gallery changes less frequently
  });
};

export const useUserPosts = (userId: number) => {
  return useQuery({
    queryKey: queryKeys.user.posts(userId.toString()),
    queryFn: async () => {
      logMigrationActivity('User posts fetch', 'MARK_USER');
      const response = await axios.get(`/api/user/posts/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 2 * 60 * 1000, // 2 minutes - posts change frequently
  });
};

/**
 * Mutations for user operations
 */
export const useUpdateUserProfileMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async (profileData: Partial<UserProfile>) => {
      logMigrationActivity('User profile update', 'MARK_USER');
      const response = await axios.put('/api/user/profile', profileData);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate user-related queries
      queryClient.invalidateQueries({ queryKey: queryKeys.user.all });
      logMigrationActivity('User profile updated successfully', 'MARK_USER');
    },
    onError: (error) => {
      logMigrationActivity('User profile update failed', 'MARK_USER', { error });
    },
  });
};

export const useFriendRequestMutation = () => {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: async ({ friendId, action }: { friendId: number; action: 'accept' | 'reject' | 'send' }) => {
      logMigrationActivity('Friend request action', 'MARK_USER', { friendId, action });
      const response = await axios.post('/api/user/friend-request', { friendId, action });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate friends and followers queries
      queryClient.invalidateQueries({ queryKey: queryKeys.user.all });
      logMigrationActivity('Friend request processed successfully', 'MARK_USER');
    },
    onError: (error) => {
      logMigrationActivity('Friend request failed', 'MARK_USER', { error });
    },
  });
};

/**
 * Combined user management hook
 */
export const useUserManagement = () => {
  const updateProfileMutation = useUpdateUserProfileMutation();
  const friendRequestMutation = useFriendRequestMutation();

  return {
    // Profile operations
    updateProfile: updateProfileMutation.mutate,
    isUpdatingProfile: updateProfileMutation.isPending,
    profileUpdateError: updateProfileMutation.error,

    // Friend operations
    sendFriendRequest: (friendId: number) => {
      friendRequestMutation.mutate({ friendId, action: 'send' });
    },
    acceptFriendRequest: (friendId: number) => {
      friendRequestMutation.mutate({ friendId, action: 'accept' });
    },
    rejectFriendRequest: (friendId: number) => {
      friendRequestMutation.mutate({ friendId, action: 'reject' });
    },
    isFriendRequestPending: friendRequestMutation.isPending,
    friendRequestError: friendRequestMutation.error,
  };
};

export default useUserManagement;
