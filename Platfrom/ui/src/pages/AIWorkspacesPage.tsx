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
import { useEffect, useMemo, useState } from 'react'
import {
  activateAIWorkspaceInstallation,
  createAIWorkspaceInstallation,
  deleteAIWorkspaceInstallation,
  disableAIWorkspaceInstallation,
  fetchAIWorkspaceCatalog,
  fetchAIWorkspaceInstallations,
  fetchPlatformCustomers,
  getPlatformApiBaseUrl,
  type AIWorkspaceInstallationSummary,
  type PlatformCustomerSummary,
  type SaveAIWorkspaceInstallationRequest,
  updateAIWorkspaceInstallation,
} from '../api/platformApi'

type FormState = {
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
  const queryClient = useQueryClient()
  const [customerId, setCustomerId] = useState('')
  const [selected, setSelected] = useState<AIWorkspaceInstallationSummary | null>(null)
  const [editing, setEditing] = useState<AIWorkspaceInstallationSummary | 'new' | null>(null)
  const [form, setForm] = useState<FormState>(defaultForm)
  const [notice, setNotice] = useState<string | null>(null)

  const customersQuery = useQuery({ queryKey: ['platform-customers'], queryFn: fetchPlatformCustomers })
  const catalogQuery = useQuery({ queryKey: ['ai-workspace-catalog'], queryFn: fetchAIWorkspaceCatalog })
  const installationsQuery = useQuery({
    queryKey: ['ai-workspace-installations', customerId],
    queryFn: () => fetchAIWorkspaceInstallations(customerId),
    enabled: Boolean(customerId),
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

  useEffect(() => {
    if (!customerId && customers.length > 0) setCustomerId(customers[0].id)
  }, [customerId, customers])

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
    mutationFn: (payload: SaveAIWorkspaceInstallationRequest) => editing === 'new'
      ? createAIWorkspaceInstallation(customerId, payload)
      : updateAIWorkspaceInstallation(customerId, editing!.installationId, payload),
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} saved.`)
      setEditing(null)
      setSelected(installation)
      await invalidate()
    },
  })

  const activateMutation = useMutation({
    mutationFn: (installation: AIWorkspaceInstallationSummary) =>
      activateAIWorkspaceInstallation(customerId, installation.installationId),
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} activated.`)
      setSelected(installation)
      await invalidate()
    },
  })

  const disableMutation = useMutation({
    mutationFn: (installation: AIWorkspaceInstallationSummary) =>
      disableAIWorkspaceInstallation(customerId, installation.installationId),
    onSuccess: async (installation) => {
      setNotice(`AI Workspace ${installation.displayName} disabled.`)
      setSelected(installation)
      await invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (installation: AIWorkspaceInstallationSummary) =>
      deleteAIWorkspaceInstallation(customerId, installation.installationId),
    onSuccess: async () => {
      setNotice('Draft AI Workspace deleted.')
      setSelected(null)
      await invalidate()
    },
  })

  const openCreate = () => {
    const firstProfile = catalog?.connectionProfiles.find((profile) => profile.mode === defaultForm.connectionMode)
    setForm({
      ...defaultForm,
      consumerId: customer?.consumers.find((consumer) => consumer.status === 'ACTIVE')?.consumerId ?? '',
      profileCode: firstProfile?.code ?? defaultForm.profileCode,
    })
    setEditing('new')
  }

  const openEdit = (installation: AIWorkspaceInstallationSummary) => {
    const config = installation.configuration ?? {}
    const dealer = config.dealer ?? {}
    const page = config.page ?? {}
    const capabilities = config.capabilities ?? {}
    const presentation = config.presentation ?? {}
    const theme = config.theme ?? {}
    setForm({
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
  }

  const submit = () => {
    const profile = catalog?.connectionProfiles.find((item) => item.code === form.profileCode)
    const payload: SaveAIWorkspaceInstallationRequest = {
      consumerId: form.consumerId,
      displayName: form.displayName.trim(),
      experiencePackCode: 'dealership',
      experiencePackVersion: catalog?.experiencePacks.find((pack) => pack.code === 'dealership')?.version ?? '1.1.0',
      connectionMode: form.connectionMode,
      connectionProfileCode: form.profileCode,
      connectionProfileVersion: profile?.version ?? '1.0.0',
      connectionConfiguration: form.connectionMode === 'backend-mediated-private-runtime'
        ? { shopDomain: form.shopDomain.trim() }
        : {},
      allowedOrigins: lines(form.origins),
      configuration: {
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
        knowledge: {
          inventoryVectorSpace: 'dealer-vehicle',
        },
        capabilities: {
          comparison: form.comparison,
          testDrive: form.testDrive,
          callback: form.callback,
        },
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
      },
      ...(editing !== 'new' && editing ? { rowVersion: editing.rowVersion } : {}),
    }
    saveMutation.mutate(payload)
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
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate} disabled={!customer || !catalog?.assetsReady}>
            New workspace
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
                    <Tooltip title="Disable"><IconButton size="small" onClick={() => disableMutation.mutate(installation)}><PauseCircleOutlineRoundedIcon /></IconButton></Tooltip>
                  ) : (
                    <Tooltip title="Activate"><span><IconButton size="small" disabled={!installation.ready} onClick={() => activateMutation.mutate(installation)}><PlayCircleOutlineRoundedIcon /></IconButton></span></Tooltip>
                  )}
                  {installation.status === 'DRAFT' && (
                    <Tooltip title="Delete draft"><IconButton size="small" color="error" onClick={() => deleteMutation.mutate(installation)}><DeleteOutlineRoundedIcon /></IconButton></Tooltip>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {!installationsQuery.isLoading && installations.length === 0 && (
              <TableRow><TableCell colSpan={7}><Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>No AI Workspace installations for this customer.</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {selected && <WorkspaceDetails installation={selected} refresh={invalidate} />}

      <Dialog open={editing != null} onClose={() => setEditing(null)} maxWidth="md" fullWidth>
        <DialogTitle>{editing === 'new' ? 'Create AI Workspace' : 'Edit AI Workspace'}</DialogTitle>
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
            <TextField multiline minRows={2} label="Allowed website origins" helperText="One exact HTTPS origin per line" value={form.origins} onChange={(e) => setForm({ ...form, origins: e.target.value })} />
            <Divider />
            <Typography variant="subtitle2">Dealership experience</Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField fullWidth label="Dealership ID" value={form.dealerId} onChange={(e) => setForm({ ...form, dealerId: e.target.value })} />
              <TextField fullWidth label="Assistant label" value={form.assistantLabel} onChange={(e) => setForm({ ...form, assistantLabel: e.target.value })} />
              <TextField fullWidth label="Source mode" value={form.sourceMode} onChange={(e) => setForm({ ...form, sourceMode: e.target.value })} />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField fullWidth label="Page content selector" value={form.rootSelector} onChange={(e) => setForm({ ...form, rootSelector: e.target.value })} />
              <TextField fullWidth label="Default context label" value={form.contextLabel} onChange={(e) => setForm({ ...form, contextLabel: e.target.value })} />
              <TextField fullWidth type="color" label="Primary colour" value={form.primaryColor} onChange={(e) => setForm({ ...form, primaryColor: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Stack>
            <Stack direction="row" spacing={2} flexWrap="wrap">
              <FormControlLabel control={<Checkbox checked={form.comparison} onChange={(e) => setForm({ ...form, comparison: e.target.checked })} />} label="Vehicle comparison" />
              <FormControlLabel control={<Checkbox checked={form.testDrive} onChange={(e) => setForm({ ...form, testDrive: e.target.checked })} />} label="Test-drive request" />
              <FormControlLabel control={<Checkbox checked={form.callback} onChange={(e) => setForm({ ...form, callback: e.target.checked })} />} label="Callback request" />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField fullWidth label="Vehicle detail path" value={form.detailBasePath} onChange={(e) => setForm({ ...form, detailBasePath: e.target.value })} />
              <TextField fullWidth multiline minRows={2} label="Approved image hosts" helperText="One hostname per line" value={form.imageHosts} onChange={(e) => setForm({ ...form, imageHosts: e.target.value })} />
            </Stack>
            <TextField
              fullWidth
              multiline
              minRows={3}
              label="Detail page mappings"
              helperText={detailSlugMappings.error || 'One source record ID=page slug per line. The source record stays provider-owned; the host owns its page route.'}
              error={Boolean(detailSlugMappings.error)}
              value={form.detailSlugs}
              onChange={(e) => setForm({ ...form, detailSlugs: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={saveMutation.isPending || !form.displayName.trim() || !form.consumerId || !form.profileCode || Boolean(detailSlugMappings.error)}>Save draft</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function WorkspaceDetails({ installation, refresh }: { installation: AIWorkspaceInstallationSummary; refresh: () => Promise<void> }) {
  const [copied, setCopied] = useState(false)
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
            <Typography variant="subtitle2">Installation script</Typography>
            <Tooltip title="Copy installation script">
              <IconButton size="small" onClick={() => { void navigator.clipboard.writeText(snippet); setCopied(true) }}><ContentCopyRoundedIcon /></IconButton>
            </Tooltip>
          </Stack>
          <Box component="pre" sx={{ m: 0, p: 2, bgcolor: 'grey.950', color: 'grey.100', borderRadius: 1, overflowX: 'auto', fontSize: 12 }}>{snippet}</Box>
          <Typography variant="caption" color={copied ? 'success.main' : 'text.secondary'}>{copied ? 'Copied.' : 'Use this exact script on an approved origin.'}</Typography>
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
