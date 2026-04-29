import { Alert, Box, CircularProgress, Stack, Typography } from '@mui/material'
import { usePlatformAuth } from './auth/PlatformAuthProvider'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { ActionsPage } from './pages/ActionsPage'
import { ActivityPage } from './pages/ActivityPage'
import { AccessPage } from './pages/AccessPage'
import { ApprovalsPage } from './pages/ApprovalsPage'
import { CustomersPage } from './pages/CustomersPage'
import { DeploymentsPage } from './pages/DeploymentsPage'
import { DiagnosticsPage } from './pages/DiagnosticsPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { MarketplacePage } from './pages/MarketplacePage'
import { InferenceServicesPage } from './pages/InferenceServicesPage'
import { ProductServicesPage } from './pages/ProductServicesPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { OverviewPage } from './pages/OverviewPage'
import { PocPage } from './pages/PocPage'
import { ProvidersPage } from './pages/ProvidersPage'
import { PromptsPage } from './pages/PromptsPage'
import { RevisionsPage } from './pages/RevisionsPage'
import { SecurityPage } from './pages/SecurityPage'
import { PlatformLoginPage } from './pages/PlatformLoginPage'
import { PlatformDiagnosticsPage } from './pages/PlatformDiagnosticsPage'
import { ShopifyPackageProfilesPage } from './pages/ShopifyPackageProfilesPage'
import { ShopifyStoresPage } from './pages/ShopifyStoresPage'
import { UsersPage } from './pages/UsersPage'
import { VectorizationPage } from './pages/VectorizationPage'
import { VerificationPage } from './pages/VerificationPage'
import { VerificationOpsPage } from './pages/VerificationOpsPage'
import { DeploymentWorkspaceProvider } from './workspace/DeploymentWorkspaceContext'

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
    <DeploymentWorkspaceProvider>
      <AppShell session={auth.session} onSignOut={auth.signOut}>
        <Routes>
          <Route path="/" element={<Navigate to="/deployments" replace />} />
          <Route path="/deployments" element={<DeploymentsPage />} />
          <Route path="/overview" element={<OverviewPage />} />
          <Route path="/activity" element={<ActivityPage />} />
          <Route path="/actions" element={<ActionsPage />} />
          <Route path="/approvals" element={<ApprovalsPage />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/access" element={<AccessPage />} />
          <Route path="/knowledge" element={<KnowledgePage />} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="/inference-services" element={<InferenceServicesPage />} />
          <Route path="/product-services" element={<ProductServicesPage />} />
          <Route path="/shopify-package-profiles" element={<ShopifyPackageProfilesPage />} />
          <Route path="/shopify-stores" element={<ShopifyStoresPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/poc" element={<PocPage />} />
          <Route path="/prompts" element={<PromptsPage />} />
          <Route path="/providers" element={<ProvidersPage />} />
          <Route path="/security" element={<SecurityPage />} />
          <Route path="/verification" element={<VerificationPage />} />
          <Route path="/verification-ops" element={<VerificationOpsPage />} />
          <Route path="/vectorization" element={<VectorizationPage />} />
          <Route path="/revisions" element={<RevisionsPage />} />
          <Route path="/diagnostics" element={<DiagnosticsPage />} />
          <Route path="/platform-diagnostics" element={<PlatformDiagnosticsPage />} />
          <Route path="/users" element={<UsersPage />} />
        </Routes>
      </AppShell>
    </DeploymentWorkspaceProvider>
  )
}
