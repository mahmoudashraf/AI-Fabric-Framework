import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material'
import { usePlatformAuth } from './auth/PlatformAuthProvider'
import { isCustomerSession } from './auth/customerSession'
import { DeskOAuthAuthorizationPage } from './pages/DeskOAuthAuthorizationPage'
import { CustomerIdentityLinkPage } from './pages/CustomerIdentityLinkPage'
import { CustomerAuthCallbackPage } from './pages/CustomerAuthCallbackPage'
import { CustomerAuthRecoveryPage } from './pages/CustomerAuthRecoveryPage'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { CustomerConsoleShell } from './layout/CustomerConsoleShell'
import { CustomerResourcesPage } from './pages/CustomerResourcesPage'
import { CustomerKnowledgeConnectionsPage } from './pages/CustomerKnowledgeConnectionsPage'
import { CustomerActionReviewsPage } from './pages/CustomerActionReviewsPage'
import { CustomerAccountPage } from './pages/CustomerAccountPage'
import { CustomerDeploymentAdvanced } from './components/customer/CustomerDeploymentAdvanced'
import { ActionsPage } from './pages/ActionsPage'
import { AIWorkspacesPage } from './pages/AIWorkspacesPage'
import { BehaviorPage } from './pages/BehaviorPage'
import { BehaviorOperationsPage } from './pages/BehaviorOperationsPage'
import { HumanReviewPage } from './pages/HumanReviewPage'
import { ActivityPage } from './pages/ActivityPage'
import { AccessPage } from './pages/AccessPage'
import { ApprovalsPage } from './pages/ApprovalsPage'
import { CustomersPage } from './pages/CustomersPage'
import { DeploymentsPage } from './pages/DeploymentsPage'
import { DiagnosticsPage } from './pages/DiagnosticsPage'
import { DocumentKnowledgePage } from './pages/DocumentKnowledgePage'
import { IntegrationsPage } from './pages/IntegrationsPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { MarketplacePage } from './pages/MarketplacePage'
import { InferenceServicesPage } from './pages/InferenceServicesPage'
import { ProductServicesPage } from './pages/ProductServicesPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { OverviewPage } from './pages/OverviewPage'
import { PartnerPrivilegesPage } from './pages/PartnerPrivilegesPage'
import { PocPage } from './pages/PocPage'
import { ProvidersPage } from './pages/ProvidersPage'
import { PromptsPage } from './pages/PromptsPage'
import { RevisionsPage } from './pages/RevisionsPage'
import { RuntimeArtifactsPage } from './pages/RuntimeArtifactsPage'
import { SecurityPage } from './pages/SecurityPage'
import { PlatformLoginPage } from './pages/PlatformLoginPage'
import { PlatformDiagnosticsPage } from './pages/PlatformDiagnosticsPage'
import { ShopifyPackageProfilesPage } from './pages/ShopifyPackageProfilesPage'
import { ShopifyLaunchReadinessPage } from './pages/ShopifyLaunchReadinessPage'
import { ShopifyReadinessAuditPage } from './pages/ShopifyReadinessAuditPage'
import { ShopifyStoresPage } from './pages/ShopifyStoresPage'
import { ThinkerResolverPage } from './pages/ThinkerResolverPage'
import { UsersPage } from './pages/UsersPage'
import { VectorizationPage } from './pages/VectorizationPage'
import { VerificationPage } from './pages/VerificationPage'
import { VerificationOpsPage } from './pages/VerificationOpsPage'
import { DeploymentWorkspaceProvider } from './workspace/DeploymentWorkspaceContext'

export default function App() {
  const auth = usePlatformAuth()
  const customerWorkspace = auth.session?.role === 'CUSTOMER_ADMIN'
  const Shell = customerWorkspace ? CustomerConsoleShell : AppShell

  // Identity-provider consent is available to Desk agents without Console membership.
  if (window.location.pathname === '/auth/desk/authorize') return <DeskOAuthAuthorizationPage />
  if (window.location.pathname === '/auth/callback') return <CustomerAuthCallbackPage />
  if (window.location.pathname === '/auth/recovery') return <CustomerAuthRecoveryPage />

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
          <Button onClick={() => { void auth.signOut() }}>Return to sign in</Button>
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

  if (auth.session?.role === 'CUSTOMER_ADMIN' && !isCustomerSession(auth.session)) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 3 }}>
        <Stack spacing={2} sx={{ maxWidth: 620 }}>
          <Alert severity="error">Customer self-service requires a verified customer session. API-key access cannot open the customer workspace.</Alert>
          <Button variant="contained" onClick={() => { void auth.signOut() }}>Return to sign in</Button>
        </Stack>
      </Box>
    )
  }

  if (customerWorkspace && auth.session?.authenticationMode === 'SESSION' && auth.customerConfig?.enabled) {
    return <CustomerIdentityLinkPage />
  }

  return (
    <DeploymentWorkspaceProvider key={customerWorkspace ? `customer:${auth.session?.actorId}:${auth.session?.customerId}` : auth.session?.actorId ?? auth.session?.customerId ?? 'platform-operator'}>
      <Shell session={auth.session} onSignOut={auth.signOut}>
        <Routes>
          <Route path="/" element={<Navigate to={customerWorkspace ? '/home' : '/deployments'} replace />} />
          {customerWorkspace ? (
            <>
              <Route path="/home" element={<CustomerResourcesPage mode="home" />} />
              <Route path="/deployments" element={<CustomerResourcesPage mode="deployments" />} />
              <Route path="/ai-workspaces" element={<CustomerResourcesPage mode="apps" />} />
              <Route path="/document-knowledge" element={<CustomerKnowledgeConnectionsPage mode="knowledge" />} />
              <Route path="/integrations" element={<CustomerKnowledgeConnectionsPage mode="connections" />} />
              <Route path="/human-support" element={<CustomerKnowledgeConnectionsPage mode="human" />} />
              <Route path="/human-review" element={<CustomerActionReviewsPage />} />
              <Route path="/revisions" element={<CustomerDeploymentAdvanced><RevisionsPage /></CustomerDeploymentAdvanced>} />
              <Route path="/actions" element={<CustomerDeploymentAdvanced><ActionsPage /></CustomerDeploymentAdvanced>} />
              <Route path="/prompts" element={<CustomerDeploymentAdvanced><PromptsPage /></CustomerDeploymentAdvanced>} />
              <Route path="/providers" element={<CustomerDeploymentAdvanced><ProvidersPage configurationOnly /></CustomerDeploymentAdvanced>} />
              <Route path="/plugins" element={<CustomerDeploymentAdvanced><MarketplacePage deploymentScoped /></CustomerDeploymentAdvanced>} />
              <Route path="/behavior" element={<Alert severity="warning">Behaviour management is not available under the customer console contract. Staff-only configuration is not exposed.</Alert>} />
              <Route path="/behavior-operations" element={<Alert severity="warning">Behaviour operations are not available under the customer console contract. No staff operation was requested.</Alert>} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/users" element={<CustomerAccountPage />} />
              <Route path="*" element={<Navigate to="/deployments" replace />} />
            </>
          ) : (
            <>
              <Route path="/deployments" element={<DeploymentsPage />} />
              <Route path="/overview" element={<OverviewPage />} />
              <Route path="/activity" element={<ActivityPage />} />
              <Route path="/actions" element={<ActionsPage />} />
              <Route path="/ai-workspaces" element={<AIWorkspacesPage />} />
              <Route path="/behavior" element={<BehaviorPage />} />
              <Route path="/behavior-operations" element={<BehaviorOperationsPage />} />
              <Route path="/human-review" element={<HumanReviewPage />} />
              <Route path="/approvals" element={<ApprovalsPage />} />
              <Route path="/customers" element={<CustomersPage />} />
              <Route path="/access" element={<AccessPage />} />
              <Route path="/knowledge" element={<KnowledgePage />} />
              <Route path="/document-knowledge" element={<DocumentKnowledgePage />} />
              <Route path="/integrations" element={<IntegrationsPage />} />
              <Route path="/marketplace" element={<MarketplacePage />} />
              <Route path="/inference-services" element={<InferenceServicesPage />} />
              <Route path="/product-services" element={<ProductServicesPage />} />
              <Route path="/partner-privileges" element={<PartnerPrivilegesPage />} />
              <Route path="/shopify-package-profiles" element={<ShopifyPackageProfilesPage />} />
              <Route path="/shopify-launch-readiness" element={<ShopifyLaunchReadinessPage />} />
              <Route path="/shopify-readiness-audit" element={<ShopifyReadinessAuditPage />} />
              <Route path="/shopify-stores" element={<ShopifyStoresPage />} />
              <Route path="/thinker-resolver" element={<ThinkerResolverPage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/poc" element={<PocPage />} />
              <Route path="/prompts" element={<PromptsPage />} />
              <Route path="/providers" element={<ProvidersPage />} />
              <Route path="/security" element={<SecurityPage />} />
              <Route path="/verification" element={<VerificationPage />} />
              <Route path="/verification-ops" element={<VerificationOpsPage />} />
              <Route path="/vectorization" element={<VectorizationPage />} />
              <Route path="/revisions" element={<RevisionsPage />} />
              <Route path="/runtime-artifacts" element={<RuntimeArtifactsPage />} />
              <Route path="/diagnostics" element={<DiagnosticsPage />} />
              <Route path="/platform-diagnostics" element={<PlatformDiagnosticsPage />} />
              <Route path="/users" element={<UsersPage />} />
            </>
          )}
        </Routes>
      </Shell>
    </DeploymentWorkspaceProvider>
  )
}
