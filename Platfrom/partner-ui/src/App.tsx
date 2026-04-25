import { Alert, Box, CircularProgress } from '@mui/material'
import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './components/AppShell'
import { usePartnerSession } from './hooks/usePartnerSession'
import { AuthCallbackPage } from './pages/AuthCallbackPage'
import { ClientStoresPage } from './pages/ClientStoresPage'
import { DashboardPage } from './pages/DashboardPage'
import { DocumentationPage } from './pages/DocumentationPage'
import { EscalationDetailPage } from './pages/EscalationDetailPage'
import { EvidenceBundlesPage } from './pages/EvidenceBundlesPage'
import { ImplementationRequestDetailPage } from './pages/ImplementationRequestDetailPage'
import { IntelligenceCatalogPage } from './pages/IntelligenceCatalogPage'
import { LoginPage } from './pages/LoginPage'
import { MerchantApprovalPage } from './pages/MerchantApprovalPage'
import { MembersPage } from './pages/MembersPage'
import { NewImplementationPage } from './pages/NewImplementationPage'
import { ProfilePage } from './pages/ProfilePage'
import { StoreWorkspacePage } from './pages/StoreWorkspacePage'
import { SupportCenterPage } from './pages/SupportCenterPage'
import { TemplatesPage } from './pages/TemplatesPage'
import { VerificationPacksPage } from './pages/VerificationPacksPage'

export function App({ mode, onToggleMode }: { mode: 'light' | 'dark'; onToggleMode: () => void }) {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="/merchant/partner-access/:approvalCode" element={<MerchantApprovalPage />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <WorkspaceRoutes mode={mode} onToggleMode={onToggleMode} />
          </RequireAuth>
        }
      />
    </Routes>
  )
}

function WorkspaceRoutes({ mode, onToggleMode }: { mode: 'light' | 'dark'; onToggleMode: () => void }) {
  const sessionQuery = usePartnerSession()
  if (sessionQuery.isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    )
  }
  if (sessionQuery.isError) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Partner session could not be loaded. Sign out and try again if the problem continues.</Alert>
      </Box>
    )
  }

  const session = sessionQuery.data
  if (!session) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Partner session is unavailable.</Alert>
      </Box>
    )
  }
  return (
    <AppShell session={session} mode={mode} onToggleMode={onToggleMode}>
      <Routes>
        <Route path="/" element={<DashboardPage session={session} />} />
        <Route path="/stores" element={<ClientStoresPage session={session} />} />
        <Route path="/stores/:storeId" element={<StoreWorkspacePage />} />
        <Route path="/implementations/new" element={<NewImplementationPage session={session} />} />
        <Route path="/implementations/:requestId" element={<ImplementationRequestDetailPage />} />
        <Route path="/catalog" element={<IntelligenceCatalogPage session={session} />} />
        <Route path="/templates" element={<TemplatesPage />} />
        <Route path="/verification" element={<VerificationPacksPage />} />
        <Route path="/evidence" element={<EvidenceBundlesPage />} />
        <Route path="/support" element={<SupportCenterPage session={session} />} />
        <Route path="/support/:escalationId" element={<EscalationDetailPage />} />
        <Route path="/docs/*" element={<DocumentationPage />} />
        <Route path="/settings/profile" element={<ProfilePage session={session} />} />
        <Route path="/settings/members" element={<MembersPage session={session} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}
