import React from 'react'
import ReactDOM from 'react-dom/client'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PlatformAuthProvider } from './auth/PlatformAuthProvider'
import { PlatformRouting } from './layout/PlatformRouting'
import { platformTheme } from './theme/platformTheme'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={platformTheme}>
        <CssBaseline />
        <PlatformAuthProvider>
          <PlatformRouting />
        </PlatformAuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
)
