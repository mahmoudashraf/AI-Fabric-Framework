import { Alert } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { fetchAIWorkspaceInstallations, fetchCustomerDeploymentPortfolio, fetchCustomerSelfServiceCatalogue, fetchDeploymentReleases, type AIWorkspaceInstallationSummary, type PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerDeploymentConsole } from '../components/customer/CustomerDeploymentConsole'
import { CustomerResourceSurface } from '../components/customer/CustomerResourceSurface'
import { CustomerSetupNavigationGuard } from '../components/customer/CustomerSetupNavigationGuard'
import { CustomerEditorSections } from '../components/customer/CustomerDetailSections'
import { CustomerReleaseSetupRedirect } from '../components/customer/CustomerReleaseSetupRedirect'
import { CustomerBrainManageRedirect } from '../components/customer/CustomerBrainManageRedirect'
import { AIWorkspacesPage } from './AIWorkspacesPage'
import { EngageReturnNavigation } from '../components/customer/EngageReturnNavigation'
import { closeEngageToolRoute, normalizeLegacyEngageRoute } from '../components/customer/engagePresentation'

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
  const engageRoute = mode === 'apps' ? normalizeLegacyEngageRoute(params) : null
  if (engageRoute) return <Navigate to={{ pathname: location.pathname, search: engageRoute.toString() }} replace state={location.state} />
  if (mode !== 'apps' && view === 'manage') {
    return setupBrainId ? <CustomerBrainManageRedirect customerId={session.customerId!} brainId={setupBrainId} />
      : <Navigate to="/deployments" replace />
  }
  if (mode !== 'apps' && view === 'setup' && setupBrainId && (setupStep === 'staging' || setupStep === 'production')) {
    return <CustomerReleaseSetupRedirect customerId={session.customerId!} brainId={setupBrainId} step={setupStep} />
  }
  if (mode !== 'apps' && (view === 'manage' || view === 'setup')) {
    return <CustomerSetupNavigationGuard><CustomerEditorSections scopeKey={`setup:${session.customerId}`} showDepthSwitch={false}><CustomerDeploymentConsole session={session} guided /></CustomerEditorSections></CustomerSetupNavigationGuard>
  }
  return <CustomerResourceReads mode={mode} session={session} />
}

function CustomerResourceReads({ mode, session }: { mode: 'home' | 'deployments' | 'apps'; session: PlatformAuthSessionSummary }) {
  const customerId = session.customerId!
  const location = useLocation()
  const navigate = useNavigate()
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
      presentation="details"
      initialDetailsSection={params.get('appSection') === 'delivery' ? 'delivery' : params.get('appSection') === 'readiness' ? 'readiness' : undefined}
      navigation={<EngageReturnNavigation app={selectedApp} deployments={portfolio.data ?? []} />} />
      : resourceKind === 'deployment' && deploymentId ? <CustomerDeploymentConsole key={`${customerId}:${deploymentId}`} session={session} guided /> : null
    : null
  const tool = params.get('appTool')
  const sourceAppId = params.get('sourceInstallationId')
  const closeTool = (saved?: AIWorkspaceInstallationSummary) => {
    if (saved && saved.customerId !== customerId) throw new Error('The saved app does not belong to the current workspace.')
    navigate({ pathname: location.pathname, search: closeEngageToolRoute(params, saved).toString() }, { replace: true, state: location.state })
  }
  const toolsReady = mode === 'apps' && !configurationRequested && portfolio.isSuccess && installations.isSuccess
  const requestedAppAvailable = !resourceId || Boolean(selectedApp)
  const knownTool = ['connection', 'appearance', 'create'].includes(tool ?? '')
  const connectionSourceAvailable = tool !== 'connection' || Boolean(selectedApp || params.get('connectDeploymentId'))
  if (tool && !knownTool) errors.push('This Engage setup action is unavailable. Select your app and choose a setup step.')
  if (tool === 'appearance' && !resourceId) errors.push('Select an Engage app before opening its appearance.')
  if (!connectionSourceAvailable) errors.push('Select an Engage app or Smart Brain before opening its connection.')
  if (sourceAppId && sourceAppId !== selectedApp?.installationId && installations.isSuccess) errors.push('The source app does not match the selected app. No configuration was opened.')
  const tools = toolsReady && knownTool && requestedAppAvailable && connectionSourceAvailable && (!sourceAppId || sourceAppId === selectedApp?.installationId)
    && (tool !== 'appearance' || selectedApp)
    ? <CustomerEditorSections scopeKey={`engage-tool:${customerId}:${tool}:${resourceId ?? ''}`} showDepthSwitch={false}>
      <AIWorkspacesPage key={`${tool}:${resourceId ?? ''}:${params.get('connectDeploymentId') ?? ''}`} presentation="tools"
        initialInstallationId={selectedApp?.installationId} initialConsumerId={params.get('consumerId') ?? undefined}
        sourceInstallationId={tool === 'create' ? sourceAppId ?? undefined : undefined}
        autoCreate={tool === 'create'} autoPreview={tool === 'appearance'}
        connectDeploymentId={tool === 'connection' ? params.get('connectDeploymentId') ?? undefined : undefined}
        connectInstallationId={tool === 'connection' ? selectedApp?.installationId : undefined}
        initialDeliveryMode={params.get('delivery') === 'embedded' ? 'embedded' : params.get('delivery') === 'standalone' ? 'standalone' : undefined}
        onToolClose={closeTool}
        navigation={selectedApp ? <EngageReturnNavigation app={selectedApp} deployments={portfolio.data ?? []} /> : undefined} />
    </CustomerEditorSections> : null
  return <CustomerResourceSurface mode={mode} deployments={portfolio.data ?? []} apps={apps}
    offers={(offers.data ?? []).filter(offer => offer.entitlement.customerId === customerId)}
    releases={releases.data ?? []} releaseLoading={Boolean(deploymentId) && releases.isPending}
    releaseError={releases.isError ? (releases.error instanceof Error ? releases.error.message : 'Release read failed.') : null}
    loading={portfolio.isPending || offers.isPending || installations.isPending} errors={errors}
    configuration={configuration} tools={tools}
    dataObservedAt={Math.min(portfolio.dataUpdatedAt || Infinity, offers.dataUpdatedAt || Infinity, installations.dataUpdatedAt || Infinity) === Infinity ? null : Math.min(portfolio.dataUpdatedAt || Infinity, offers.dataUpdatedAt || Infinity, installations.dataUpdatedAt || Infinity)} />
}
