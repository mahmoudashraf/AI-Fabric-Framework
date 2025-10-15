// eslint-disable-next-line
import * as createTheme from '@mui/material/styles';
import { customShadows } from 'themes/shadows';

declare module '@mui/material/styles' {
  export interface ThemeOptions {
    customShadows?: customShadows;
    customization?: TypographyOptions | ((palette: Palette) => TypographyOptions);
    darkTextSecondary?: string;
    textDark?: string;
    darkTextPrimary?: string;
    grey500?: string;
  }
  interface Theme {
    customShadows: customShadows;
    customization: Typography;
    darkTextSecondary: string;
    textDark: string;
    grey500: string;
    darkTextPrimary: string;
  }

  // Extend Palette interface to include custom colors
  interface Palette {
    orange: PaletteColor;
    dark: PaletteColor;
  }

  interface PaletteOptions {
    orange?: PaletteColorOptions;
    dark?: PaletteColorOptions;
  }

  // Extend PaletteColor to include numeric keys
  interface PaletteColor {
    [key: number]: string;
  }

  interface PaletteColorOptions {
    [key: number]: string;
    light?: string;
    main?: string;
    dark?: string;
  }

  // Extend TypographyVariants to include custom variants
  interface TypographyVariants {
    customInput: React.CSSProperties;
    commonAvatar: React.CSSProperties;
    smallAvatar: React.CSSProperties;
    mediumAvatar: React.CSSProperties;
    largeAvatar: React.CSSProperties;
    menuCaption: React.CSSProperties;
    subMenuCaption: React.CSSProperties;
    mainContent: React.CSSProperties;
  }

  interface TypographyVariantsOptions {
    customInput?: React.CSSProperties;
    commonAvatar?: React.CSSProperties;
    smallAvatar?: React.CSSProperties;
    mediumAvatar?: React.CSSProperties;
    largeAvatar?: React.CSSProperties;
    menuCaption?: React.CSSProperties;
    subMenuCaption?: React.CSSProperties;
    mainContent?: React.CSSProperties;
  }

  // Extend TypeText to include custom properties
  interface TypeText {
    hint?: string;
    dark?: string;
  }
}
