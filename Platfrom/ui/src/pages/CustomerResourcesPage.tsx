import { Alert, Box } from '@mui/material'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useLocation } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { fetchAIWorkspaceInstallations, fetchCustomerDeploymentPortfolio, fetchCustomerSelfServiceCatalogue, fetchDeploymentReleases, type PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerDeploymentConsole } from '../components/customer/CustomerDeploymentConsole'
import { CustomerResourceSurface } from '../components/customer/CustomerResourceSurface'
import { CustomerSetupNavigationGuard } from '../components/customer/CustomerSetupNavigationGuard'
import { CustomerEditorSections } from '../components/customer/CustomerDetailSections'
import { CustomerReleaseSetupRedirect } from '../components/customer/CustomerReleaseSetupRedirect'
import { CustomerBrainManageRedirect } from '../components/customer/CustomerBrainManageRedirect'
import { AIWorkspacesPage } from './AIWorkspacesPage'
import { EngageReturnNavigation } from '../components/customer/EngageReturnNavigation'

export function CustomerResourcesPage({ mode }: { mode: 'home' | 'deployments' | 'apps' }) {
  const { session } = usePlatformAuth()
  const location = useLocation()
  if (!isCustomerSession(session)) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  const view = new URLSearchParams(location.search).get('view')
  const params = new URLSearchParams(location.search)
  const setupStep = params.get('step')
  const setupBrainId = params.get('deploymentId')
  if (mode !== 'apps' && view === 'manage') {
    return setupBrainId ? <CustomerBrainManageRedirect customerId={session.customerId!} brainId={setupBrainId} />
      : <Navigate to="/deployments" replace />
  }
  if (mode !== 'apps' && view === 'setup' && setupBrainId && (setupStep === 'staging' || setupStep === 'production')) {
    return <CustomerReleaseSetupRedirect customerId={session.customerId!} brainId={setupBrainId} step={setupStep} />
  }
  if (view === 'manage' || view === 'setup') {
    if (mode === 'apps') {
      return <CustomerEngageEditor key={location.search} customerId={session.customerId!} />
    }
    return <CustomerSetupNavigationGuard><CustomerEditorSections scopeKey={`setup:${session.customerId}`} showDepthSwitch={false}><CustomerDeploymentConsole session={session} guided /></CustomerEditorSections></CustomerSetupNavigationGuard>
  }
  return <CustomerResourceReads mode={mode} session={session} />
}

function CustomerEngageEditor({ customerId }: { customerId: string }) {
  const params = new URLSearchParams(useLocation().search), delivery = params.get('delivery')
  const appId = params.get('previewInstallationId') ?? params.get('connectInstallationId') ?? params.get('sourceInstallationId')
  const apps = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId), enabled: Boolean(appId) })
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio, enabled: Boolean(appId) })
  const app = apps.data?.find(item => item.installationId === appId && item.customerId === customerId)
  const navigation: ReactNode = app ? <EngageReturnNavigation app={app} deployments={portfolio.data ?? []} /> : null
  return <CustomerEditorSections scopeKey={`engage:${customerId}`}><Box sx={{ mb: app ? 1 : 0 }}>{navigation}</Box><AIWorkspacesPage initialConsumerId={params.get('consumerId') ?? undefined}
        initialInstallationId={params.get('previewInstallationId') ?? undefined} autoPreview={Boolean(params.get('previewInstallationId'))}
        sourceInstallationId={params.get('sourceInstallationId') ?? undefined}
        connectDeploymentId={params.get('connectDeploymentId') ?? undefined}
        connectInstallationId={params.get('connectInstallationId') ?? undefined}
        navigation={navigation} initialDeliveryMode={delivery === 'embedded' || delivery === 'standalone' ? delivery : undefined} /></CustomerEditorSections>
}

function CustomerResourceReads({ mode, session }: { mode: 'home' | 'deployments' | 'apps'; session: PlatformAuthSessionSummary }) {
  const customerId = session.customerId!
  const location = useLocation()
  const params = new URLSearchParams(location.search)
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio })
  const offers = useQuery({ queryKey: ['customer-self-service-catalogue', customerId], queryFn: fetchCustomerSelfServiceCatalogue })
  const installations = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId) })
  const apps = (installations.data ?? []).filter(app => app.customerId === customerId)
  const resourceId = params.get('resourceId')
  const resourceKind = params.get('resourceKind')
  const relatedDeploymentId = resourceKind === 'deployment' ? resourceId : resourceKind === 'app' ? apps.find(app => app.installationId === resourceId)?.deploymentId : null
  const deploymentId = portfolio.data?.find(deployment => deployment.deploymentId === relatedDeploymentId)?.deploymentId
  const releases = useQuery({
    queryKey: ['deployment-releases', deploymentId], queryFn: () => fetchDeploymentReleases(deploymentId!), enabled: Boolean(deploymentId),
  })
  const errors = [portfolio, offers, installations].flatMap(query => query.isError ? [query.error instanceof Error ? query.error.message : 'Resource data could not be loaded.'] : [])
  const selectedApp = resourceKind === 'app' ? apps.find(app => app.installationId === resourceId) : null
  const configurationRequested = params.get('editor') === 'configuration'
  const configurationScopeMismatch = Boolean(configurationRequested && deploymentId && params.get('deploymentId') !== deploymentId)
  if (configurationScopeMismatch) errors.push('Configuration scope does not match the inspected resource. Close the resource and reopen its configuration; no editor or command was opened.')
  const configuration = configurationRequested && !configurationScopeMismatch
    ? selectedApp ? <AIWorkspacesPage key={`${customerId}:${selectedApp.installationId}`} initialInstallationId={selectedApp.installationId}
      initialDetailsSection={params.get('appSection') === 'delivery' ? 'delivery' : params.get('appSection') === 'readiness' ? 'readiness' : undefined}
      navigation={<EngageReturnNavigation app={selectedApp} deployments={portfolio.data ?? []} />} />
      : resourceKind === 'deployment' && deploymentId ? <CustomerDeploymentConsole key={`${customerId}:${deploymentId}`} session={session} guided /> : null
    : null
  return <CustomerResourceSurface mode={mode} deployments={portfolio.data ?? []} apps={apps}
    offers={(offers.data ?? []).filter(offer => offer.entitlement.customerId === customerId)}
    releases={releases.data ?? []} releaseLoading={Boolean(deploymentId) && releases.isPending}
    releaseError={releases.isError ? (releases.error instanceof Error ? releases.error.message : 'Release read failed.') : null}
    loading={portfolio.isPending || offers.isPending || installations.isPending} errors={errors}
    configuration={configuration}
    dataObservedAt={Math.min(portfolio.dataUpdatedAt || Infinity, offers.dataUpdatedAt || Infinity, installations.dataUpdatedAt || Infinity) === Infinity ? null : Math.min(portfolio.dataUpdatedAt || Infinity, offers.dataUpdatedAt || Infinity, installations.dataUpdatedAt || Infinity)} />
}
