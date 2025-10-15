'use client';

import React, { createContext, useContext, useReducer, useEffect, ReactNode } from 'react';

// Types for theme customization
export interface CustomizationState {
  fontFamily: string;
  borderRadius: number;
  outlinedFilled: boolean;
  navType: 'light' | 'dark';
  presColor: string;
  locale: string;
  rtlLayout: boolean;
  opened: boolean;
}

type CustomizationAction =
  | { type: 'SET_FONT_FAMILY'; payload: string }
  | { type: 'SET_BORDER_RADIUS'; payload: number }
  | { type: 'SET_OUTLINED_FILLED'; payload: boolean }
  | { type: 'SET_NAV_TYPE'; payload: 'light' | 'dark' }
  | { type: 'SET_PRESET_COLOR'; payload: string }
  | { type: 'SET_LOCALE'; payload: string }
  | { type: 'SET_RTL_LAYOUT'; payload: boolean }
  | { type: 'SET_MENU_OPENED'; payload: boolean }
  | { type: 'RESET_THEME'; payload: Partial<CustomizationState> };

// Context type
interface ThemeContextType {
  customization: CustomizationState;
  setFontFamily: (fontFamily: string) => void;
  setBorderRadius: (borderRadius: number) => void;
  setOutlinedFilled: (outlinedFilled: boolean) => void;
  setNavType: (navType: 'light' | 'dark') => void;
  setPresetColor: (presColor: string) => void;
  setLocale: (locale: string) => void;
  setRTL: (rtlLayout: boolean) => void;
  setMenuOpen: (opened: boolean) => void;
  resetTheme: (defaultCustomization: Partial<CustomizationState>) => void;
}

// Context
export const ThemeContext = createContext<ThemeContextType | null>(null);

// Initial customization state
const initialState: CustomizationState = {
  fontFamily: `'Inter', sans-serif`,
  borderRadius: 12,
  outlinedFilled: false,
  navType: 'light',
  presColor: 'default',
  locale: 'en',
  rtlLayout: false,
  opened: true,
};

// Reducer
function themeReducer(state: CustomizationState, action: CustomizationAction): CustomizationState {
  switch (action.type) {
    case 'SET_FONT_FAMILY':
      return { ...state, fontFamily: action.payload };

    case 'SET_BORDER_RADIUS':
      return { ...state, borderRadius: action.payload };

    case 'SET_OUTLINED_FILLED':
      return { ...state, outlinedFilled: action.payload };

    case 'SET_NAV_TYPE':
      return { ...state, navType: action.payload };

    case 'SET_PRESET_COLOR':
      return { ...state, presColor: action.payload };

    case 'SET_LOCALE':
      return { ...state, locale: action.payload };

    case 'SET_RTL_LAYOUT':
      return { ...state, rtlLayout: action.payload };

    case 'SET_MENU_OPENED':
      return { ...state, opened: action.payload };

    case 'RESET_THEME':
      return { ...state, ...action.payload };

    default:
      return state;
  }
}

// Provider component
export const ThemeProvider: React.FC<{ 
  children: ReactNode;
  initialState?: Partial<CustomizationState>;
}> = ({ children, initialState: customInitialState }) => {
  const [customization, dispatch] = useReducer(
    themeReducer, 
    { ...initialState, ...customInitialState }
  );

  // Persist customization to localStorage
  useEffect(() => {
    try {
      const savedCustomization = localStorage.getItem('kiwi-customization');
      if (savedCustomization) {
        const parsed = JSON.parse(savedCustomization);
        // Validate and update state only if keys are valid
        if (typeof parsed === 'object' && parsed !== null) {
          Object.keys(parsed).forEach(key => {
            if (key in initialState) {
              dispatch({ type: `SET_${key.toUpperCase()}` as any, payload: parsed[key] });
            }
          });
        }
      }
    } catch (error) {
      console.warn('Failed to load customization from localStorage:', error);
    }
  }, []);

  // Save to localStorage whenever customization changes
  useEffect(() => {
    try {
      localStorage.setItem('kiwi-customization', JSON.stringify(customization));
    } catch (error) {
      console.warn('Failed to save customization to localStorage:', error);
    }
  }, [customization]);

  const value: ThemeContextType = {
    customization,
    setFontFamily: (fontFamily: string) => {
      dispatch({ type: 'SET_FONT_FAMILY', payload: fontFamily });
    },
    setBorderRadius: (borderRadius: number) => {
      dispatch({ type: 'SET_BORDER_RADIUS', payload: borderRadius });
    },
    setOutlinedFilled: (outlinedFilled: boolean) => {
      dispatch({ type: 'SET_OUTLINED_FILLED', payload: outlinedFilled });
    },
    setNavType: (navType: 'light' | 'dark') => {
      dispatch({ type: 'SET_NAV_TYPE', payload: navType });
    },
    setPresetColor: (presColor: string) => {
      dispatch({ type: 'SET_PRESET_COLOR', payload: presColor });
    },
    setLocale: (locale: string) => {
      dispatch({ type: 'SET_LOCALE', payload: locale });
    },
    setRTL: (rtlLayout: boolean) => {
      dispatch({ type: 'SET_RTL_LAYOUT', payload: rtlLayout });
    },
    setMenuOpen: (opened: boolean) => {
      dispatch({ type: 'SET_MENU_OPENED', payload: opened });
    },
    resetTheme: (defaultCustomization: Partial<CustomizationState>) => {
      dispatch({ type: 'RESET_THEME', payload: defaultCustomization });
    },
  };

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
};

// Hook for using theme customization
export const useCustomization = (): ThemeContextType => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useCustomization must be used within a ThemeProvider');
  }
  return context;
};

// Convenience hooks for specific theme properties
export const useThemeSettings = () => {
  const { customization } = useCustomization();
  return customization;
};

export const useThemeActions = () => {
  const { 
    setFontFamily, 
    setBorderRadius, 
    setOutlinedFilled, 
    setNavType, 
    setPresetColor, 
    setLocale, 
    setRTL, 
    setMenuOpen 
  } = useCustomization();
  
  return {
    setFontFamily,
    setBorderRadius,
    setOutlinedFilled,
    setNavType,
    setPresetColor,
    setLocale,
    setRTL,
    setMenuOpen,
  };
};

export default ThemeProvider;
