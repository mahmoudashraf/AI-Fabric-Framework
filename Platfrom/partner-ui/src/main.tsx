import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React, { useMemo, useState } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { SupabaseProvider } from './auth/SupabaseProvider'
import { App } from './App'
import { createPartnerTheme } from './theme'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function Root() {
  const [mode, setMode] = useState<'light' | 'dark'>(() => (localStorage.getItem('partner-ui-mode') === 'light' ? 'light' : 'dark'))
  const theme = useMemo(() => createPartnerTheme(mode), [mode])
  const toggleMode = () => {
    setMode((current) => {
      const next = current === 'light' ? 'dark' : 'light'
      localStorage.setItem('partner-ui-mode', next)
      return next
    })
  }

  return (
    <React.StrictMode>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <SupabaseProvider>
            <BrowserRouter>
              <App mode={mode} onToggleMode={toggleMode} />
            </BrowserRouter>
          </SupabaseProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </React.StrictMode>
  )
}

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(<Root />)
