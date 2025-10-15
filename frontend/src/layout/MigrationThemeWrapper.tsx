'use client';

import React, { ReactNode } from 'react';

// Existing theme system
import { ConfigProvider } from 'contexts/ConfigContext';

// New enhanced theme system
import ThemeCustomization from 'themes';
import { ThemeProvider as _ThemeProvider } from 'contexts/ThemeContext';
import { MenuProvider } from 'contexts/MenuContext';
import { CartProvider } from 'contexts/CartContext';

// Migration flags
// Using Context API

// ==============================|| MIGRATION THEME WRAPPER ||============================== //

/**
 * Wrapper component that switches between ConfigContext and ThemeContext
 * based on migration feature flags
 */
interface MigrationThemeWrapperProps {
  children: ReactNode;
}

const MigrationThemeWrapper: React.FC<MigrationThemeWrapperProps> = ({ children }) => {
  // Modern Context API - All providers enabled
  return (
    <ConfigProvider>
      <ThemeCustomization>
        <MenuProvider>
          <CartProvider>
            {children}
          </CartProvider>
        </MenuProvider>
      </ThemeCustomization>
    </ConfigProvider>
  );
};

export default MigrationThemeWrapper;
