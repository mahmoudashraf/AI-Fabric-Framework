// Modern Menu Management Hook
// Pure Context API implementation

import { useMenu } from 'contexts/MenuContext';

/**
 * Modern Menu Management Hook
 * Provides comprehensive menu management using Context API
 */
export const useModernMenu = () => {
  const menuContext = useMenu();

  return {
    // Menu state
    menu: menuContext.state.menu,
    selectedItem: menuContext.state.selectedItem || [],
    selectedID: menuContext.state.selectedID || '',
    drawerOpen: menuContext.state.drawerOpen || false,
    
    // Menu operations
    setActiveID: (id: string | null) => menuContext.setActiveID(id),
    openDrawer: () => {
      return menuContext.openDrawer();
    },
    closeDrawer: () => {
      return menuContext.closeDrawer();
    },
    
    // Selection management
    selectMenu: () => {
      // Set the ID based on current pathname if available
      const activePath = typeof window !== 'undefined' ? window.location.pathname : '';
      return menuContext.setActiveID(activePath);
    },
    
    // Menu data
    menuItems: Object.keys(menuContext.state.menu ?? {}).length,
    isMenuReady: !!menuContext.state.menu,
    
    // Enhanced debugging
    menuDebug: () => ({
      menuItems: Object.keys(menuContext.state.menu ?? {}).length,
      selectedID: menuContext.state.selectedID,
      drawerOpen: menuContext.state.drawerOpen,
      selectedItems: menuContext.state.selectedItem.length,
    }),
  };
};

export default useModernMenu;
