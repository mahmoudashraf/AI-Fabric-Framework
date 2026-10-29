import { Alert } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { fetchAIWorkspaceInstallations, fetchCustomerDeploymentPortfolio, fetchCustomerSelfServiceCatalogue, fetchDeploymentReleases, type PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerDeploymentConsole } from '../components/customer/CustomerDeploymentConsole'
import { CustomerResourceSurface } from '../components/customer/CustomerResourceSurface'
import { CustomerSetupNavigationGuard } from '../components/customer/CustomerSetupNavigationGuard'
import { CustomerEditorSections } from '../components/customer/CustomerDetailSections'
import { AIWorkspacesPage } from './AIWorkspacesPage'

export function CustomerResourcesPage({ mode }: { mode: 'home' | 'deployments' | 'apps' }) {
  const { session } = usePlatformAuth()
  const location = useLocation()
  if (!isCustomerSession(session)) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  const view = new URLSearchParams(location.search).get('view')
  if (view === 'manage' || view === 'setup') {
    if (mode === 'apps') {
      const params = new URLSearchParams(location.search)
      const delivery = params.get('delivery')
      return <CustomerEditorSections key={location.search} scopeKey={`engage:${session.customerId}`}><AIWorkspacesPage initialConsumerId={params.get('consumerId') ?? undefined}
        initialInstallationId={params.get('previewInstallationId') ?? undefined} autoPreview={Boolean(params.get('previewInstallationId'))}
        sourceInstallationId={params.get('sourceInstallationId') ?? undefined}
        connectDeploymentId={params.get('connectDeploymentId') ?? undefined}
        connectInstallationId={params.get('connectInstallationId') ?? undefined}
        initialDeliveryMode={delivery === 'embedded' || delivery === 'standalone' ? delivery : undefined} /></CustomerEditorSections>
    }
    return view === 'setup' ? <CustomerSetupNavigationGuard><CustomerEditorSections scopeKey={`setup:${session.customerId}`}><CustomerDeploymentConsole session={session} guided /></CustomerEditorSections></CustomerSetupNavigationGuard>
      : <CustomerDeploymentConsole session={session} />
  }
  return <CustomerResourceReads mode={mode} session={session} />
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
    ? selectedApp ? <AIWorkspacesPage key={`${customerId}:${selectedApp.installationId}`} initialInstallationId={selectedApp.installationId} />
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
