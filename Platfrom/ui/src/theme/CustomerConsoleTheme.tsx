import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { ScopedCssBaseline, ThemeProvider, useMediaQuery } from '@mui/material'
import { createTheme } from '@mui/material/styles'

export type CustomerColorPreference = 'light' | 'dark' | 'system'
const preferenceKey = 'loomai-customer-console-color-scheme'
const ColorPreference = createContext<{
  preference: CustomerColorPreference
  setPreference: (preference: CustomerColorPreference) => void
}>({ preference: 'dark', setPreference: () => {} })

function readPreference(): CustomerColorPreference {
  try {
    const value = window.localStorage.getItem(preferenceKey)
    if (value === 'light' || value === 'dark' || value === 'system') return value
  } catch { /* A blocked preference store must not block access. */ }
  return 'dark'
}

export function CustomerConsoleTheme({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<CustomerColorPreference>(readPreference)
  const systemDark = useMediaQuery('(prefers-color-scheme: dark)')
  const mode = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
  const theme = useMemo(() => createTheme({
    palette: {
      mode,
      primary: { main: mode === 'dark' ? '#9bafff' : '#4558cf' },
      secondary: { main: mode === 'dark' ? '#89ceb9' : '#16776c' },
      background: { default: mode === 'dark' ? '#17191c' : '#f7f8fa', paper: mode === 'dark' ? '#22252a' : '#ffffff' },
      text: { primary: mode === 'dark' ? '#e3e5e9' : '#24272d', secondary: mode === 'dark' ? '#a1a6af' : '#717783' },
      divider: mode === 'dark' ? '#363a42' : '#e2e5ea',
      success: { main: mode === 'dark' ? '#89ceb9' : '#16776c' },
      warning: { main: mode === 'dark' ? '#e4bd70' : '#92600e' },
      error: { main: mode === 'dark' ? '#ee9b99' : '#b44443' },
    },
    shape: { borderRadius: 6 },
    typography: {
      fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
      fontSize: 13,
      h4: { fontSize: 25, fontWeight: 650, letterSpacing: 0 },
      h5: { fontSize: 21, fontWeight: 600, letterSpacing: 0 },
      h6: { fontSize: 17, fontWeight: 600, letterSpacing: 0 },
      button: { textTransform: 'none', fontWeight: 550, letterSpacing: 0 },
    },
    components: {
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiCard: { defaultProps: { variant: 'outlined' }, styleOverrides: { root: { backgroundImage: 'none', boxShadow: 'none' } } },
      MuiButton: { defaultProps: { disableElevation: true, size: 'small' } },
      MuiTableCell: { styleOverrides: { head: { color: mode === 'dark' ? '#a1a6af' : '#717783', fontSize: 12 } } },
      MuiTooltip: { defaultProps: { arrow: true } },
    },
  }), [mode])
  const value = useMemo(() => ({ preference, setPreference: (next: CustomerColorPreference) => {
    setPreferenceState(next)
    try { window.localStorage.setItem(preferenceKey, next) } catch { /* Device preference is optional. */ }
  } }), [preference])
  return (
    <ColorPreference.Provider value={value}>
      <ThemeProvider theme={theme}>
        <ScopedCssBaseline data-customer-theme={mode} sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
          {children}
        </ScopedCssBaseline>
      </ThemeProvider>
    </ColorPreference.Provider>
  )
}

export function useCustomerColorPreference() { return useContext(ColorPreference) }
