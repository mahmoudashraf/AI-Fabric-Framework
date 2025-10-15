'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

// Types
export interface MenuState {
  selectedItem: string[];
  selectedID: string | null;
  drawerOpen: boolean;
  error: string | null;
  menu: Record<string, unknown>;
}

export type MenuAction =
  | { type: 'SET_ACTIVE_ITEM'; payload: string[] }
  | { type: 'SET_ACTIVE_ID'; payload: string | null }
  | { type: 'SET_DRAWER_OPEN'; payload: boolean }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_MENU_DATA'; payload: Record<string, unknown> }
  | { type: 'TOGGLE_DRAWER' }
  | { type: 'CLOSE_DRAWER' }
  | { type: 'OPEN_DRAWER' };

// Context type
interface MenuContextType {
  state: MenuState;
  setActiveItem: (item: string[]) => void;
  setActiveID: (id: string | null) => void;
  setDrawerOpen: (open: boolean) => void;
  toggleDrawer: () => void;
  closeDrawer: () => void;
  openDrawer: () => void;
  setError: (error: string | null) => void;
  setMenuData: (menu: Record<string, unknown>) => void;
}

// Context
export const MenuContext = createContext<MenuContextType | null>(null);

// Initial state
const initialState: MenuState = {
  selectedItem: ['dashboard'],
  selectedID: null,
  drawerOpen: false,
  error: null,
  menu: {},
};

// Reducer
function menuReducer(state: MenuState, action: MenuAction): MenuState {
  switch (action.type) {
    case 'SET_ACTIVE_ITEM':
      return { ...state, selectedItem: action.payload };

    case 'SET_ACTIVE_ID':
      return { ...state, selectedID: action.payload };

    case 'SET_DRAWER_OPEN':
      return { ...state, drawerOpen: action.payload };

    case 'SET_ERROR':
      return { ...state, error: action.payload };

    case 'SET_MENU_DATA':
      return { ...state, menu: action.payload };

    case 'TOGGLE_DRAWER':
      return { ...state, drawerOpen: !state.drawerOpen };

    case 'CLOSE_DRAWER':
      return { ...state, drawerOpen: false };

    case 'OPEN_DRAWER':
      return { ...state, drawerOpen: true };

    default:
      return state;
  }
}

// Provider component
export const MenuProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(menuReducer, initialState);

  const value: MenuContextType = {
    state,
    setActiveItem: (item: string[]) => {
      dispatch({ type: 'SET_ACTIVE_ITEM', payload: item });
    },
    setActiveID: (id: string | null) => {
      dispatch({ type: 'SET_ACTIVE_ID', payload: id });
    },
    setDrawerOpen: (open: boolean) => {
      dispatch({ type: 'SET_DRAWER_OPEN', payload: open });
    },
    toggleDrawer: () => {
      dispatch({ type: 'TOGGLE_DRAWER' });
    },
    closeDrawer: () => {
      dispatch({ type: 'CLOSE_DRAWER' });
    },
    openDrawer: () => {
      dispatch({ type: 'OPEN_DRAWER' });
    },
    setError: (error: string | null) => {
      dispatch({ type: 'SET_ERROR', payload: error });
    },
    setMenuData: (menu: Record<string, unknown>) => {
      dispatch({ type: 'SET_MENU_DATA', payload: menu });
    },
  };

  return (
    <MenuContext.Provider value={value}>
      {children}
    </MenuContext.Provider>
  );
};

// Hook for using menu context
export const useMenu = (): MenuContextType => {
  const context = useContext(MenuContext);
  if (!context) {
    throw new Error('useMenu must be used within a MenuProvider');
  }
  return context;
};

// Convenience hooks
export const useMenuState = () => {
  const { state } = useMenu();
  return state;
};

export const useMenuActions = () => {
  const { 
    setActiveItem, 
    setActiveID, 
    setDrawerOpen, 
    toggleDrawer, 
    closeDrawer, 
    openDrawer,
    setError,
    setMenuData 
  } = useMenu();
  
  return {
    setActiveItem,
    setActiveID, 
    setDrawerOpen,
    toggleDrawer,
    closeDrawer,
    openDrawer,
    setError,
    setMenuData,
  };
};

export default MenuProvider;
