import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Divider, MenuItem, Stack, TextField, Typography } from '@mui/material'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import CableOutlined from '@mui/icons-material/CableOutlined'
import {
  fetchCustomerDeploymentPortfolio, fetchDeploymentIntegrations, fetchDeploymentReleases,
  fetchDeploymentTargetContext, fetchDocumentSources, fetchDocumentStorageBindings, fetchConversationHandoverOperations,
  type DeploymentTargetReference,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { CustomerConfigurationPanel } from '../components/customer/CustomerConfigurationPanel'
import { DocumentKnowledgePage } from './DocumentKnowledgePage'
import { IntegrationsPage, ConversationHandoverOperations, ConversationHandoverDeliveryNotices } from './IntegrationsPage'
import { safeCustomerAppUrl } from '../components/customer/customerResourceModels'
import { CustomerDetailSections, type CustomerEditorSection } from '../components/customer/CustomerDetailSections'

export function CustomerKnowledgeConnectionsPage({ mode }: { mode: 'knowledge' | 'connections' | 'human' }) {
  const { session } = usePlatformAuth()
  const location = useLocation()
  if (session?.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION' || !session.customerId) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  if (new URLSearchParams(location.search).get('view') === 'manage') {
    return mode === 'knowledge' ? <DocumentKnowledgePage /> : <IntegrationsPage handoverOnly={mode === 'human'} />
  }
  return <CustomerKnowledgeConnectionsReads key={`${session.actorId}:${session.customerId}`} mode={mode} customerId={session.customerId} />
}

function CustomerKnowledgeConnectionsReads({ mode, customerId }: { mode: 'knowledge' | 'connections' | 'human'; customerId: string }) {
  const location = useLocation()
  const navigate = useNavigate()
  const params = new URLSearchParams(location.search)
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio })
  const deploymentId = params.get('deploymentId') ?? ''
  const deployment = portfolio.data?.find(item => item.deploymentId === deploymentId)
  const releases = useQuery({ queryKey: ['deployment-releases', deployment?.deploymentId], queryFn: () => fetchDeploymentReleases(deployment!.deploymentId), enabled: Boolean(deployment) })
  const releaseId = params.get('releaseId') ?? ''
  const availableReleases = (releases.data ?? []).filter(item => item.targetProfileId && item.provisioningStatus === 'ACTIVE'
    && ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(item.status))
  const release = availableReleases.find(item => item.id === releaseId)
  const target: DeploymentTargetReference = { deploymentId: deployment?.deploymentId ?? '', targetProfileId: release?.targetProfileId ?? '', releaseId: release?.id ?? '' }
  const context = useQuery({ queryKey: ['deployment-target-context', target.deploymentId, target.targetProfileId, target.releaseId], queryFn: () => fetchDeploymentTargetContext(target), enabled: Boolean(release), retry: false })
  const documentsConfigured = Boolean(context.data?.documentDatasetIds.length)
  const sources = useQuery({ queryKey: ['document-sources', target.deploymentId, target.targetProfileId, target.releaseId], queryFn: () => fetchDocumentSources(target), enabled: mode === 'knowledge' && documentsConfigured, retry: false })
  const bindings = useQuery({ queryKey: ['document-storage-bindings', target.deploymentId], queryFn: () => fetchDocumentStorageBindings(target.deploymentId), enabled: mode === 'knowledge' && Boolean(deployment), retry: false })
  const connections = useQuery({ queryKey: ['deployment-integrations', target.deploymentId, target.targetProfileId, target.releaseId], queryFn: () => fetchDeploymentIntegrations(target), enabled: Boolean(mode === 'connections' && context.data?.externalIntegrationConfigured), retry: false })
  const handover = useQuery({ queryKey: ['conversation-handover-operations', target.deploymentId, target.targetProfileId, target.releaseId], queryFn: () => fetchConversationHandoverOperations(target), enabled: Boolean(mode === 'human' && context.data?.conversationHandoverConfigured), retry: false })
  const setParams = (values: Record<string, string | null>) => {
    const next = new URLSearchParams(location.search)
    Object.entries(values).forEach(([key, value]) => value == null ? next.delete(key) : next.set(key, value))
    navigate({ pathname: location.pathname, search: next.toString() })
  }
  const scopedBindings = (bindings.data ?? []).filter(item => item.deploymentId === target.deploymentId && item.targetProfileId === target.targetProfileId)
  const selectedSourceId = params.get('sourceId') ?? ''
  const selectedSource = sources.data?.find(item => item.sourceId === selectedSourceId)
  const selectedConnectionId = params.get('connectionId') ?? ''
  const selectedConnection = connections.data?.sources.find(item => item.sourceId === selectedConnectionId)
  const errors = [portfolio, releases, context, ...(mode === 'knowledge' ? [sources, bindings] : mode === 'human' ? [handover] : [connections])].flatMap(query => query.isError ? [query.error instanceof Error ? query.error.message : 'The selected resource could not be loaded.'] : [])
  const configure = (section: CustomerEditorSection = 'configuration') => setParams({ editor: 'configuration', editorSection: section })
  const closeConfiguration = () => setParams({ editor: null, editorSection: null })
  const editorOpen = Boolean(deployment && release && params.get('editor') === 'configuration' && (mode !== 'human' || context.data?.conversationHandoverConfigured))
  const title = mode === 'knowledge' ? 'Knowledge & data' : mode === 'human' ? 'Human support' : 'Connections'
  return <Stack spacing={3}>
    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
      <Box><Typography variant="h4">{title}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>{mode === 'knowledge' ? 'Approved documents and their storage bindings, scoped to a target release.' : mode === 'human' ? 'Conversation handover, delivery status and named inbox operators. Separate from action approvals.' : 'Source synchronization and provider connections for a selected target release.'}</Typography></Box>
      <Button variant="contained" startIcon={<SettingsOutlined />} disabled={!deployment || !release || (mode === 'human' && !context.data?.conversationHandoverConfigured)} onClick={() => configure()}>Manage</Button>
    </Stack>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
      <TextField select size="small" label="Deployment" value={deployment?.deploymentId ?? ''} onChange={event => setParams({ deploymentId: event.target.value, releaseId: null, sourceId: null, connectionId: null, editor: null })} sx={{ minWidth: 260 }}>
        <MenuItem value="">Select deployment</MenuItem>{(portfolio.data ?? []).map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}
      </TextField>
      <TextField select size="small" label="Target release" value={release?.id ?? ''} disabled={!deployment || releases.isPending} onChange={event => setParams({ releaseId: event.target.value, sourceId: null, connectionId: null, editor: null })} sx={{ minWidth: 260 }}>
        <MenuItem value="">Select target release</MenuItem>{availableReleases.map(item => <MenuItem key={item.id} value={item.id}>{item.targetProfileId} / {item.id}</MenuItem>)}
      </TextField>
    </Stack>
    {errors.map((error,index) => <Alert key={index} severity="error">{error}</Alert>)}
    {deploymentId && portfolio.isSuccess && !deployment ? <Alert severity="error">The requested deployment is not available in this account. No alternative deployment was selected.</Alert> : null}
    {releaseId && releases.isSuccess && !release ? <Alert severity="error">The requested target release is not available. No alternative release was selected.</Alert> : null}
    {portfolio.isPending || (Boolean(deployment) && releases.isPending) || (Boolean(release) && context.isPending) ? <CircularProgress aria-label="Loading selected configuration" /> : null}
    {!release && !releaseId ? <Alert severity="info">Select a deployment and target release to view its configuration.</Alert> : null}
    {context.data ? <Typography variant="caption" color="text.secondary">{context.data.environmentName} / {context.data.releaseStatus} / {context.data.releaseId}. Installed configuration is not a live health check.</Typography> : null}
    {mode === 'knowledge' && release && context.isSuccess ? <>
      {!documentsConfigured ? <Alert severity="info">No document dataset is installed in this target release.</Alert> : sources.isPending ? <CircularProgress size={22} aria-label="Loading indexed documents" /> : sources.isSuccess && !sources.data.length ? <Alert severity="info">No document sources are registered in this target release.</Alert> : null}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>
        {(sources.data ?? []).map(source => <Card key={source.sourceId}><CardActionArea onClick={() => setParams({ sourceId: source.sourceId })}><CardContent>
          <Stack direction="row" justifyContent="space-between"><DescriptionOutlined color="primary" /><Chip size="small" label={source.status} color={source.failureCode ? 'error' : source.status === 'ACTIVE' ? 'success' : 'default'} /></Stack>
          <Typography fontWeight={600} sx={{ mt: 2, overflowWrap: 'anywhere' }}>{source.displayName}</Typography>
          <Typography variant="caption" color="text.secondary">Active version: {source.activeVersion ?? 'None'} / Candidate: {source.candidateVersion}</Typography>
          {source.failureMessage ? <Alert severity="error" sx={{ mt: 1 }}>{source.failureMessage}</Alert> : null}
        </CardContent></CardActionArea></Card>)}
      </Box>
      {selectedSourceId && sources.isSuccess && !selectedSource ? <Alert severity="error">The requested document source is not available for this target.</Alert> : null}
      {selectedSource ? <Box component="aside" aria-label="Document details" sx={{ borderLeft: 2, borderColor: 'primary.main', pl: 2 }}>
        <Typography variant="h6" sx={{ overflowWrap: 'anywhere' }}>{selectedSource.displayName}</Typography>
        {selectedSource.failureMessage ? <Alert severity="error" sx={{ my: 1 }}>{selectedSource.failureMessage}</Alert> : null}
        <CustomerDetailSections key={`${target.releaseId}:${selectedSource.sourceId}`} preferenceKey="document"
          overview={<Stack spacing={1}><Typography variant="body2">{selectedSource.mediaType} / {selectedSource.contentLength.toLocaleString()} bytes / {selectedSource.visibility}</Typography><Typography variant="caption" color="text.secondary">Updated {new Date(selectedSource.updatedAt).toLocaleString()}</Typography></Stack>}
          configuration={<Box><Button onClick={() => configure()} startIcon={<SettingsOutlined />}>Manage document</Button></Box>}
          advanced={<Stack spacing={1} sx={{ overflowWrap: 'anywhere' }}><Typography variant="caption">Source: {selectedSource.sourceId}</Typography><Typography variant="caption">Dataset: {selectedSource.datasetId}</Typography><Typography variant="caption">Storage binding: {selectedSource.connectorBindingRef ?? 'Not recorded'}</Typography><Typography variant="caption">Provider revision: {selectedSource.providerRevisionFingerprint}</Typography><Button onClick={() => configure('advanced')}>Open advanced document settings</Button></Stack>} />
      </Box> : null}
      <Divider /><Typography variant="h6">Storage bindings</Typography>
      {bindings.isSuccess && !scopedBindings.length ? <Alert severity="info">No storage binding is recorded for this target.</Alert> : null}
      {scopedBindings.map(binding => <Box key={binding.bindingRef} sx={{ borderBottom: 1, borderColor: 'divider', py: 1 }}><Typography fontWeight={600}>{binding.connectorType}</Typography><Typography variant="body2" color="text.secondary">{binding.status} / Credentials {binding.credentialsPresent ? 'present' : 'not present'}</Typography></Box>)}
    </> : null}
    {mode === 'connections' && release && context.isSuccess ? <>
      {!context.data.externalIntegrationConfigured ? <Alert severity="info">No external data integration is installed in this target release.</Alert> : connections.isPending ? <CircularProgress size={22} aria-label="Loading source connections" /> : null}
      {connections.isSuccess && !connections.data.success ? <Alert severity="error">The integration service did not return a successful operational overview.</Alert> : null}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>{(connections.data?.sources ?? []).map(source => <Card key={source.sourceId}><CardActionArea onClick={() => setParams({ connectionId: source.sourceId })}><CardContent><Stack direction="row" justifyContent="space-between"><CableOutlined color="primary" /><Chip size="small" label={source.state.status || 'Unknown'} color={source.state.errorClass ? 'error' : 'default'} /></Stack><Typography fontWeight={600} sx={{ mt: 2, overflowWrap: 'anywhere' }}>{source.sourceId}</Typography><Typography variant="body2" color="text.secondary">{source.freshnessState} / {source.vectorSpace}</Typography>{source.state.errorMessage ? <Alert severity="error" sx={{ mt: 1 }}>{source.state.errorMessage}</Alert> : null}</CardContent></CardActionArea></Card>)}</Box>
      {selectedConnectionId && connections.isSuccess && !selectedConnection ? <Alert severity="error">The requested connection is not available for this target.</Alert> : null}
      {selectedConnection ? <Box component="aside" aria-label="Connection details" sx={{ borderLeft: 2, borderColor: 'primary.main', pl: 2 }}>
        <Typography variant="h6" sx={{ overflowWrap: 'anywhere' }}>{selectedConnection.sourceId}</Typography>
        {selectedConnection.state.errorMessage ? <Alert severity="error" sx={{ my: 1 }}>{selectedConnection.state.errorMessage}</Alert> : null}
        <CustomerDetailSections key={`${target.releaseId}:${selectedConnection.sourceId}`} preferenceKey="connection"
          overview={<Stack spacing={1}><Typography variant="body2">Scheduled every {selectedConnection.scheduleSeconds} seconds / {selectedConnection.state.counts.indexedCount} indexed</Typography><Typography variant="caption" color="text.secondary">Last successful sync: {selectedConnection.state.lastSuccessAt ? new Date(selectedConnection.state.lastSuccessAt).toLocaleString() : 'Not recorded'}</Typography></Stack>}
          configuration={<Box><Button onClick={() => configure()} startIcon={<SettingsOutlined />}>Manage connection</Button></Box>}
          advanced={<Stack spacing={1} sx={{ overflowWrap: 'anywhere' }}><Typography variant="caption">Connection profile: {selectedConnection.connectionProfileRef}</Typography><Typography variant="caption">Resource binding: {selectedConnection.protectedResourceBindingRef}</Typography><Typography variant="caption">Entity / vector space: {selectedConnection.entityType} / {selectedConnection.vectorSpace}</Typography><Button onClick={() => configure('advanced')}>Open advanced connection settings</Button></Stack>} />
      </Box> : null}
      {context.data.conversationHandoverConfigured ? <Alert severity="info" action={<Button component={RouterLink} to={`/human-support?${new URLSearchParams(target)}`}>Open support</Button>}>A human-support channel is installed in this target release.</Alert> : null}
    </> : null}
    {mode === 'human' && release && context.isSuccess ? <>
      {!context.data.conversationHandoverConfigured ? <Alert severity="info">Human support is not installed in this target release. No support operation is available.</Alert> : handover.isPending ? <CircularProgress size={22} aria-label="Loading human support status" /> : null}
      {handover.data && !handover.data.success ? <Alert severity="error">Human support did not return a successful operational overview.</Alert> : null}
      {handover.data?.success ? <>
        {!handover.data.connector.reachable ? <Alert severity="error">The conversation connector is unavailable.</Alert> : null}
        {!handover.data.runtime.enabled ? <Alert severity="warning">Conversation handover is disabled in the runtime.</Alert> : null}
        <ConversationHandoverDeliveryNotices value={handover.data} />
        <CustomerDetailSections key={target.releaseId} preferenceKey="human-support"
          overview={<Stack spacing={2}><Typography variant="h6">Conversation handover</Typography><Typography variant="body2">{handover.data.runtime.enabled ? 'Enabled' : 'Disabled'} / {handover.data.connector.reachable ? 'Connector reachable' : 'Connector unavailable'}</Typography><Stack direction="row" gap={1} flexWrap="wrap">{Object.entries(handover.data.runtime.handoverCounts).map(([status,count]) => <Chip key={status} size="small" label={`${status} ${count}`} />)}</Stack>{safeCustomerAppUrl(handover.data.operatorConsoleUrl ?? null) ? <Button component="a" href={safeCustomerAppUrl(handover.data.operatorConsoleUrl ?? null)!} target="_blank" rel="noopener noreferrer">Operator inbox</Button> : null}</Stack>}
          configuration={<Box><Button onClick={() => configure()}>Manage channel and operators</Button></Box>}
          advanced={<ConversationHandoverOperations value={{ ...handover.data, operatorConsoleUrl: safeCustomerAppUrl(handover.data.operatorConsoleUrl ?? null) ?? undefined }} canOperate={false} busy={false} onRetry={() => {}} onExpire={() => {}} />} />
      </> : null}
    </> : null}
    <CustomerConfigurationPanel open={editorOpen} title={`${deployment?.name ?? ''} / ${title}`} scopeKey={`${mode}:${customerId}:${target.deploymentId}:${target.targetProfileId}:${target.releaseId}`} initialSection={params.get('editorSection') === 'advanced' ? 'advanced' : 'configuration'} notices={<Stack spacing={1} sx={{ mb: 2 }}>{errors.map((error,index) => <Alert key={index} severity="error">{error}</Alert>)}{selectedSource?.failureMessage ? <Alert severity="error">{selectedSource.failureMessage}</Alert> : null}{selectedConnection?.state.errorMessage ? <Alert severity="error">{selectedConnection.state.errorMessage}</Alert> : null}{mode === 'human' && handover.data && !handover.data.connector.reachable ? <Alert severity="error">The conversation connector is unavailable.</Alert> : null}</Stack>} onClose={closeConfiguration}>
      {editorOpen ? mode === 'knowledge' ? <DocumentKnowledgePage key={`${target.deploymentId}:${target.releaseId}`} initialReleaseId={target.releaseId} initialSourceId={selectedSource?.sourceId} lockReleaseSelection /> : <IntegrationsPage key={`${target.deploymentId}:${target.releaseId}`} initialReleaseId={target.releaseId} handoverOnly={mode === 'human'} lockReleaseSelection /> : null}
    </CustomerConfigurationPanel>
  </Stack>
}
