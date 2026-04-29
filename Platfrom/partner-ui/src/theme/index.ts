import { createTheme } from '@mui/material/styles'

export function createPartnerTheme(mode: 'light' | 'dark') {
  const light = mode === 'light'
  return createTheme({
    palette: {
      mode,
      primary: { main: '#5B5BD6' },
      background: {
        default: light ? '#F7F8FA' : '#0B0F17',
        paper: light ? '#FFFFFF' : '#111827',
      },
      text: {
        primary: light ? '#0F172A' : '#E5E7EB',
        secondary: light ? '#475569' : '#9CA3AF',
        disabled: light ? '#94A3B8' : '#6B7280',
      },
      divider: light ? '#E2E8F0' : '#1F2937',
    },
    typography: {
      fontFamily: `'Inter', -apple-system, system-ui, sans-serif`,
      h1: { fontSize: '1.75rem', fontWeight: 600, letterSpacing: 0 },
      h2: { fontSize: '1.375rem', fontWeight: 600, letterSpacing: 0 },
      h3: { fontSize: '1.125rem', fontWeight: 600, letterSpacing: 0 },
      h4: { fontSize: '1rem', fontWeight: 600, letterSpacing: 0 },
      body1: { fontSize: '0.875rem', letterSpacing: 0 },
      body2: { fontSize: '0.8125rem', letterSpacing: 0 },
      caption: { fontSize: '0.75rem', fontWeight: 500, letterSpacing: 0 },
      button: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
      fontFamilyMonospace: `'JetBrains Mono', ui-monospace, SFMono-Regular, monospace`,
    } as any,
    shape: { borderRadius: 8 },
    components: {
      MuiButton: { defaultProps: { disableElevation: true, size: 'small' } },
      MuiTextField: { defaultProps: { size: 'small', variant: 'outlined' } },
      MuiTable: { defaultProps: { size: 'small', stickyHeader: true } },
      MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: `1px solid ${light ? '#E2E8F0' : '#1F2937'}` } } },
      MuiTooltip: { defaultProps: { arrow: true, enterDelay: 400 } },
      MuiAlert: { defaultProps: { variant: 'outlined' } },
    },
  })
}
