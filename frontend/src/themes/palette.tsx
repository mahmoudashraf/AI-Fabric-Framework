// material-ui
import { PaletteMode } from '@mui/material';
import { createTheme } from '@mui/material/styles';

// assets
import { ColorProps } from 'types';

import theme1 from '../scss/_theme1.module.scss';
import theme2 from '../scss/_theme2.module.scss';
import theme3 from '../scss/_theme3.module.scss';
import theme4 from '../scss/_theme4.module.scss';
import theme5 from '../scss/_theme5.module.scss';
import theme6 from '../scss/_theme6.module.scss';
import defaultColor from '../scss/_themes-vars.module.scss';

// types

// ==============================|| DEFAULT THEME - PALETTE ||============================== //

const Palette = (navType: PaletteMode, presetColor: string) => {
  let colors: ColorProps;
  switch (presetColor) {
    case 'theme1':
      colors = theme1;
      break;
    case 'theme2':
      colors = theme2;
      break;
    case 'theme3':
      colors = theme3;
      break;
    case 'theme4':
      colors = theme4;
      break;
    case 'theme5':
      colors = theme5;
      break;
    case 'theme6':
      colors = theme6;
      break;
    case 'default':
    default:
      colors = defaultColor;
  }

  return createTheme({
    palette: {
      mode: navType,
      common: {
        black: colors.darkPaper || '#000000',
      },
      primary: {
        light: navType === 'dark' ? (colors.darkPrimaryLight || '#90caf9') : (colors.primaryLight || '#90caf9'),
        main: navType === 'dark' ? (colors.darkPrimaryMain || '#1976d2') : (colors.primaryMain || '#1976d2'),
        dark: navType === 'dark' ? (colors.darkPrimaryDark || '#115293') : (colors.primaryDark || '#115293'),
        200: navType === 'dark' ? (colors.darkPrimary200 || '#eeeeee') : (colors.primary200 || '#eeeeee'),
        800: navType === 'dark' ? (colors.darkPrimary800 || '#424242') : (colors.primary800 || '#424242'),
      },
      secondary: {
        light: navType === 'dark' ? (colors.darkSecondaryLight || '#f48fb1') : (colors.secondaryLight || '#f48fb1'),
        main: navType === 'dark' ? (colors.darkSecondaryMain || '#d81b60') : (colors.secondaryMain || '#d81b60'),
        dark: navType === 'dark' ? (colors.darkSecondaryDark || '#880e4f') : (colors.secondaryDark || '#880e4f'),
        200: navType === 'dark' ? (colors.darkSecondary200 || '#eeeeee') : (colors.secondary200 || '#eeeeee'),
        800: navType === 'dark' ? (colors.darkSecondary800 || '#424242') : (colors.secondary800 || '#424242'),
      },
      error: {
        light: colors.errorLight || '#ef9a9a',
        main: colors.errorMain || '#f44336',
        dark: colors.errorDark || '#c62828',
      },
      orange: {
        light: colors.orangeLight || '#ffcc80',
        main: colors.orangeMain || '#ff9800',
        dark: colors.orangeDark || '#ef6c00',
      },
      warning: {
        light: colors.warningLight || '#ffe082',
        main: colors.warningMain || '#ffca28',
        dark: colors.warningDark || '#ffa000',
      },
      success: {
        light: colors.successLight || '#a5d6a7',
        200: colors.success200 || '#c8e6c9',
        main: colors.successMain || '#4caf50',
        dark: colors.successDark || '#2e7d32',
      },
      grey: {
        50: colors.grey50 || '#fafafa',
        100: colors.grey100 || '#f5f5f5',
        500: navType === 'dark' ? (colors.darkTextSecondary || '#9e9e9e') : (colors.grey500 || '#9e9e9e'),
        600: navType === 'dark' ? (colors.darkTextTitle || '#757575') : (colors.grey600 || '#757575'),
        700: navType === 'dark' ? (colors.darkTextPrimary || '#616161') : (colors.grey700 || '#616161'),
        900: navType === 'dark' ? (colors.darkTextPrimary || '#212121') : (colors.grey900 || '#212121'),
      },
      dark: {
        light: colors.darkTextPrimary || '#ffffff',
        main: colors.darkLevel1 || '#121212',
        dark: colors.darkLevel2 || '#0d0d0d',
        800: colors.darkBackground || '#1e1e1e',
        900: colors.darkPaper || '#121212',
      },
      text: {
        primary: navType === 'dark' ? (colors.darkTextPrimary || '#ffffff') : (colors.grey700 || '#616161'),
        secondary: navType === 'dark' ? (colors.darkTextSecondary || '#9e9e9e') : (colors.grey500 || '#9e9e9e'),
        dark: navType === 'dark' ? (colors.darkTextPrimary || '#ffffff') : (colors.grey900 || '#212121'),
        hint: colors.grey100 || '#f5f5f5',
      },
      divider: navType === 'dark' ? (colors.darkTextPrimary || '#424242') : (colors.grey200 || '#eeeeee'),
      background: {
        paper: navType === 'dark' ? (colors.darkLevel2 || '#1e1e1e') : (colors.paper || '#ffffff'),
        default: navType === 'dark' ? (colors.darkPaper || '#121212') : (colors.paper || '#ffffff'),
      },
    },
  });
};

export default Palette;
