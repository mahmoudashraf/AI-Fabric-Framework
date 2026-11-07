import { createTheme, type PaletteMode } from '@mui/material/styles'

// Keep customer resources and shared configuration editors on the Kit palette.
export function createConsoleTheme(mode: PaletteMode) {
  const dark = mode === 'dark'
  return createTheme({
    palette: {
      mode,
      primary: { main: dark ? '#4ca5ff' : '#116cca', contrastText: dark ? '#07131f' : '#ffffff' },
      secondary: { main: dark ? '#48dfbe' : '#117e6d' },
      background: { default: dark ? '#111619' : '#f7f9fa', paper: dark ? '#191f24' : '#ffffff' },
      text: { primary: dark ? '#f1f3f5' : '#17232f', secondary: dark ? '#afbdcc' : '#566677' },
      divider: dark ? '#36414b' : '#cbd5df',
      success: { main: dark ? '#48dfbe' : '#117e6d' },
      warning: { main: dark ? '#f4c05c' : '#95610b' },
      error: { main: dark ? '#f29b99' : '#b44443' },
    },
    shape: { borderRadius: 6 },
    typography: {
      fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
      fontSize: 13,
      h1: { fontSize: 30, fontWeight: 650, lineHeight: 1.25, letterSpacing: 0 },
      h4: { fontSize: 26, fontWeight: 650, letterSpacing: 0 },
      h5: { fontSize: 21, fontWeight: 600, letterSpacing: 0 },
      h6: { fontSize: 17, fontWeight: 600, letterSpacing: 0 },
      subtitle1: { fontWeight: 600, letterSpacing: 0 },
      overline: { letterSpacing: 0, fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
    },
    components: {
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiCard: { defaultProps: { variant: 'outlined' }, styleOverrides: { root: { backgroundImage: 'none', boxShadow: 'none' } } },
      MuiButton: { defaultProps: { disableElevation: true, size: 'small' }, styleOverrides: {
        root: { minHeight: 34, borderRadius: 6 },
        outlined: { borderColor: dark ? '#36414b' : '#cbd5df' },
      } },
      MuiOutlinedInput: { styleOverrides: { root: { backgroundColor: dark ? '#191f24' : '#ffffff', fontSize: 13 } } },
      MuiInputLabel: { styleOverrides: { root: { fontSize: 13 } } },
      MuiChip: { defaultProps: { size: 'small' }, styleOverrides: { root: { borderRadius: 4, fontWeight: 500, maxWidth: '100%' } } },
      MuiTab: { styleOverrides: { root: { minHeight: 44, textTransform: 'none', fontWeight: 550, letterSpacing: 0 } } },
      MuiTabs: { styleOverrides: { root: { minHeight: 44 } } },
      MuiAccordion: { styleOverrides: { root: { backgroundImage: 'none', boxShadow: 'none', '&:before': { display: 'none' } } } },
      MuiTableCell: { styleOverrides: {
        root: { borderColor: dark ? '#36414b' : '#cbd5df' },
        head: { color: dark ? '#afbdcc' : '#566677', fontSize: 12, fontWeight: 600 },
      } },
      MuiTooltip: { defaultProps: { arrow: true } },
    },
  })
}
