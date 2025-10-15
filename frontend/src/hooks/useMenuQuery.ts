import { useQuery } from '@tanstack/react-query';
import { queryKeys } from 'lib/react-query';
import axios from 'utils/axios';
import { useMenu } from 'contexts/MenuContext';
import { logMigrationActivity } from 'utils/logMigrationActivity';
// Using Context API

/**
 * React Query hook for menu data management
 * Integrates with MenuContext for UI state
 */
export const useMenuQuery = () => {
  const { setMenuData, setError, state: menuState } = useMenu();

  // Fetch menu widget data
  const menuQuery = useQuery({
    queryKey: queryKeys.menu.widget(),
    queryFn: async () => {
      logMigrationActivity('Menu data fetch', 'MARK_MENU');
      const response = await axios.get('/api/menu/widget');
      const widget = response.data.widget;
      // side-effect: sync context on success
      setMenuData(widget);
      logMigrationActivity('Menu data loaded', 'MARK_MENU', { menuItems: Object.keys(widget ?? {}).length });
      return widget;
    },
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  if (menuQuery.error) {
    setError(menuQuery.error instanceof Error ? menuQuery.error.message : 'Failed to load menu');
    logMigrationActivity('Menu data error', 'MARK_MENU', { error: menuQuery.error });
  }

  return {
    // Query state
    data: menuQuery.data,
    isLoading: menuQuery.isLoading,
    error: menuQuery.error,
    
    // Menu state from context
    menuState: {
      selectedItem: menuState.selectedItem,
      selectedID: menuState.selectedID,
      drawerOpen: menuState.drawerOpen,
    },
    
    // Refetch capability
    refetch: menuQuery.refetch,
  };
};

/**
 * Hook for menu actions that combines React Query and Context
 */
export const useMenuActions = () => {
  const menuContext = useMenu();
  
  return {
    // UI state actions
    setActiveItem: menuContext.setActiveItem,
    setActiveID: menuContext.setActiveID,
    toggleDrawer: menuContext.toggleDrawer,
    closeDrawer: menuContext.closeDrawer,
    openDrawer: menuContext.openDrawer,
  };
};

export default useMenuQuery;
