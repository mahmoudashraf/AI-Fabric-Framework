import { Alert, Box, CircularProgress, Stack, Typography } from '@mui/material'
import { usePlatformAuth } from './auth/PlatformAuthProvider'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { ActionsPage } from './pages/ActionsPage'
import { DeploymentsPage } from './pages/DeploymentsPage'
import { DiagnosticsPage } from './pages/DiagnosticsPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { ProvidersPage } from './pages/ProvidersPage'
import { RevisionsPage } from './pages/RevisionsPage'
import { SecurityPage } from './pages/SecurityPage'
import { PlatformLoginPage } from './pages/PlatformLoginPage'
import { VerificationPage } from './pages/VerificationPage'

export default function App() {
  const auth = usePlatformAuth()

  if (auth.isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <Stack spacing={2} alignItems="center">
          <CircularProgress />
          <Typography color="text.secondary">Checking platform access…</Typography>
        </Stack>
      </Box>
    )
  }

  if (auth.error) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 3 }}>
        <Alert severity="error" sx={{ maxWidth: 720 }}>
          {auth.error.message}
        </Alert>
      </Box>
    )
  }

  if (auth.session?.enabled && !auth.session.authenticated) {
    return (
      <PlatformLoginPage
        headerName={auth.session.headerName}
        sessionAuthEnabled={auth.session.sessionAuthEnabled}
        apiKeyAuthEnabled={auth.session.apiKeyAuthEnabled}
        errorMessage={auth.apiKey ? 'Platform API key rejected. Please try again.' : null}
        onPasswordSubmit={auth.signInWithPassword}
        onApiKeySubmit={auth.setApiKey}
      />
    )
  }

  return (
    <AppShell session={auth.session} onSignOut={auth.signOut}>
      <Routes>
        <Route path="/" element={<Navigate to="/deployments" replace />} />
        <Route path="/deployments" element={<DeploymentsPage />} />
        <Route path="/actions" element={<ActionsPage />} />
        <Route path="/knowledge" element={<KnowledgePage />} />
        <Route path="/providers" element={<ProvidersPage />} />
        <Route path="/security" element={<SecurityPage />} />
        <Route path="/verification" element={<VerificationPage />} />
        <Route path="/revisions" element={<RevisionsPage />} />
        <Route path="/diagnostics" element={<DiagnosticsPage />} />
      </Routes>
    </AppShell>
  )
}
