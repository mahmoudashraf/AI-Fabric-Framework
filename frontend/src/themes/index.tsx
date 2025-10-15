import { CssBaseline, StyledEngineProvider } from '@mui/material';
import { createTheme, ThemeOptions, ThemeProvider, Theme } from '@mui/material/styles';
import { useMemo, ReactNode } from 'react';

// material-ui

// project import
import { ColorProps } from 'types';
import { CustomShadowProps } from 'types/default-theme';

import useConfig from '../hooks/useConfig';

// assets
import theme1 from '../scss/_theme1.module.scss';
import theme2 from '../scss/_theme2.module.scss';
import theme3 from '../scss/_theme3.module.scss';
import theme4 from '../scss/_theme4.module.scss';
import theme5 from '../scss/_theme5.module.scss';
import theme6 from '../scss/_theme6.module.scss';
import colors from '../scss/_themes-vars.module.scss';

import componentStyleOverrides from './compStyleOverride';
import { NextAppDirEmotionCacheProvider } from './emotionCache';
import Palette from './palette';
import customShadows from './shadows';
import Typography from './typography';

// types

interface Props {
  children: ReactNode;
}

export default function ThemeCustomization({ children }: Props) {
  const config = useConfig();
  const { borderRadius, fontFamily, navType, outlinedFilled, presetColor, rtlLayout } = useConfig();

  const theme: Theme = useMemo<Theme>(() => Palette(navType, presetColor), [navType, presetColor]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const themeTypography = useMemo(
    () => Typography(theme, borderRadius, fontFamily),
    [theme, borderRadius, fontFamily]
  );
  const themeCustomShadows: CustomShadowProps = useMemo<CustomShadowProps>(
    () => customShadows(navType, theme),
    [navType, theme]
  );

  let color: ColorProps;
  switch (config.presetColor) {
    case 'theme1':
      color = theme1;
      break;
    case 'theme2':
      color = theme2;
      break;
    case 'theme3':
      color = theme3;
      break;
    case 'theme4':
      color = theme4;
      break;
    case 'theme5':
      color = theme5;
      break;
    case 'theme6':
      color = theme6;
      break;
    case 'default':
    default:
      color = colors;
  }

  const themeOption = {
    colors: color,
    heading: color.grey900 || '#212121',
    paper: color.paper || '#ffffff',
    backgroundDefault: color.paper || '#ffffff',
    background: color.primaryLight || '#90caf9',
    darkTextPrimary: color.grey700 || '#616161',
    darkTextSecondary: color.grey500 || '#9e9e9e',
    textDark: color.grey900 || '#212121',
    menuSelected: color.secondaryDark || '#880e4f',
    menuSelectedBack: color.secondaryLight || '#f48fb1',
    divider: color.grey200 || '#eeeeee',
    customization: config,
  } as any;

  switch (config.navType) {
    case 'dark':
      themeOption.paper = color.darkLevel2 || '#1e1e1e';
      themeOption.backgroundDefault = color.darkPaper || '#121212';
      themeOption.background = color.darkBackground || '#1e1e1e';
      themeOption.darkTextPrimary = color.darkTextPrimary || '#ffffff';
      themeOption.darkTextSecondary = color.darkTextSecondary || '#9e9e9e';
      themeOption.textDark = color.darkTextPrimary || '#ffffff';
      themeOption.menuSelected = color.darkSecondaryMain || '#d81b60';
      themeOption.menuSelectedBack = (color.darkSecondaryMain || '#d81b60') + '15';
      themeOption.divider = color.darkTextPrimary || '#424242';
      themeOption.heading = color.darkTextTitle || '#ffffff';
      break;
    case 'light':
    default:
      themeOption.paper = color.paper || '#ffffff';
      themeOption.backgroundDefault = color.paper || '#ffffff';
      themeOption.background = color.primaryLight || '#90caf9';
      themeOption.darkTextPrimary = color.grey700 || '#616161';
      themeOption.darkTextSecondary = color.grey500 || '#9e9e9e';
      themeOption.textDark = color.grey900 || '#212121';
      themeOption.menuSelected = color.secondaryDark || '#880e4f';
      themeOption.menuSelectedBack = color.secondaryLight || '#f48fb1';
      themeOption.divider = color.grey200 || '#eeeeee';
      themeOption.heading = color.grey900 || '#212121';
      break;
  }

  const themeOptions: ThemeOptions = useMemo(
    () => ({
      direction: rtlLayout ? 'rtl' : 'ltr',
      palette: theme.palette,
      mixins: {
        toolbar: {
          minHeight: '48px',
          padding: '16px',
          '@media (min-width: 600px)': {
            minHeight: '48px',
          },
        },
      },
      typography: themeTypography as any,
      customShadows: themeCustomShadows,
    }),
    [rtlLayout, theme, themeCustomShadows, themeTypography]
  );

  const themes: Theme = createTheme(themeOptions);
  themes.components = useMemo(
    () => componentStyleOverrides(themes, borderRadius, outlinedFilled),
    [themes, borderRadius, outlinedFilled]
  );

  return (
    <StyledEngineProvider injectFirst>
      <NextAppDirEmotionCacheProvider options={{ key: 'mui' }}>
        <ThemeProvider theme={themes}>
          <CssBaseline />
          {children}
        </ThemeProvider>
      </NextAppDirEmotionCacheProvider>
    </StyledEngineProvider>
  );
}
