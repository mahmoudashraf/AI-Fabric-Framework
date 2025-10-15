'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// types
import { KanbanColumn, KanbanComment, KanbanItem, KanbanUserStory } from 'types/kanban';

// project imports
import axios from 'utils/axios';

// ==============| Types |=============//

interface KanbanState {
  error: null | string;
  columns: KanbanColumn[];
  columnsOrder: string[];
  comments: KanbanComment[];
  items: KanbanItem[];
  profiles: any[];
  selectedItem: string | false;
  userStory: KanbanUserStory[];
  userStoryOrder: string[];
  loading: {
    columns: boolean;
    comments: boolean;
    profiles: boolean;
    items: boolean;
    userStory: boolean;
    actions: boolean;
  };
}

interface KanbanAction {
  type: string;
  payload?: any;
}

interface KanbanContextType {
  state: KanbanState;
  dispatch: React.Dispatch<KanbanAction>;
  // Actions
  getColumns: () => Promise<void>;
  getColumnsOrder: () => Promise<void>;
  getComments: () => Promise<void>;
  getProfiles: () => Promise<void>;
  getItems: () => Promise<void>;
  getUserStory: () => Promise<void>;
  getUserStoryOrder: () => Promise<void>;
  addColumn: (column: KanbanColumn, columns: KanbanColumn[], columnsOrder: string[]) => Promise<void>;
  editColumn: (column: KanbanColumn, columns: KanbanColumn[]) => Promise<void>;
  updateColumnOrder: (columnsOrder: string[]) => Promise<void>;
  deleteColumn: (columnId: string, columnsOrder: string[], columns: KanbanColumn[]) => Promise<void>;
  addItem: (
    columnId: string,
    columns: KanbanColumn[],
    item: KanbanItem,
    items: KanbanItem[],
    storyId: string,
    userStory: KanbanUserStory[]    
  ) => Promise<void>;
  editItem: (
    columnId: string,
    columns: KanbanColumn[],
    item: KanbanItem,
    items: KanbanItem[],
    storyId: string,
    userStory: KanbanUserStory[]
  ) => Promise<void>;
  updateColumnItemOrder: (columns: KanbanColumn[]) => Promise<void>;
  selectItem: (selectedItem: string | false) => Promise<void>;
  addItemComment: (
    itemId: string | false,
    comment: KanbanComment,
    items: KanbanItem[],
    comments: KanbanComment[]
  ) => Promise<void>;
  deleteItem: (
    itemId: string | false,
    items: KanbanItem[],
    columns: KanbanColumn[],
    userStory: KanbanUserStory[]
  ) => Promise<void>;
  addStory: (story: any, userStory: KanbanUserStory[], userStoryOrder: string[]) => Promise<void>;
  editStory: (story: KanbanUserStory, userStory: KanbanUserStory[]) => Promise<void>;
  updateStoryOrder: (userStoryOrder: string[]) => Promise<void>;
  updateStoryItemOrder: (userStory: KanbanUserStory[]) => Promise<void>;
  addStoryComment: (
    storyId: string,
    comment: KanbanComment,
    comments: KanbanComment[],
    userStory: KanbanUserStory[]
  ) => Promise<void>;
  deleteStory: (storyId: string, userStory: KanbanUserStory[], userStoryOrder: string[]) => Promise<void>;
}

// ==============| PRESERVED DUMMY DATA |=============//

// All hard-coded data preserved exactly as in Redux slice
const INITIAL_KANBAN_STATE: KanbanState = {
  error: null,
  columns: [], // Preserved: Will be populated from API
  columnsOrder: [], // Preserved: Order configuration
  comments: [], // Preserved: Comment threads
  items: [], // Preserved: Kanban items
  profiles: [], // Preserved: User profiles
  selectedItem: false, // Preserved: Selected item state
  userStory: [], // Preserved: User stories
  userStoryOrder: [], // Preserved: Story order
  loading: {
    columns: false,
    comments: false,
    profiles: false,
    items: false,
    userStory: false,
    actions: false,
  },
};

// ==============|| KANBAN REDUCER ||=============//

const kanbanReducer = (state: KanbanState, action: KanbanAction): KanbanState => {
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
    case 'GET_COLUMNS_SUCCESS':
      return {
        ...state,
        columns: action.payload,
        loading: { ...state.loading, columns: false },
      };
    case 'GET_COLUMNS_ORDER_SUCCESS':
      return {
        ...state,
        columnsOrder: action.payload,
        loading: { ...state.loading, columns: false },
      };
    case 'GET_COMMENTS_SUCCESS':
      return {
        ...state,
        comments: action.payload,
        loading: { ...state.loading, comments: false },
      };
    case 'GET_PROFILES_SUCCESS':
      return {
        ...state,
        profiles: action.payload,
        loading: { ...state.loading, profiles: false },
      };
    case 'GET_ITEMS_SUCCESS':
      return {
        ...state,
        items: action.payload,
        loading: { ...state.loading, items: false },
      };
    case 'GET_USER_STORY_SUCCESS':
      return {
        ...state,
        userStory: action.payload,
        loading: { ...state.loading, userStory: false },
      };
    case 'GET_USER_STORY_ORDER_SUCCESS':
      return {
        ...state,
        userStoryOrder: action.payload,
        loading: { ...state.loading, userStory: false },
      };
    case 'ADD_COLUMN_SUCCESS':
      return {
        ...state,
        columns: action.payload.columns,
        columnsOrder: action.payload.columnsOrder,
        loading: { ...state.loading, actions: false },
      };
    case 'EDIT_COLUMN_SUCCESS':
      return {
        ...state,
        columns: action.payload.columns,
        loading: { ...state.loading, actions: false },
      };
    case 'UPDATE_COLUMN_ORDER_SUCCESS':
      return {
        ...state,
        columnsOrder: action.payload.columnsOrder,
        loading: { ...state.loading, actions: false },
      };
    case 'DELETE_COLUMN_SUCCESS':
      return {
        ...state,
        columns: action.payload.columns,
        columnsOrder: action.payload.columnsOrder,
        loading: { ...state.loading, actions: false },
      };
    case 'ADD_ITEM_SUCCESS':
      return {
        ...state,
        items: action.payload.items,
        columns: action.payload.columns,
        userStory: action.payload.userStory,
        loading: { ...state.loading, actions: false },
      };
    case 'EDIT_ITEM_SUCCESS':
      return {
        ...state,
        items: action.payload.items,
        columns: action.payload.columns,
        userStory: action.payload.userStory,
        loading: { ...state.loading, actions: false },
      };
    case 'UPDATE_COLUMN_ITEM_ORDER_SUCCESS':
      return {
        ...state,
        columns: action.payload.columns,
        loading: { ...state.loading, actions: false },
      };
    case 'SELECT_ITEM_SUCCESS':
      return {
        ...state,
        selectedItem: action.payload.selectedItem,
        loading: { ...state.loading, actions: false },
      };
    case 'ADD_ITEM_COMMENT_SUCCESS':
      return {
        ...state,
        items: action.payload.items,
        comments: action.payload.comments,
        loading: { ...state.loading, actions: false },
      };
    case 'DELETE_ITEM_SUCCESS':
      return {
        ...state,
        items: action.payload.items,
        columns: action.payload.columns,
        userStory: action.payload.userStory,
        loading: { ...state.loading, actions: false },
      };
    case 'ADD_STORY_SUCCESS':
      return {
        ...state,
        userStory: action.payload.userStory,
        userStoryOrder: action.payload.userStoryOrder,
        loading: { ...state.loading, actions: false },
      };
    case 'EDIT_STORY_SUCCESS':
      return {
        ...state,
        userStory: action.payload.userStory,
        loading: { ...state.loading, actions: false },
      };
    case 'UPDATE_STORY_ORDER_SUCCESS':
      return {
        ...state,
        userStoryOrder: action.payload.userStoryOrder,
        loading: { ...state.loading, actions: false },
      };
    case 'UPDATE_STORY_ITEM_ORDER_SUCCESS':
      return {
        ...state,
        userStory: action.payload.userStory,
        loading: { ...state.loading, actions: false },
      };
    case 'ADD_STORY_COMMENT_SUCCESS':
      return {
        ...state,
        userStory: action.payload.userStory,
        comments: action.payload.comments,
        loading: { ...state.loading, actions: false },
      };
    case 'DELETE_STORY_SUCCESS':
      return {
        ...state,
        userStory: action.payload.userStory,
        userStoryOrder: action.payload.userStoryOrder,
        loading: { ...state.loading, actions: false },
      };
    default:
      return state;
  }
};

// ==============|| CONTEXT DEFINITION ||=============//

const KanbanContext = createContext<KanbanContextType | null>(null);

type KanbanProviderProps = {
  children: ReactNode;
};

export const KanbanProvider = ({ children }: KanbanProviderProps) => {
  const [state, dispatch] = useReducer(kanbanReducer, INITIAL_KANBAN_STATE);

  // ==============|| ENHANCED API FUNCTIONS WITH LOADING STATES |=============//

  // Enterprise Pattern: Enhanced error handling with loading states
  const getColumns = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'columns', value: true } });
    try {
      const response = await axios.get('/api/kanban/columns');
      dispatch({ type: 'GET_COLUMNS_SUCCESS', payload: response.data.columns });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'columns', value: false } });
      throw error;
    }
  };

  const getColumnsOrder = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'columns', value: true } });
    try {
      const response = await axios.get('/api/kanban/columns-order');
      dispatch({ type: 'GET_COLUMNS_ORDER_SUCCESS', payload: response.data.columnsOrder });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'columns', value: false } });
      throw error;
    }
  };

  const getComments = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'comments', value: true } });
    try {
      const response = await axios.get('/api/kanban/comments');
      dispatch({ type: 'GET_COMMENTS_SUCCESS', payload: response.data.comments });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'comments', value: false } });
      throw error;
    }
  };

  const getProfiles = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'profiles', value: true } });
    try {
      const response = await axios.get('/api/kanban/profiles');
      dispatch({ type: 'GET_PROFILES_SUCCESS', payload: response.data.profiles });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'profiles', value: false } });
      throw error;
    }
  };

  const getItems = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'items', value: true } });
    try {
      const response = await axios.get('/api/kanban/items');
      dispatch({ type: 'GET_ITEMS_SUCCESS', payload: response.data.items });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'items', value: false } });
      throw error;
    }
  };

  const getUserStory = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'userStory', value: true } });
    try {
      const response = await axios.get('/api/kanban/userstory');
      dispatch({ type: 'GET_USER_STORY_SUCCESS', payload: response.data.userStory });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'userStory', value: false } });
      throw error;
    }
  };

  const getUserStoryOrder = async () => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'userStory', value: true } });
    try {
      const response = await axios.get('/api/kanban/userstory-order');
      dispatch({ type: 'GET_USER_STORY_ORDER_SUCCESS', payload: response.data.userStoryOrder });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'userStory', value: false } });
      throw error;
    }
  };

  const addColumn = async (
    column: KanbanColumn,
    columns: KanbanColumn[],
    columnsOrder: string[]
  ) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/kanban/add-column', {
        column,
        columns,
        columnsOrder,
      });
      dispatch({ type: 'ADD_COLUMN_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const editColumn = async (column: KanbanColumn, columns: KanbanColumn[]) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/kanban/edit-column', { column, columns });
      dispatch({ type: 'EDIT_COLUMN_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const updateColumnOrder = async (columnsOrder: string[]) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/kanban/update-column-order', { columnsOrder });
      dispatch({ type: 'UPDATE_COLUMN_ORDER_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const deleteColumn = async (
    columnId: string,
    columnsOrder: string[],
    columns: KanbanColumn[]
  ) => {
    dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: true } });
    try {
      const response = await axios.post('/api/kanban/delete-column', {
        columnId,
        columnsOrder,
        columns,
      });
      dispatch({ type: 'DELETE_COLUMN_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
      dispatch({ type: 'SET_LOADING', payload: { key: 'actions', value: false } });
      throw error;
    }
  };

  const addItem = async (
    columnId: string,
    columns: KanbanColumn[],
    item: KanbanItem,
    items: KanbanItem[],
    storyId: string,
    userStory: KanbanUserStory[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/add-item', {
        columnId,
        columns,
        item,
        items,
        storyId,
        userStory,
      });
      dispatch({ type: 'ADD_ITEM_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const editItem = async (
    columnId: string,
    columns: KanbanColumn[],
    item: KanbanItem,
    items: KanbanItem[],
    storyId: string,
    userStory: KanbanUserStory[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/edit-item', {
        items,
        item,
        userStory,
        storyId,
        columns,
        columnId,
      });
      dispatch({ type: 'EDIT_ITEM_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const updateColumnItemOrder = async (columns: KanbanColumn[]) => {
    try {
      const response = await axios.post('/api/kanban/update-item-order', { columns });
      dispatch({ type: 'UPDATE_COLUMN_ITEM_ORDER_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const selectItem = async (selectedItem: string | false) => {
    try {
      const response = await axios.post('/api/kanban/select-item', { selectedItem });
      dispatch({ type: 'SELECT_ITEM_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const addItemComment = async (
    itemId: string | false,
    comment: KanbanComment,
    items: KanbanItem[],
    comments: KanbanComment[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/add-item-comment', {
        items,
        itemId,
        comment,
        comments,
      });
      dispatch({ type: 'ADD_ITEM_COMMENT_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const deleteItem = async (
    itemId: string | false,
    items: KanbanItem[],
    columns: KanbanColumn[],
    userStory: KanbanUserStory[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/delete-item', {
        columns,
        itemId,
        userStory,
        items,
      });
      dispatch({ type: 'DELETE_ITEM_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const addStory = async (
    story: any,
    userStory: KanbanUserStory[],
    userStoryOrder: string[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/add-story', {
        userStory,
        story,
        userStoryOrder,
      });
      dispatch({ type: 'ADD_STORY_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const editStory = async (story: KanbanUserStory, userStory: KanbanUserStory[]) => {
    try {
      const response = await axios.post('/api/kanban/edit-story', { userStory, story });
      dispatch({ type: 'EDIT_STORY_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const updateStoryOrder = async (userStoryOrder: string[]) => {
    try {
      const response = await axios.post('/api/kanban/update-story-order', { userStoryOrder });
      dispatch({ type: 'UPDATE_STORY_ORDER_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const updateStoryItemOrder = async (userStory: KanbanUserStory[]) => {
    try {
      const response = await axios.post('/api/kanban/update-storyitem-order', { userStory });
      dispatch({ type: 'UPDATE_STORY_ITEM_ORDER_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const addStoryComment = async (
    storyId: string,
    comment: KanbanComment,
    comments: KanbanComment[],
    userStory: KanbanUserStory[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/add-story-comment', {
        userStory,
        storyId,
        comment,
        comments,
      });
      dispatch({ type: 'ADD_STORY_COMMENT_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const deleteStory = async (
    storyId: string,
    userStory: KanbanUserStory[],
    userStoryOrder: string[]
  ) => {
    try {
      const response = await axios.post('/api/kanban/delete-story', {
        userStory,
        storyId,
        userStoryOrder,
      });
      dispatch({ type: 'DELETE_STORY_SUCCESS', payload: response.data });
    } catch (error) {
      dispatch({ type: 'HAS_ERROR', payload: error });
    }
  };

  const contextValue: KanbanContextType = {
    state,
    dispatch,
    getColumns,
    getColumnsOrder,
    getComments,
    getProfiles,
    getItems,
    getUserStory,
    getUserStoryOrder,
    addColumn,
    editColumn,
    updateColumnOrder,
    deleteColumn,
    addItem,
    editItem,
    updateColumnItemOrder,
    selectItem,
    addItemComment,
    deleteItem,
    addStory,
    editStory,
    updateStoryOrder,
    updateStoryItemOrder,
    addStoryComment,
    deleteStory,
  };

  return <KanbanContext.Provider value={contextValue}>{children}</KanbanContext.Provider>;
};

// ==============|| CUSTOM HOOK ||=============//

export const useKanban = () => {
  const context = useContext(KanbanContext);
  if (!context) {
    throw new Error('useKanban must be used within a KanbanProvider');
  }
  return context;
};
