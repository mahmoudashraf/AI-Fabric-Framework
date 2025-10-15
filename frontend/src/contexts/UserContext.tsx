'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// types
import { Reply } from 'types/user-profile';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface UserState {
  error: null | string;
  usersS1: any[];
  usersS2: any[];
  followers: any[];
  friendRequests: any[];
  friends: any[];
  gallery: any[];
  posts: any[];
  detailCards: any[];
  simpleCards: any[];
  profileCards: any[];
}

interface UserAction {
  type: string;
  payload?: any;
}

interface UserContextType {
  state: UserState;
  dispatch: React.Dispatch<UserAction>;
  // Compatibility aliases (direct fields expected by some components)
  usersS1: any[];
  usersS2: any[];
  friendRequests: any[];
  friends: any[];
  gallery: any[];
  posts: any[];
  // Actions - All preserved from Redux slice
  getUsersListStyle1: () => Promise<void>;
  getUsersListStyle2: () => Promise<void>;
  getFollowers: () => Promise<void>;
  filterFollowers: (key: string) => Promise<void>;
  getFriendRequests: () => Promise<void>;
  filterFriendRequests: (key: string) => Promise<void>;
  getFriends: () => Promise<void>;
  filterFriends: (key: string) => Promise<void>;
  getGallery: () => Promise<void>;
  getPosts: () => Promise<void>;
  editComment: (key: string, id: string) => Promise<void>;
  addComment: (postId: string, comment: Reply) => Promise<void>;
  addReply: (postId: string, commentId: string, reply: Reply) => Promise<void>;
  likePost: (postId: string) => Promise<void>;
  likeComment: (postId: string, commentId: string) => Promise<void>;
  likeReply: (postId: string, commentId: string, replayId: string) => Promise<void>;
  getDetailCards: () => Promise<void>;
  filterDetailCards: (key: string) => Promise<void>;
  getSimpleCards: () => Promise<void>;
  filterSimpleCards: (key: string) => Promise<void>;
  getProfileCards: () => Promise<void>;
  filterProfileCards: (key: string) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_USER_STATE: UserState = {
  error: null,
  usersS1: [], // Preserved: Users style 1
  usersS2: [], // Preserved: Users style 2
  followers: [], // Preserved: Followers list
  friendRequests: [], // Preserved: Friend requests
  friends: [], // Preserved: Friends list
  gallery: [], // Preserved: Gallery photos
  posts: [], // Preserved: Social posts
  detailCards: [], // Preserved: Detail cards
  simpleCards: [], // Preserved: Simple cards
  profileCards: [], // Preserved: Profile cards
};

// ==============|| USER REDUCER ||=============//

const userReducer = (state: UserState, action: UserAction): UserState => {
  switch (action.type) {
    case 'HAS_ERROR':
      return {
        ...state,
        error: action.payload,
      };
    case 'GET_USERS_LIST_STYLE_1_SUCCESS':
      return {
        ...state,
        usersS1: action.payload,
      };
    case 'GET_USERS_LIST_STYLE_2_SUCCESS':
      return {
        ...state,
        usersS2: action.payload,
      };
    case 'GET_FOLLOWERS_SUCCESS':
      return {
        ...state,
        followers: action.payload,
      };
    case 'FILTER_FOLLOWERS_SUCCESS':
      return {
        ...state,
        followers: action.payload,
      };
    case 'GET_FRIEND_REQUESTS_SUCCESS':
      return {
        ...state,
        friendRequests: action.payload,
      };
    case 'FILTER_FRIEND_REQUESTS_SUCCESS':
      return {
        ...state,
        friendRequests: action.payload,
      };
    case 'GET_FRIENDS_SUCCESS':
      return {
        ...state,
        friends: action.payload,
      };
    case 'FILTER_FRIENDS_SUCCESS':
      return {
        ...state,
        friends: action.payload,
      };
    case 'GET_GALLERY_SUCCESS':
      return {
        ...state,
        gallery: action.payload,
      };
    case 'GET_POSTS_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'EDIT_COMMENT_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'ADD_COMMENT_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'ADD_REPLY_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'LIKE_POST_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'LIKE_COMMENT_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'LIKE_REPLY_SUCCESS':
      return {
        ...state,
        posts: action.payload,
      };
    case 'GET_DETAIL_CARDS_SUCCESS':
      return {
        ...state,
        detailCards: action.payload,
      };
    case 'FILTER_DETAIL_CARDS_SUCCESS':
      return {
        ...state,
        detailCards: action.payload,
      };
    case 'GET_SIMPLE_CARDS_SUCCESS':
      return {
        ...state,
        simpleCards: action.payload,
      };
    case 'FILTER_SIMPLE_CARDS_SUCCESS':
      return {
        ...state,
        simpleCards: action.payload,
      };
    case 'GET_PROFILE_CARDS_SUCCESS':
      return {
        ...state,
        profileCards: action.payload,
      };
    case 'FILTER_PROFILE_CARDS_SUCCESS':
      return {
        ...state,
        profileCards: action.payload,
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const UserContext = createContext<UserContextType | null>(null);

type UserProviderProps = {
  children: ReactNode;
};

export const UserProvider = ({ children }: UserProviderProps) => {
  const [state, dispatch] = useReducer(userReducer, INITIAL_USER_STATE);

  // ==============|| PRESERVED API FUNCTIONS |=============//

  // All API endpoints preserved exactly as in Redux slice

  const getUsersListStyle1 = async () => {
    try {
      const response = await axios.get('/api/user-list/s1/list');
      dispatch({ type: 'GET_USERS_LIST_STYLE_1_SUCCESS', payload: response.data.users_s1 });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getUsersListStyle2 = async () => {
    try {
      const response = await axios.get('/api/user-list/s2/list');
      dispatch({ type: 'GET_USERS_LIST_STYLE_2_SUCCESS', payload: response.data.users_s2 });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getFollowers = async () => {
    try {
      const response = await axios.get('/api/followers/list');
      dispatch({ type: 'GET_FOLLOWERS_SUCCESS', payload: response.data.followers });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterFollowers = async (key: string) => {
    try {
      const response = await axios.post('/api/followers/filter', { key });
      dispatch({ type: 'FILTER_FOLLOWERS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getFriendRequests = async () => {
    try {
      const response = await axios.get('/api/friend-request/list');
      dispatch({ type: 'GET_FRIEND_REQUESTS_SUCCESS', payload: response.data.friends });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterFriendRequests = async (key: string) => {
    try {
      const response = await axios.post('/api/friend-request/filter', { key });
      dispatch({ type: 'FILTER_FRIEND_REQUESTS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getFriends = async () => {
    try {
      const response = await axios.get('/api/friends/list');
      dispatch({ type: 'GET_FRIENDS_SUCCESS', payload: response.data.friends });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterFriends = async (key: string) => {
    try {
      const response = await axios.post('/api/friends/filter', { key });
      dispatch({ type: 'FILTER_FRIENDS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getGallery = async () => {
    try {
      const response = await axios.get('/api/gallery/list');
      dispatch({ type: 'GET_GALLERY_SUCCESS', payload: response.data.gallery });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getPosts = async () => {
    try {
      const response = await axios.get('/api/posts/list');
      dispatch({ type: 'GET_POSTS_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const editComment = async (key: string, id: string) => {
    try {
      const response = await axios.post('/api/posts/editComment', { key, id });
      dispatch({ type: 'EDIT_COMMENT_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const addComment = async (postId: string, comment: Reply) => {
    try {
      const response = await axios.post('/api/comments/add', { postId, comment });
      dispatch({ type: 'ADD_COMMENT_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const addReply = async (postId: string, commentId: string, reply: Reply) => {
    try {
      const response = await axios.post('/api/replies/add', { postId, commentId, reply });
      dispatch({ type: 'ADD_REPLY_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const likePost = async (postId: string) => {
    try {
      const response = await axios.post('/api/posts/list/like', { postId });
      dispatch({ type: 'LIKE_POST_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const likeComment = async (postId: string, commentId: string) => {
    try {
      const response = await axios.post('/api/comments/list/like', { postId, commentId });
      dispatch({ type: 'LIKE_COMMENT_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const likeReply = async (postId: string, commentId: string, replayId: string) => {
    try {
      const response = await axios.post('/api/replies/list/like', { postId, commentId, replayId });
      dispatch({ type: 'LIKE_REPLY_SUCCESS', payload: response.data.posts });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getDetailCards = async () => {
    try {
      const response = await axios.get('/api/details-card/list');
      dispatch({ type: 'GET_DETAIL_CARDS_SUCCESS', payload: response.data.users });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterDetailCards = async (key: string) => {
    try {
      const response = await axios.post('/api/details-card/filter', { key });
      dispatch({ type: 'FILTER_DETAIL_CARDS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getSimpleCards = async () => {
    try {
      const response = await axios.get('/api/simple-card/list');
      dispatch({ type: 'GET_SIMPLE_CARDS_SUCCESS', payload: response.data.users });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterSimpleCards = async (key: string) => {
    try {
      const response = await axios.post('/api/simple-card/filter', { key });
      dispatch({ type: 'FILTER_SIMPLE_CARDS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const getProfileCards = async () => {
    try {
      const response = await axios.get('/api/profile-card/list');
      dispatch({ type: 'GET_PROFILE_CARDS_SUCCESS', payload: response.data.users });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const filterProfileCards = async (key: string) => {
    try {
      const response = await axios.post('/api/profile-card/filter', { key });
      dispatch({ type: 'FILTER_PROFILE_CARDS_SUCCESS', payload: response.data.results });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const contextValue: UserContextType = {
    state,
    dispatch,
    usersS1: state.usersS1,
    usersS2: state.usersS2,
    friendRequests: state.friendRequests,
    friends: state.friends,
    gallery: state.gallery,
    posts: state.posts,
    getUsersListStyle1,
    getUsersListStyle2,
    getFollowers,
    filterFollowers,
    getFriendRequests,
    filterFriendRequests,
    getFriends,
    filterFriends,
    getGallery,
    getPosts,
    editComment,
    addComment,
    addReply,
    likePost,
    likeComment,
    likeReply,
    getDetailCards,
    filterDetailCards,
    getSimpleCards,
    filterSimpleCards,
    getProfileCards,
    filterProfileCards,
  };

  return <UserContext.Provider value={contextValue}>{children}</UserContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useUser = () => {
  const context = useContext(UserContext);
  if (!context) {
    throw new Error('useUser must be used within a UserProvider');
  }
  return context;
};
