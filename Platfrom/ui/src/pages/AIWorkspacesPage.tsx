import AddRoundedIcon from '@mui/icons-material/AddRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import PauseCircleOutlineRoundedIcon from '@mui/icons-material/PauseCircleOutlineRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  activateAIWorkspaceInstallation,
  createAIWorkspaceInstallation,
  deleteAIWorkspaceInstallation,
  disableAIWorkspaceInstallation,
  fetchAIWorkspaceCatalog,
  fetchAIWorkspaceInstallations,
  fetchCustomerDeploymentPortfolio,
  fetchCustomerSelfServiceCatalogue,
  fetchPlatformCustomers,
  getPlatformApiBaseUrl,
  type AIWorkspaceInstallationSummary,
  type PlatformCustomerSummary,
  type SaveAIWorkspaceInstallationRequest,
  updateAIWorkspaceInstallation,
  verifyAIWorkspaceDomain,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { resolveReviewedWorkspaceProfiles, reviewedWorkspaceRequest } from './aiWorkspaceReviewedProfiles'

type FormState = {
  packCode: string
  deliveryMode: string
  appSlug: string
  customDomains: string
  interactionPolicy: string
  authProfileCode: string
  supabaseProjectUrl: string
  supabasePublishableKey: string
  supabaseEmailPassword: boolean
  supabaseMagicLink: boolean
  displayName: string
  consumerId: string
  connectionMode: string
  profileCode: string
  origins: string
  dealerId: string
  assistantLabel: string
  sourceMode: string
  rootSelector: string
  contextLabel: string
  primaryColor: string
  comparison: boolean
  testDrive: boolean
  callback: boolean
  imageHosts: string
  detailBasePath: string
  detailSlugs: string
  shopDomain: string
}

const defaultForm: FormState = {
  packCode: 'generic-workspace',
  deliveryMode: 'standalone',
  appSlug: '',
  customDomains: '',
  interactionPolicy: 'public-with-protected-capabilities',
  authProfileCode: 'supabase',
  supabaseProjectUrl: '',
  supabasePublishableKey: '',
  supabaseEmailPassword: true,
  supabaseMagicLink: true,
  displayName: '',
  consumerId: '',
  connectionMode: 'public-runtime-anonymous',
  profileCode: 'runtime-anonymous-direct',
  origins: '',
  dealerId: '',
  assistantLabel: 'LoomAI Workspace',
  sourceMode: 'DEALERSHIP_INVENTORY',
  rootSelector: 'main',
  contextLabel: 'Current dealership page',
  primaryColor: '#123b35',
  comparison: true,
  testDrive: true,
  callback: true,
  imageHosts: '',
  detailBasePath: '/vehicles/',
  detailSlugs: '',
  shopDomain: '',
}

function statusColor(status: string): 'success' | 'warning' | 'default' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'default'
}

function errorText(error: unknown) {
  return error instanceof Error ? error.message : 'The operation failed.'
}

export function AIWorkspacesPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const customerScopeLocked = auth.session?.role === 'CUSTOMER_ADMIN'
  const [customerId, setCustomerId] = useState(() => customerScopeLocked ? (auth.session?.customerId ?? '') : '')
  const [selected, setSelected] = useState<AIWorkspaceInstallationSummary | null>(null)
  const [editing, setEditing] = useState<AIWorkspaceInstallationSummary | 'new' | null>(null)
  const [form, setForm] = useState<FormState>(defaultForm)
  const [notice, setNotice] = useState<string | null>(null)
  const [reviewedProfileId, setReviewedProfileId] = useState('')
  const [configurationError, setConfigurationError] = useState<string | null>(null)
  const saveCommand = useRef<{ identity: string; key: string } | null>(null)

  const customersQuery = useQuery({ queryKey: ['platform-customers'], queryFn: fetchPlatformCustomers })
  const catalogQuery = useQuery({ queryKey: ['ai-workspace-catalog'], queryFn: fetchAIWorkspaceCatalog })
  const installationsQuery = useQuery({
    queryKey: ['ai-workspace-installations', customerId],
    queryFn: () => fetchAIWorkspaceInstallations(customerId),
    enabled: Boolean(customerId),
  })
  const offersQuery = useQuery({
    queryKey: ['customer-self-service-catalogue', customerId],
    queryFn: fetchCustomerSelfServiceCatalogue,
    enabled: customerScopeLocked && Boolean(customerId) && editing != null,
  })
  const portfolioQuery = useQuery({
    queryKey: ['customer-deployment-portfolio', customerId],
    queryFn: fetchCustomerDeploymentPortfolio,
    enabled: customerScopeLocked && Boolean(customerId) && editing != null,
  })

  const customers = customersQuery.data ?? []
  const customer = customers.find((item) => item.id === customerId) ?? null
  const installations = installationsQuery.data ?? []
  const catalog = catalogQuery.data
  const compatibleProfiles = useMemo(
    () => (catalog?.connectionProfiles ?? []).filter((profile) => profile.mode === form.connectionMode),
    [catalog?.connectionProfiles, form.connectionMode],
  )
  const detailSlugMappings = useMemo(() => parseMappings(form.detailSlugs), [form.detailSlugs])
  const consumer = customer?.consumers.find((item) => item.consumerId === form.consumerId)
  const reviewedDeployment = portfolioQuery.data?.find((item) => item.deploymentId === consumer?.boundDeploymentId)
  const reviewedOffer = offersQuery.data?.find((item) => item.id === reviewedDeployment?.offerId
    && item.fingerprint === reviewedDeployment.offerFingerprint
    && item.entitlement.customerId === customerId && item.entitlement.status === 'ACTIVE')
  const reviewedTarget = reviewedOffer?.targets.find((item) => item.id === consumer?.boundTargetProfileId)
  const reviewedScopeReady = Boolean(consumer?.customerId === customerId && consumer.status === 'ACTIVE'
    && reviewedTarget?.capabilities.includes('APP_CONFIGURE')
    && reviewedTarget.capabilities.includes('AUTH_CONFIGURE') && reviewedTarget.capabilities.includes('DOMAIN_CONFIGURE'))
  const reviewedProfilesQuery = useQuery({
    queryKey: ['ai-workspace-reviewed-profiles', customerId, reviewedOffer?.id, reviewedOffer?.fingerprint,
      reviewedOffer?.settings, reviewedOffer?.workspaceProfiles,
      installations.map((item) => [item.installationId, item.rowVersion])],
    queryFn: () => resolveReviewedWorkspaceProfiles(reviewedOffer!, customerId, installations),
    enabled: customerScopeLocked && editing != null && reviewedScopeReady && Boolean(reviewedOffer)
      && installationsQuery.isSuccess,
    retry: false,
  })
  const reviewedProfiles = reviewedScopeReady ? (reviewedProfilesQuery.data ?? []) : []
  const reviewedProfile = reviewedProfiles.find((item) => item.id === reviewedProfileId) ?? null
  const effectiveDeliveryMode = customerScopeLocked ? reviewedProfile?.configuration.deliveryMode : form.deliveryMode

  useEffect(() => {
    if (!customerScopeLocked || editing == null) return
    setReviewedProfileId((current) => reviewedProfiles.some((item) => item.id === current) ? current
      : editing === 'new' ? reviewedProfiles[0]?.id ?? ''
        : reviewedProfiles.find((item) => item.id === editing.installationId)?.id ?? '')
  }, [customerScopeLocked, editing, reviewedProfilesQuery.data, reviewedScopeReady])

  useEffect(() => {
    if (customerScopeLocked && auth.session?.customerId && customerId !== auth.session.customerId) {
      setCustomerId(auth.session.customerId)
      return
    }
    if (!customerId && customers.length > 0) setCustomerId(customers[0].id)
  }, [auth.session?.customerId, customerId, customerScopeLocked, customers])

  useEffect(() => {
    if (selected) {
      const refreshed = installations.find((item) => item.installationId === selected.installationId)
      setSelected(refreshed ?? null)
    }
  }, [installations]) // eslint-disable-line react-hooks/exhaustive-deps

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] })
  }

  const saveMutation = useMutation({
    mutationFn: ({ payload, operationKey }: { payload: SaveAIWorkspaceInstallationRequest; operationKey: string }) => editing === 'new'
      ? createAIWorkspaceInstallation(customerId, payload, operationKey)
      : updateAIWorkspaceInstallation(customerId, editing!.installationId, payload, operationKey),
    onSuccess: async (installation) => {
      saveCommand.current = null
      setNotice(`AI Workspace ${installation.displayName} saved.`)
      setEditing(null)
      setSelected(installation)
      await invalidate()
    },
  })

  const activateMutation = useMutation({
    mutationFn: ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) =>
      activateAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey),
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} activated.`)
      setSelected(installation)
      await invalidate()
    },
  })

  const disableMutation = useMutation({
    mutationFn: ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) =>
      disableAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey),
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} disabled.`)
      setSelected(installation)
      await invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: ({ installation, operationKey }: { installation: AIWorkspaceInstallationSummary; operationKey: string }) =>
      deleteAIWorkspaceInstallation(installation.customerId, installation.installationId, operationKey),
    onSuccess: async () => {
      setNotice('Draft AI Workspace deleted.')
      setSelected(null)
      await invalidate()
    },
  })

  const openCreate = () => {
    saveCommand.current = null
    const firstProfile = catalog?.connectionProfiles.find((profile) => profile.mode === defaultForm.connectionMode)
    setForm({
      ...defaultForm,
      consumerId: customer?.consumers.find((consumer) => consumer.status === 'ACTIVE')?.consumerId ?? '',
      profileCode: firstProfile?.code ?? defaultForm.profileCode,
    })
    setEditing('new')
    setReviewedProfileId('')
    setConfigurationError(null)
  }

  const openEdit = (installation: AIWorkspaceInstallationSummary) => {
    saveCommand.current = null
    const config = installation.configuration ?? {}
    const dealer = config.dealer ?? {}
    const page = config.page ?? {}
    const capabilities = config.capabilities ?? {}
    const presentation = config.presentation ?? {}
    const theme = config.theme ?? {}
    setForm({
      packCode: installation.experiencePackCode,
      deliveryMode: installation.deliveryMode,
      appSlug: installation.appSlug ?? '',
      customDomains: installation.domains
        .filter((domain) => domain.type === 'CUSTOMER_CUSTOM')
        .map((domain) => domain.hostname)
        .join('\n'),
      interactionPolicy: installation.interactionPolicy,
      authProfileCode: installation.authProfileCode,
      supabaseProjectUrl: String(installation.authConfiguration.projectUrl ?? ''),
      supabasePublishableKey: String(installation.authConfiguration.publishableKey ?? ''),
      supabaseEmailPassword: Array.isArray(installation.authConfiguration.methods)
        && installation.authConfiguration.methods.includes('email-password'),
      supabaseMagicLink: Array.isArray(installation.authConfiguration.methods)
        && installation.authConfiguration.methods.includes('magic-link'),
      displayName: installation.displayName,
      consumerId: installation.consumerId ?? '',
      connectionMode: installation.connectionMode,
      profileCode: installation.connectionProfileCode,
      origins: installation.allowedOrigins.join('\n'),
      dealerId: dealer.id ?? '',
      assistantLabel: dealer.assistantLabel ?? 'LoomAI Workspace',
      sourceMode: dealer.sourceMode ?? 'DEALERSHIP_INVENTORY',
      rootSelector: page.rootSelector ?? 'main',
      contextLabel: page.contextLabel ?? 'Current dealership page',
      primaryColor: theme.primaryColor ?? '#123b35',
      comparison: capabilities.comparison !== false,
      testDrive: capabilities.testDrive === true,
      callback: capabilities.callback === true,
      imageHosts: Array.isArray(presentation.imageHostAllowlist) ? presentation.imageHostAllowlist.join('\n') : '',
      detailBasePath: presentation.detailBasePath ?? '/vehicles/',
      detailSlugs: formatMappings(presentation.detailSlugs),
      shopDomain: String(installation.connectionConfiguration.shopDomain ?? ''),
    })
    setEditing(installation)
    setReviewedProfileId(installation.installationId)
    setConfigurationError(null)
  }

  const saveWorkspace = (payload: SaveAIWorkspaceInstallationRequest) => {
    const identity = JSON.stringify({ customerId, installationId: editing === 'new' ? null : editing?.installationId, payload })
    if (saveCommand.current?.identity !== identity) saveCommand.current = { identity, key: crypto.randomUUID() }
    saveMutation.mutate({ payload, operationKey: saveCommand.current.key })
  }

  const submit = () => {
    setConfigurationError(null)
    if (customerScopeLocked) {
      if (!reviewedOffer || !reviewedScopeReady || !reviewedProfile) {
        setConfigurationError('Select an available configuration from the current reviewed offer before saving.')
        return
      }
      try {
        const payload = reviewedWorkspaceRequest(reviewedOffer, reviewedProfile, {
          consumerId: form.consumerId,
          displayName: form.displayName,
          appSlug: effectiveDeliveryMode === 'standalone' ? form.appSlug.trim() : undefined,
          customDomains: effectiveDeliveryMode === 'standalone' ? lines(form.customDomains) : [],
          allowedOrigins: lines(form.origins),
          ...(editing !== 'new' && editing ? { rowVersion: editing.rowVersion } : {}),
        })
        saveWorkspace(payload)
      } catch (error) {
        setConfigurationError(errorText(error))
      }
      return
    }
    const profile = catalog?.connectionProfiles.find((item) => item.code === form.profileCode)
    const pack = catalog?.experiencePacks.find((item) => item.code === form.packCode)
    const authProfile = catalog?.authenticationProfiles.find((item) => item.code === form.authProfileCode)
    const genericConfiguration = {
      app: { assistantLabel: form.assistantLabel.trim() },
      page: {
        rootSelector: form.rootSelector.trim(),
        contextLabel: form.contextLabel.trim(),
        maxChars: 1800,
        maxPages: 3,
        maxTotalChars: 10000,
      },
      capabilities: { currentPage: true, handover: true },
      theme: {
        primaryColor: form.primaryColor,
        borderRadius: '0.5rem',
        fontFamily: 'Inter, system-ui, sans-serif',
        darkMode: false,
      },
    }
    const dealershipConfiguration = {
      dealer: {
        id: form.dealerId.trim(),
        assistantLabel: form.assistantLabel.trim(),
        sourceMode: form.sourceMode.trim(),
      },
      page: {
        kind: 'auto',
        rootSelector: form.rootSelector.trim(),
        contextLabel: form.contextLabel.trim(),
        maxChars: 1800,
        maxPages: 3,
        maxTotalChars: 10000,
      },
      knowledge: { inventoryVectorSpace: 'dealer-vehicle' },
      capabilities: { comparison: form.comparison, testDrive: form.testDrive, callback: form.callback },
      presentation: {
        detailBasePath: form.detailBasePath.trim(),
        imageHostAllowlist: lines(form.imageHosts),
        detailSlugs: detailSlugMappings.values,
      },
      theme: {
        primaryColor: form.primaryColor,
        borderRadius: '0.5rem',
        fontFamily: 'Inter, system-ui, sans-serif',
        darkMode: false,
      },
    }
    const payload: SaveAIWorkspaceInstallationRequest = {
      consumerId: form.consumerId,
      displayName: form.displayName.trim(),
      experiencePackCode: form.packCode,
      experiencePackVersion: pack?.version ?? '1.0.0',
      connectionMode: form.connectionMode,
      connectionProfileCode: form.profileCode,
      connectionProfileVersion: profile?.version ?? '1.0.0',
      connectionConfiguration: form.connectionMode === 'backend-mediated-private-runtime'
        ? { shopDomain: form.shopDomain.trim() }
        : {},
      allowedOrigins: lines(form.origins),
      configuration: form.packCode === 'dealership' ? dealershipConfiguration : genericConfiguration,
      deliveryMode: form.deliveryMode,
      appSlug: form.deliveryMode === 'standalone' ? form.appSlug.trim() : undefined,
      customDomains: form.deliveryMode === 'standalone' ? lines(form.customDomains) : [],
      interactionPolicy: form.interactionPolicy,
      authProfileCode: form.authProfileCode,
      authProfileVersion: authProfile?.version ?? '1.0.0',
      authConfiguration: form.authProfileCode === 'supabase' ? {
        projectUrl: form.supabaseProjectUrl.trim(),
        publishableKey: form.supabasePublishableKey.trim(),
        methods: [
          ...(form.supabaseEmailPassword ? ['email-password'] : []),
          ...(form.supabaseMagicLink ? ['magic-link'] : []),
        ],
      } : {},
      ...(editing !== 'new' && editing ? { rowVersion: editing.rowVersion } : {}),
    }
    saveWorkspace(payload)
  }

  const operationError = saveMutation.error || activateMutation.error || disableMutation.error || deleteMutation.error

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>AI Workspaces</Typography>
          <Typography color="text.secondary">
            Publish reviewed one-script experiences that resolve each consumer&apos;s assigned runtime.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          {customerScopeLocked ? (
            <Chip label={auth.session?.customerName ?? customer?.name ?? 'My customer account'} variant="outlined" />
          ) : (
            <TextField
              select
              size="small"
              label="Customer"
              value={customerId}
              onChange={(event) => { setCustomerId(event.target.value); setSelected(null) }}
              sx={{ minWidth: 260 }}
            >
              {customers.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
            </TextField>
          )}
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate} disabled={!customer || !catalog?.assetsReady}>
            Create AI App
          </Button>
        </Stack>
      </Stack>

      {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
      {operationError && <Alert severity="error">{errorText(operationError)}</Alert>}
      {catalog && !catalog.assetsReady && <Alert severity="warning">Workspace assets are not release-ready: {catalog.assetStatus}</Alert>}

      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Delivery</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Consumer</TableCell>
              <TableCell>Connection</TableCell>
              <TableCell>Assignment</TableCell>
              <TableCell>Origins</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {installations.map((installation) => (
              <TableRow
                hover
                key={installation.installationId}
                selected={selected?.installationId === installation.installationId}
                onClick={() => setSelected(installation)}
                sx={{ cursor: 'pointer' }}
              >
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{installation.displayName}</Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                    {installation.installationId}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{installation.deliveryMode === 'standalone' ? 'Standalone app' : 'Embedded'}</Typography>
                  <Typography variant="caption" color="text.secondary">{installation.appUrl ?? installation.experiencePackCode}</Typography>
                </TableCell>
                <TableCell><Chip size="small" label={installation.status} color={statusColor(installation.status)} /></TableCell>
                <TableCell>{installation.consumerId ?? 'Unavailable'}</TableCell>
                <TableCell>
                  <Typography variant="body2">{connectionLabel(installation.connectionMode)}</Typography>
                  <Typography variant="caption" color="text.secondary">{installation.connectionProfileCode}</Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    icon={installation.ready ? <CheckCircleRoundedIcon /> : <WarningAmberRoundedIcon />}
                    label={installation.ready ? 'Ready' : 'Blocked'}
                    color={installation.ready ? 'success' : 'warning'}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>{installation.allowedOrigins.length}</TableCell>
                <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                  <Tooltip title="Edit"><span><IconButton size="small" disabled={installation.status === 'ACTIVE'} onClick={() => openEdit(installation)}><EditRoundedIcon /></IconButton></span></Tooltip>
                  {installation.status === 'ACTIVE' ? (
                    <Tooltip title="Disable"><IconButton size="small" onClick={() => disableMutation.mutate({ installation, operationKey: crypto.randomUUID() })}><PauseCircleOutlineRoundedIcon /></IconButton></Tooltip>
                  ) : (
                    <Tooltip title="Activate"><span><IconButton size="small" disabled={!installation.ready} onClick={() => activateMutation.mutate({ installation, operationKey: crypto.randomUUID() })}><PlayCircleOutlineRoundedIcon /></IconButton></span></Tooltip>
                  )}
                  {installation.status === 'DRAFT' && (
                    <Tooltip title="Delete draft"><IconButton size="small" color="error" onClick={() => deleteMutation.mutate({ installation, operationKey: crypto.randomUUID() })}><DeleteOutlineRoundedIcon /></IconButton></Tooltip>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {!installationsQuery.isLoading && installations.length === 0 && (
              <TableRow><TableCell colSpan={8}><Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>No AI Workspace installations for this customer.</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {selected && <WorkspaceDetails installation={selected} refresh={invalidate} />}

      <Dialog open={editing != null} onClose={() => setEditing(null)} maxWidth="md" fullWidth>
        <DialogTitle>{editing === 'new' ? 'Create AI App' : 'Edit AI App'}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <Alert severity="info">
              The installation ID is a public locator. Provider credentials, private assertions and runtime keys remain server-side.
            </Alert>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField fullWidth label="Workspace name" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
              <TextField select fullWidth label="Consumer" value={form.consumerId} onChange={(e) => setForm({ ...form, consumerId: e.target.value })}>
                {(customer?.consumers ?? []).filter((consumer) => consumer.status === 'ACTIVE').map((consumer) => (
                  <MenuItem key={consumer.consumerId} value={consumer.consumerId}>{consumer.displayName} ({consumer.consumerId})</MenuItem>
                ))}
              </TextField>
            </Stack>
            {customerScopeLocked ? (
              <Stack spacing={1.5}>
                <TextField select fullWidth label="Reviewed app configuration" value={reviewedProfile?.id ?? ''}
                  disabled={reviewedProfilesQuery.isFetching || reviewedProfiles.length === 0}
                  onChange={(event) => { setReviewedProfileId(event.target.value); setConfigurationError(null) }}>
                  {reviewedProfiles.map((profile) => (
                    <MenuItem key={profile.id} value={profile.id}>
                      {profile.label} · {profile.configuration.experiencePackCode}
                    </MenuItem>
                  ))}
                </TextField>
                {offersQuery.isFetching || portfolioQuery.isFetching || reviewedProfilesQuery.isFetching ? (
                  <Typography variant="body2" color="text.secondary">Loading reviewed configurations...</Typography>
                ) : offersQuery.error || portfolioQuery.error || reviewedProfilesQuery.error || installationsQuery.error ? (
                  <Alert severity="error">{errorText(offersQuery.error || portfolioQuery.error || reviewedProfilesQuery.error || installationsQuery.error)}</Alert>
                ) : !reviewedScopeReady ? (
                  <Alert severity="warning">The selected assignment needs a current reviewed offer and an entitled deployment target before configuring an app.</Alert>
                ) : reviewedProfiles.length === 0 ? (
                  <Alert severity="warning">
                    No valid reviewed app configuration is available from this offer or your existing apps.
                    LoomAI must publish a configuration matching the approved interaction, theme, and authentication profiles before you can save.
                  </Alert>
                ) : !reviewedProfile ? (
                  <Alert severity="warning">The saved configuration is not included in this offer. Select a reviewed configuration explicitly before saving.</Alert>
                ) : null}
                {reviewedProfile ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Tooltip title={reviewedProfile.interactionProfileId}><Chip label={reviewedProfile.configuration.experiencePackCode} variant="outlined" /></Tooltip>
                    <Chip label={reviewedProfile.configuration.deliveryMode} variant="outlined" />
                    <Tooltip title={reviewedProfile.authProfileId}><Chip label={reviewedProfile.configuration.interactionPolicy} variant="outlined" /></Tooltip>
                    <Tooltip title={reviewedProfile.themeProfileId}><Chip label="Reviewed theme" variant="outlined" /></Tooltip>
                  </Stack>
                ) : null}
              </Stack>
            ) : null}
            {!customerScopeLocked ? (
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField select fullWidth label="Experience pack" value={form.packCode} onChange={(e) => setForm({ ...form, packCode: e.target.value })}>
                  {(catalog?.experiencePacks ?? []).map((pack) => <MenuItem key={pack.code} value={pack.code} disabled={!pack.enabled}>{pack.name}</MenuItem>)}
                </TextField>
                <TextField select fullWidth label="Delivery" value={form.deliveryMode} onChange={(e) => setForm({ ...form, deliveryMode: e.target.value })}>
                  <MenuItem value="standalone">Standalone AI app</MenuItem>
                  <MenuItem value="embedded">Embedded one-script workspace</MenuItem>
                </TextField>
              </Stack>
            ) : null}
            {effectiveDeliveryMode === 'standalone' && (
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField fullWidth label="App slug" helperText="Creates <slug>.apps.loomai.pro" value={form.appSlug} onChange={(e) => setForm({ ...form, appSlug: e.target.value.toLowerCase() })} />
                <TextField fullWidth multiline minRows={2} label="Custom domains" helperText="Optional exact hostnames, one per line" value={form.customDomains} onChange={(e) => setForm({ ...form, customDomains: e.target.value })} />
              </Stack>
            )}
            {(!customerScopeLocked || effectiveDeliveryMode === 'embedded') ? (
              <TextField multiline minRows={2} label="Allowed website origins" helperText="One exact HTTPS origin per line" value={form.origins} onChange={(e) => setForm({ ...form, origins: e.target.value })} />
            ) : null}
            {!customerScopeLocked ? (
              <>
                <Divider />
                <Typography variant="subtitle2">Interaction and identity</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField
                    select fullWidth label="Interaction policy" value={form.interactionPolicy}
                    onChange={(e) => {
                      const policy = e.target.value
                      const authenticated = policy === 'login-required'
                      const isPublic = policy === 'public'
                      const mode = authenticated ? 'public-runtime-authenticated' : 'public-runtime-anonymous'
                      const profile = catalog?.connectionProfiles.find((item) => item.mode === mode && item.enabled)
                      setForm({ ...form, interactionPolicy: policy, authProfileCode: isPublic ? 'none' : 'supabase', connectionMode: mode, profileCode: profile?.code ?? '' })
                    }}
                  >
                    <MenuItem value="public-with-protected-capabilities">Public with protected capabilities</MenuItem>
                    <MenuItem value="login-required">Login required</MenuItem>
                    <MenuItem value="public">Public only</MenuItem>
                  </TextField>
                  <TextField select fullWidth label="Authentication profile" value={form.authProfileCode} disabled={form.interactionPolicy === 'public'} onChange={(e) => setForm({ ...form, authProfileCode: e.target.value })}>
                    {(catalog?.authenticationProfiles ?? []).map((profile) => <MenuItem key={profile.code} value={profile.code} disabled={!profile.enabled || (form.interactionPolicy !== 'public' && profile.code === 'none')}>{profile.name}</MenuItem>)}
                  </TextField>
                </Stack>
                {form.authProfileCode === 'supabase' && (
                  <>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                      <TextField fullWidth label="Supabase project URL" value={form.supabaseProjectUrl} onChange={(e) => setForm({ ...form, supabaseProjectUrl: e.target.value })} />
                      <TextField fullWidth label="Supabase publishable key" value={form.supabasePublishableKey} onChange={(e) => setForm({ ...form, supabasePublishableKey: e.target.value })} />
                    </Stack>
                    <Stack direction="row" spacing={2} flexWrap="wrap">
                      <FormControlLabel control={<Checkbox checked={form.supabaseEmailPassword} onChange={(e) => setForm({ ...form, supabaseEmailPassword: e.target.checked })} />} label="Email and password" />
                      <FormControlLabel control={<Checkbox checked={form.supabaseMagicLink} onChange={(e) => setForm({ ...form, supabaseMagicLink: e.target.checked })} />} label="Magic link" />
                    </Stack>
                  </>
                )}
                <Divider />
                <Typography variant="subtitle2">Connection</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField
                    select fullWidth label="Connection mode" value={form.connectionMode}
                    onChange={(e) => {
                      const mode = e.target.value
                      const profile = catalog?.connectionProfiles.find((item) => item.mode === mode && item.enabled)
                      setForm({ ...form, connectionMode: mode, profileCode: profile?.code ?? '' })
                    }}
                  >
                    <MenuItem value="public-runtime-anonymous">Public anonymous runtime</MenuItem>
                    <MenuItem value="public-runtime-authenticated">Public authenticated runtime</MenuItem>
                    <MenuItem value="backend-mediated-private-runtime">Backend-mediated private runtime</MenuItem>
                  </TextField>
                  <TextField select fullWidth label="Reviewed profile" value={form.profileCode} onChange={(e) => setForm({ ...form, profileCode: e.target.value })}>
                    {compatibleProfiles.map((profile) => (
                      <MenuItem key={profile.code} value={profile.code} disabled={!profile.enabled}>
                        {profile.name}{profile.enabled ? '' : ` - ${profile.availabilityMessage}`}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
                {form.connectionMode === 'backend-mediated-private-runtime' && (
                  <TextField label="Shopify store domain" helperText="Reviewed Shopify Bridge profile only" value={form.shopDomain} onChange={(e) => setForm({ ...form, shopDomain: e.target.value })} />
                )}
                <Divider />
                <Typography variant="subtitle2">{form.packCode === 'dealership' ? 'Dealership experience' : 'Generic experience'}</Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  {form.packCode === 'dealership' && <TextField fullWidth label="Dealership ID" value={form.dealerId} onChange={(e) => setForm({ ...form, dealerId: e.target.value })} />}
                  <TextField fullWidth label="Assistant label" value={form.assistantLabel} onChange={(e) => setForm({ ...form, assistantLabel: e.target.value })} />
                  {form.packCode === 'dealership' && <TextField fullWidth label="Source mode" value={form.sourceMode} onChange={(e) => setForm({ ...form, sourceMode: e.target.value })} />}
                </Stack>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField fullWidth label="Page content selector" value={form.rootSelector} onChange={(e) => setForm({ ...form, rootSelector: e.target.value })} />
                  <TextField fullWidth label="Default context label" value={form.contextLabel} onChange={(e) => setForm({ ...form, contextLabel: e.target.value })} />
                  <TextField fullWidth type="color" label="Primary colour" value={form.primaryColor} onChange={(e) => setForm({ ...form, primaryColor: e.target.value })} InputLabelProps={{ shrink: true }} />
                </Stack>
                {form.packCode === 'dealership' && <Stack direction="row" spacing={2} flexWrap="wrap">
                  <FormControlLabel control={<Checkbox checked={form.comparison} onChange={(e) => setForm({ ...form, comparison: e.target.checked })} />} label="Vehicle comparison" />
                  <FormControlLabel control={<Checkbox checked={form.testDrive} onChange={(e) => setForm({ ...form, testDrive: e.target.checked })} />} label="Test-drive request" />
                  <FormControlLabel control={<Checkbox checked={form.callback} onChange={(e) => setForm({ ...form, callback: e.target.checked })} />} label="Callback request" />
                </Stack>}
                {form.packCode === 'dealership' && <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField fullWidth label="Vehicle detail path" value={form.detailBasePath} onChange={(e) => setForm({ ...form, detailBasePath: e.target.value })} />
                  <TextField fullWidth multiline minRows={2} label="Approved image hosts" helperText="One hostname per line" value={form.imageHosts} onChange={(e) => setForm({ ...form, imageHosts: e.target.value })} />
                </Stack>}
                {form.packCode === 'dealership' && <TextField
                  fullWidth
                  multiline
                  minRows={3}
                  label="Detail page mappings"
                  helperText={detailSlugMappings.error || 'One source record ID=page slug per line. The source record stays provider-owned; the host owns its page route.'}
                  error={Boolean(detailSlugMappings.error)}
                  value={form.detailSlugs}
                  onChange={(e) => setForm({ ...form, detailSlugs: e.target.value })}
                />}
              </>
            ) : null}
            {configurationError ? <Alert severity="error">{configurationError}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={saveMutation.isPending || !form.displayName.trim() || !form.consumerId
            || (effectiveDeliveryMode === 'standalone' && !form.appSlug.trim())
            || (customerScopeLocked
              ? !reviewedProfile || !reviewedScopeReady || offersQuery.isFetching || portfolioQuery.isFetching || reviewedProfilesQuery.isFetching
                || offersQuery.isError || portfolioQuery.isError || reviewedProfilesQuery.isError || installationsQuery.isError
              : !form.profileCode || (form.packCode === 'dealership' && Boolean(detailSlugMappings.error))
                || (form.authProfileCode === 'supabase' && (!form.supabaseProjectUrl.trim() || !form.supabasePublishableKey.trim()
                  || (!form.supabaseEmailPassword && !form.supabaseMagicLink))))}>Save draft</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function WorkspaceDetails({ installation, refresh }: { installation: AIWorkspaceInstallationSummary; refresh: () => Promise<void> }) {
  const [copied, setCopied] = useState(false)
  const verifyMutation = useMutation({
    mutationFn: ({ hostname, operationKey }: { hostname: string; operationKey: string }) =>
      verifyAIWorkspaceDomain(installation.customerId, installation.installationId, hostname, operationKey),
    onSuccess: () => void refresh(),
  })
  const snippet = `<script\n  async\n  src="${getPlatformApiBaseUrl()}/api/public/ai-workspace/install.js"\n  data-installation-id="${installation.installationId}">\n</script>`
  return (
    <Box>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 750 }}>{installation.displayName}</Typography>
          <Typography variant="body2" color="text.secondary">
            Assigned deployment {installation.deploymentId ?? 'unavailable'} · release {installation.releaseId ?? 'unavailable'}
          </Typography>
        </Box>
        <Button startIcon={<RefreshRoundedIcon />} onClick={() => void refresh()}>Refresh readiness</Button>
      </Stack>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={3} alignItems="stretch">
        <Paper variant="outlined" sx={{ p: 2.5, flex: 1, borderRadius: 1 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle2">{installation.deliveryMode === 'standalone' ? 'AI app address' : 'Installation script'}</Typography>
            <Tooltip title={installation.deliveryMode === 'standalone' ? 'Copy app address' : 'Copy installation script'}>
              <IconButton size="small" onClick={() => { void navigator.clipboard.writeText(installation.deliveryMode === 'standalone' ? (installation.appUrl ?? '') : snippet); setCopied(true) }}><ContentCopyRoundedIcon /></IconButton>
            </Tooltip>
          </Stack>
          {installation.deliveryMode === 'standalone' ? (
            <>
              <Typography component="a" href={installation.appUrl ?? undefined} target="_blank" rel="noreferrer" sx={{ fontWeight: 750 }}>{installation.appUrl ?? 'App address unavailable'}</Typography>
              <Stack spacing={1.5} sx={{ mt: 2 }}>
                {installation.domains.map((domain) => (
                  <Paper key={domain.hostname} variant="outlined" sx={{ p: 1.5, borderRadius: 1 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{domain.hostname}</Typography>
                        <Typography variant="caption" color="text.secondary">{domain.type} · {domain.status}</Typography>
                      </Box>
                      {domain.status !== 'VERIFIED' && (
                        <Button size="small" onClick={() => verifyMutation.mutate({ hostname: domain.hostname, operationKey: crypto.randomUUID() })} disabled={verifyMutation.isPending}>
                          {domain.type === 'CUSTOMER_CUSTOM' ? 'Verify domain' : 'Verify route'}
                        </Button>
                      )}
                    </Stack>
                    {domain.type === 'CUSTOMER_CUSTOM' && domain.status !== 'VERIFIED' && (
                      <Stack spacing={0.5} sx={{ mt: 1 }}>
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>
                          TXT {domain.dnsRecordName} = {domain.dnsRecordValue}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>
                          CNAME {domain.routingRecordName} = {domain.routingRecordValue ?? 'shared frontend route pending'}
                        </Typography>
                      </Stack>
                    )}
                    {domain.lastVerificationError && <Typography variant="caption" color="warning.main">{domain.lastVerificationError}</Typography>}
                  </Paper>
                ))}
              </Stack>
            </>
          ) : (
            <>
              <Box component="pre" sx={{ m: 0, p: 2, bgcolor: 'grey.950', color: 'grey.100', borderRadius: 1, overflowX: 'auto', fontSize: 12 }}>{snippet}</Box>
              <Typography variant="caption" color={copied ? 'success.main' : 'text.secondary'}>{copied ? 'Copied.' : 'Use this exact script on an approved origin.'}</Typography>
            </>
          )}
        </Paper>
        <Paper variant="outlined" sx={{ p: 2.5, flex: 1, borderRadius: 1 }}>
          <Typography variant="subtitle2" sx={{ mb: 1.5 }}>Readiness</Typography>
          <Stack spacing={1}>
            {installation.readinessChecks.map((check) => (
              <Stack key={check.code} direction="row" spacing={1} alignItems="flex-start">
                {check.status === 'PASSED' ? <CheckCircleRoundedIcon color="success" fontSize="small" /> : <WarningAmberRoundedIcon color="warning" fontSize="small" />}
                <Box><Typography variant="body2" sx={{ fontWeight: 700 }}>{check.code}</Typography><Typography variant="caption" color="text.secondary">{check.message}</Typography></Box>
              </Stack>
            ))}
          </Stack>
        </Paper>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, fontFamily: 'monospace' }}>
        Assignment revision: {installation.assignmentRevision ?? 'unavailable'}
      </Typography>
    </Box>
  )
}

function lines(value: string) {
  return [...new Set(value.split(/\r?\n|,/).map((item) => item.trim()).filter(Boolean))]
}

function parseMappings(value: string): { values: Record<string, string>; error: string | null } {
  const values: Record<string, string> = {}
  const entries = value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  for (const [index, entry] of entries.entries()) {
    const separator = entry.indexOf('=')
    const key = separator > 0 ? entry.slice(0, separator).trim() : ''
    const mappedValue = separator > 0 ? entry.slice(separator + 1).trim() : ''
    if (!key || !mappedValue) {
      return { values: {}, error: `Line ${index + 1} must use source-record-id=page-slug.` }
    }
    if (key in values) {
      return { values: {}, error: `Line ${index + 1} repeats source record ID ${key}.` }
    }
    values[key] = mappedValue
  }
  return { values, error: null }
}

function formatMappings(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return ''
  return Object.entries(value as Record<string, unknown>)
    .filter((entry): entry is [string, string] => typeof entry[1] === 'string' && Boolean(entry[1].trim()))
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, mappedValue]) => `${key}=${mappedValue}`)
    .join('\n')
}

function connectionLabel(mode: string) {
  if (mode === 'public-runtime-anonymous') return 'Anonymous direct'
  if (mode === 'public-runtime-authenticated') return 'Authenticated direct'
  return 'Private adapter'
}
