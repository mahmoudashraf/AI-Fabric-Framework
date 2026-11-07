import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { ScopedCssBaseline, ThemeProvider, useMediaQuery } from '@mui/material'
import { createConsoleTheme } from './consoleTheme'

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
  const theme = useMemo(() => createConsoleTheme(mode), [mode])
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
