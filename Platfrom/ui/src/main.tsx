import React from 'react'
import ReactDOM from 'react-dom/client'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { PlatformAuthProvider } from './auth/PlatformAuthProvider'
import App from './App'
import { platformTheme } from './theme/platformTheme'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={platformTheme}>
        <CssBaseline />
        <PlatformAuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </PlatformAuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
)
