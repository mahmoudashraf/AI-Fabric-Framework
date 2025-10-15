import React, { useContext } from 'react';

// Context-based hooks for different types of state management

// Menu state hook (for simple drawer/menu state)
export interface MenuState {
  selectedItem: string[];
  selectedID: string | null;
  drawerOpen: boolean;
}

export interface MenuActions {
  setSelectedItem: (items: string[]) => void;
  setSelectedID: (id: string | null) => void;
  toggleDrawer: () => void;
  setDrawerOpen: (open: boolean) => void;
}

// Simple local state hook for menu functionality
export const useMenuState = (initialSelectedItem: string[] = ['dashboard']): MenuState & MenuActions => {
  const [selectedItem, setSelectedItem] = React.useState<string[]>(initialSelectedItem);
  const [selectedID, setSelectedID] = React.useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = React.useState(false);

  const toggleDrawer = React.useCallback(() => {
    setDrawerOpen(prev => !prev);
  }, []);

  return {
    selectedItem,
    selectedID,
    drawerOpen,
    setSelectedItem,
    setSelectedID,
    setDrawerOpen,
    toggleDrawer,
  };
};

// Performance monitoring hook for context providers
export const useProviderPerformance = (providerName: string) => {
  const renderCountRef = React.useRef(0);
  
  React.useEffect(() => {
    renderCountRef.current += 1;
    if (process.env.NODE_ENV === 'development') {
      console.log(`${providerName} Provider rendered ${renderCountRef.current} times`);
    }
  });

  return {
    renderCount: renderCountRef.current,
  };
};

// Context value comparison for optimization
export const usePreviousValue = <T>(value: T): T | undefined => {
  const ref = React.useRef<T>();
  
  React.useEffect(() => {
    ref.current = value;
  });
  
  return ref.current;
};

// Memoized context value creator
export const createMemoizedContextValue = <T>(
  valueFactory: () => T,
  dependencies: React.DependencyList
): T => {
  return React.useMemo(valueFactory, dependencies);
};

// Context consumer hook wrapper
export const useContextConsumer = <T, R>(
  context: React.Context<T | null>,
  transformer: (value: T) => R,
  defaultValue?: R
): R => {
  const contextValue = useContext(context);
  
  if (!contextValue) {
    if (defaultValue !== undefined) {
      return defaultValue;
    }
    throw new Error(`Context must be used within its Provider`);
  }
  
  return transformer(contextValue);
};
