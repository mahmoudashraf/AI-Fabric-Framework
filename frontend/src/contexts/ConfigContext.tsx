'use client';
import { PaletteMode } from '@mui/material';
import { createContext, ReactNode } from 'react';

// project import
import defaultConfig from 'config';
import useLocalStorage from 'hooks/useLocalStorage';

// types
import { CustomizationProps } from 'types/config';

// initial state
const initialState: CustomizationProps = {
  ...defaultConfig,
  onChangeLayout: () => {},
  onChangeDrawer: () => {},
  onChangeMenuType: () => {},
  onChangePresetColor: () => {},
  onChangeLocale: () => {},
  onChangeRTL: () => {},
  onChangeContainer: () => {},
  onChangeFontFamily: () => {},
  onChangeBorderRadius: () => {},
  onChangeOutlinedField: () => {},
  onReset: () => {},
};

// ==============================|| CONFIG CONTEXT & PROVIDER ||============================== //

const ConfigContext = createContext(initialState);

type ConfigProviderProps = {
  children: ReactNode;
};

function ConfigProvider({ children }: ConfigProviderProps) {
  const [config, setConfig] = useLocalStorage('kiwi-config-ts', {
    layout: initialState.layout,
    drawerType: initialState.drawerType,
    fontFamily: initialState.fontFamily,
    borderRadius: initialState.borderRadius,
    outlinedFilled: initialState.outlinedFilled,
    navType: initialState.navType,
    presetColor: initialState.presetColor,
    locale: initialState.locale,
    rtlLayout: initialState.rtlLayout,
    container: (initialState as any).container ?? false,
  });

  const onChangeLayout = (layout: string) => {
    setConfig({
      ...config,
      layout,
    });
  };

  const onChangeDrawer = (drawerType: string) => {
    setConfig({
      ...config,
      drawerType,
    });
  };

  const onChangeMenuType = (navType: PaletteMode) => {
    setConfig({
      ...config,
      navType,
    });
  };

  const onChangePresetColor = (presetColor: string) => {
    setConfig({
      ...config,
      presetColor,
    });
  };

  const onChangeLocale = (locale: string) => {
    setConfig({
      ...config,
      locale,
    });
  };

  const onChangeRTL = (rtlLayout: boolean) => {
    setConfig({
      ...config,
      rtlLayout,
    });
  };

  const onChangeContainer = (container: boolean) => {
    setConfig({
      ...config,
      container,
    });
  };

  const onChangeFontFamily = (fontFamily: string) => {
    setConfig({
      ...config,
      fontFamily,
    });
  };

  const onChangeBorderRadius = (_event: Event, newValue: number | number[]) => {
    setConfig({
      ...config,
      borderRadius: newValue as number,
    });
  };

  const onChangeOutlinedField = (outlinedFilled: boolean) => {
    setConfig({
      ...config,
      outlinedFilled,
    });
  };

  const onReset = () => {
    setConfig({ ...defaultConfig });
  };

  return (
    <ConfigContext.Provider
      value={{
        ...config,
        onChangeLayout,
        onChangeDrawer,
        onChangeMenuType,
        onChangePresetColor,
        onChangeLocale,
        onChangeRTL,
        onChangeContainer,
        onChangeFontFamily,
        onChangeBorderRadius,
        onChangeOutlinedField,
        onReset,
      }}
    >
      {children}
    </ConfigContext.Provider>
  );
}

export { ConfigProvider, ConfigContext };

// Legacy export for compatibility
export const Context = ConfigContext;
