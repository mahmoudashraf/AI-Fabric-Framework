import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import ExtensionRoundedIcon from '@mui/icons-material/ExtensionRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  bootstrapMarketplaceTemplatePlugin,
  createDeploymentMarketplaceInstall,
  deleteDeploymentMarketplaceInstall,
  fetchDeploymentMarketplaceImpact,
  fetchDeploymentMarketplaceInstalls,
  fetchDeploymentTemplates,
  fetchMarketplaceCategories,
  fetchMarketplacePlugin,
  fetchMarketplacePlugins,
  resolveDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstall,
  type CreateMarketplaceTemplateBootstrapRequest,
  type DeploymentMarketplaceInstallSummary,
  type MarketplacePluginDetailSummary,
  type MarketplacePluginSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function normalizeText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function prettifyJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return {}
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch {
    throw new Error(`${label} must be valid JSON.`)
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`${label} must be a JSON object.`)
  }
  return parsed as Record<string, unknown>
}

function pluginTypeColor(pluginType: string): 'primary' | 'secondary' | 'success' {
  switch (pluginType) {
    case 'TEMPLATE':
      return 'secondary'
    case 'ACTION':
      return 'primary'
    default:
      return 'success'
  }
}

function categoryLabel(plugin: MarketplacePluginSummary): string {
  switch (plugin.pluginType) {
    case 'TEMPLATE':
      return 'Templates'
    case 'ACTION':
      return 'Actions'
    case 'DATA':
      return 'Data'
    default:
      return plugin.pluginType
  }
}

function contributionList(values: string[]): string {
  return values.length > 0 ? values.join(', ') : '—'
}

function defaultBootstrapName(detail: MarketplacePluginDetailSummary | null): string {
  if (!detail) {
    return 'Marketplace Deployment'
  }
  return `${detail.plugin.displayName} Deployment`
}

export function MarketplacePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const canEdit = workspace?.access.canEdit ?? false
  const [selectedType, setSelectedType] = useState<'ALL' | 'TEMPLATE' | 'ACTION' | 'DATA'>('ALL')
  const [searchText, setSearchText] = useState('')
  const [selectedPluginId, setSelectedPluginId] = useState('')
  const [selectedVersion, setSelectedVersion] = useState('')
  const [configJson, setConfigJson] = useState('{\n}')
  const [secretRefsJson, setSecretRefsJson] = useState('{\n}')
  const [templateName, setTemplateName] = useState('Marketplace Deployment')
  const [templateEnvironment, setTemplateEnvironment] = useState('dev')
  const [templateId, setTemplateId] = useState('')
  const [templateVectorProvisioningMode, setTemplateVectorProvisioningMode] = useState('')
  const [templateCustomerId, setTemplateCustomerId] = useState('')
  const [templateTenantId, setTemplateTenantId] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const pluginsQuery = useQuery({
    queryKey: ['marketplace-plugins'],
    queryFn: fetchMarketplacePlugins,
  })
  const categoriesQuery = useQuery({
    queryKey: ['marketplace-categories'],
    queryFn: fetchMarketplaceCategories,
  })
  const deploymentTemplatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })
  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', selectedDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const impactQuery = useQuery({
    queryKey: ['deployment-marketplace-impact', selectedDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceImpact(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const filteredPlugins = useMemo(() => {
    const needle = normalizeText(searchText)
    return (pluginsQuery.data ?? []).filter((plugin) => {
      if (selectedType !== 'ALL' && plugin.pluginType !== selectedType) {
        return false
      }
      if (needle.length === 0) {
        return true
      }
      const haystack = [
        plugin.displayName,
        plugin.shortDescription,
        plugin.publisherDisplayName,
        plugin.slug,
      ].map(normalizeText).join(' ')
      return haystack.includes(needle)
    })
  }, [pluginsQuery.data, searchText, selectedType])

  useEffect(() => {
    if (filteredPlugins.length === 0) {
      setSelectedPluginId('')
      return
    }
    if (!filteredPlugins.some((plugin) => plugin.id === selectedPluginId)) {
      setSelectedPluginId(filteredPlugins[0].id)
    }
  }, [filteredPlugins, selectedPluginId])

  const pluginDetailQuery = useQuery({
    queryKey: ['marketplace-plugin', selectedPluginId],
    queryFn: () => fetchMarketplacePlugin(selectedPluginId),
    enabled: selectedPluginId.length > 0,
  })

  const selectedPlugin = pluginDetailQuery.data?.plugin ?? null
  const selectedInstall = useMemo(() => {
    if (!selectedPlugin) {
      return null
    }
    return (installsQuery.data ?? []).find((install) => install.pluginId === selectedPlugin.id) ?? null
  }, [installsQuery.data, selectedPlugin])

  useEffect(() => {
    const detail = pluginDetailQuery.data
    if (!detail) {
      return
    }
    const preferredVersion = selectedInstall?.pluginVersion ?? detail.versions[0]?.version ?? ''
    setSelectedVersion((current) => {
      if (detail.versions.some((version) => version.version === current)) {
        return current
      }
      return preferredVersion
    })
    setTemplateName((current) => (current.trim().length > 0 ? current : defaultBootstrapName(detail)))
  }, [pluginDetailQuery.data, selectedInstall?.pluginVersion])

  useEffect(() => {
    if (!selectedInstall) {
      setConfigJson('{\n}')
      setSecretRefsJson('{\n}')
      return
    }
    setConfigJson(prettifyJson(selectedInstall.config))
    setSecretRefsJson(prettifyJson(selectedInstall.secretRefs))
  }, [selectedInstall?.id])

  useEffect(() => {
    if (workspace?.deployment.binding) {
      setTemplateCustomerId((current) => current || workspace.deployment.binding?.customerId || '')
      setTemplateTenantId((current) => current || workspace.deployment.binding?.tenantId || '')
    }
  }, [workspace?.deployment.binding])

  useEffect(() => {
    const templates = deploymentTemplatesQuery.data ?? []
    if (templates.length === 0) {
      return
    }
    setTemplateId((current) => current || templates[0].id)
  }, [deploymentTemplatesQuery.data])

  const invalidateDeploymentState = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-impact', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
    ])
  }

  const installMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPlugin || selectedDeploymentId.length === 0) {
        throw new Error('Select a deployment and a marketplace plugin first.')
      }
      const payload = {
        pluginId: selectedPlugin.id,
        pluginVersion: selectedVersion,
        config: parseJsonObject(configJson, 'Config JSON'),
        secretRefs: parseJsonObject(secretRefsJson, 'Secret refs JSON'),
      }
      if (selectedInstall) {
        return updateDeploymentMarketplaceInstall(selectedDeploymentId, selectedInstall.id, {
          pluginVersion: payload.pluginVersion,
          status: selectedInstall.status,
          config: payload.config,
          secretRefs: payload.secretRefs,
        })
      }
      return createDeploymentMarketplaceInstall(selectedDeploymentId, payload)
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`${install.pluginDisplayName} is now ${selectedInstall ? 'updated' : 'installed'} for ${selectedDeploymentId}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const statusMutation = useMutation({
    mutationFn: async (status: string) => {
      if (!selectedInstall || selectedDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return updateDeploymentMarketplaceInstall(selectedDeploymentId, selectedInstall.id, {
        status,
      })
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`${install.pluginDisplayName} is now ${install.status}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const resolveMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || selectedDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return resolveDeploymentMarketplaceInstall(selectedDeploymentId, selectedInstall.id)
    },
    onSuccess: async () => {
      setFormError(null)
      setSuccessMessage('Marketplace install recompiled into the active draft.')
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || selectedDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      await deleteDeploymentMarketplaceInstall(selectedDeploymentId, selectedInstall.id)
    },
    onSuccess: async () => {
      setFormError(null)
      setSuccessMessage('Marketplace install removed from the deployment.')
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const bootstrapMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPlugin) {
        throw new Error('Select a template plugin first.')
      }
      const payload: CreateMarketplaceTemplateBootstrapRequest = {
        pluginVersion: selectedVersion || undefined,
        name: templateName.trim(),
        environment: templateEnvironment.trim(),
        templateId: templateId.trim() || undefined,
        vectorProvisioningMode: templateVectorProvisioningMode.trim() || undefined,
        customerId: templateCustomerId.trim() || undefined,
        tenantId: templateTenantId.trim() || undefined,
      }
      if (!payload.name) {
        throw new Error('Deployment name is required.')
      }
      if (!payload.environment) {
        throw new Error('Environment is required.')
      }
      return bootstrapMarketplaceTemplatePlugin(selectedPlugin.id, payload)
    },
    onSuccess: async (deployment) => {
      setFormError(null)
      setSuccessMessage(`Template bootstrapped deployment ${deployment.name}.`)
      await queryClient.invalidateQueries({ queryKey: ['deployments'] })
      navigate(`/overview?deploymentId=${encodeURIComponent(deployment.id)}`)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const selectedVersionSummary = pluginDetailQuery.data?.versions.find((version) => version.version === selectedVersion)
    ?? pluginDetailQuery.data?.versions[0]
    ?? null

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.5 }}>
            Marketplace
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75, maxWidth: 900 }}>
            Browse first-party marketplace plugins, bootstrap deployment templates, and compile action or data plugins into the selected deployment draft through the normal publish and apply lifecycle.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip icon={<StorefrontRoundedIcon />} label={`${pluginsQuery.data?.length ?? 0} plugins`} variant="outlined" />
          <Chip icon={<ExtensionRoundedIcon />} label={selectedDeploymentId ? `Deployment: ${selectedDeploymentId}` : 'No deployment selected'} color={selectedDeploymentId ? 'primary' : 'default'} />
          {impactQuery.data ? (
            <Chip icon={<SyncRoundedIcon />} label={`${impactQuery.data.totalInstalls} active install records`} variant="outlined" />
          ) : null}
        </Stack>
      </Stack>

      {formError ? <Alert severity="error">{formError}</Alert> : null}
      {successMessage ? <Alert severity="success">{successMessage}</Alert> : null}
      {!selectedDeploymentId ? (
        <Alert severity="info">
          No deployment is selected. You can still bootstrap template plugins into a new deployment. Installing action or data plugins requires a selected deployment.
        </Alert>
      ) : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Catalog
                </Typography>
                <TextField
                  label="Search plugins"
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  placeholder="Search by name, slug, or publisher"
                />
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label="All"
                    color={selectedType === 'ALL' ? 'primary' : 'default'}
                    onClick={() => setSelectedType('ALL')}
                  />
                  {(categoriesQuery.data ?? []).map((category) => (
                    <Chip
                      key={category.id}
                      label={`${category.label} (${category.pluginCount})`}
                      color={selectedType === category.id.toUpperCase() ? 'primary' : 'default'}
                      onClick={() => setSelectedType(category.id.toUpperCase() as 'TEMPLATE' | 'ACTION' | 'DATA')}
                    />
                  ))}
                </Stack>
                <Divider />
                {pluginsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading marketplace catalog…</Typography>
                ) : filteredPlugins.length === 0 ? (
                  <Alert severity="info">No plugins match the current filter.</Alert>
                ) : (
                  <List disablePadding>
                    {filteredPlugins.map((plugin) => (
                      <ListItemButton
                        key={plugin.id}
                        selected={plugin.id === selectedPluginId}
                        onClick={() => {
                          setSelectedPluginId(plugin.id)
                          setFormError(null)
                          setSuccessMessage(null)
                        }}
                        sx={{ borderRadius: 2, mb: 1, alignItems: 'flex-start' }}
                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{plugin.displayName}</Typography>
                              <Chip size="small" label={categoryLabel(plugin)} color={pluginTypeColor(plugin.pluginType)} />
                            </Stack>
                          }
                          secondary={
                            <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                              <Typography variant="body2" color="text.secondary">
                                {plugin.shortDescription}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {plugin.publisherDisplayName} · latest {plugin.latestVersion ?? '—'}
                              </Typography>
                            </Stack>
                          }
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                {!selectedPlugin ? (
                  <Alert severity="info">Select a marketplace plugin to review its contributions and available versions.</Alert>
                ) : pluginDetailQuery.isLoading ? (
                  <Typography color="text.secondary">Loading plugin details…</Typography>
                ) : (
                  <Stack spacing={2.5}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="h5" sx={{ fontWeight: 800 }}>
                            {selectedPlugin.displayName}
                          </Typography>
                          <Chip label={selectedPlugin.pluginType} color={pluginTypeColor(selectedPlugin.pluginType)} />
                          {selectedInstall ? (
                            <Chip icon={<CheckCircleRoundedIcon />} label={`Installed · ${selectedInstall.status}`} color="success" variant="outlined" />
                          ) : null}
                        </Stack>
                        <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                          {selectedPlugin.shortDescription}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                          Publisher: {selectedPlugin.publisherDisplayName} · Updated {formatTimestamp(selectedPlugin.updatedAt)}
                        </Typography>
                      </Box>
                      <TextField
                        select
                        label="Version"
                        value={selectedVersion}
                        onChange={(event) => setSelectedVersion(event.target.value)}
                        sx={{ minWidth: { xs: '100%', md: 220 } }}
                      >
                        {(pluginDetailQuery.data?.versions ?? []).map((version) => (
                          <MenuItem key={version.id} value={version.version}>
                            {version.version} · {version.releaseChannel}
                          </MenuItem>
                        ))}
                      </TextField>
                    </Stack>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Action contributions
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.actionIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Knowledge sources
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.knowledgeSourceIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Shell modules
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {contributionList(selectedVersionSummary?.contributions.shellModuleIds ?? [])}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Curated template module
                            </Typography>
                            <Typography sx={{ mt: 0.75 }}>
                              {selectedVersionSummary?.contributions.templateCuratedModuleId ?? '—'}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                  </Stack>
                )}
              </CardContent>
            </Card>

            {selectedPlugin?.pluginType === 'TEMPLATE' ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <LaunchRoundedIcon color="secondary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Bootstrap deployment from template plugin
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Template plugins create a new deployment through the standard control-plane create flow. They are not installed into an existing live deployment.
                    </Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          fullWidth
                          label="Deployment name"
                          value={templateName}
                          onChange={(event) => setTemplateName(event.target.value)}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          fullWidth
                          label="Environment"
                          value={templateEnvironment}
                          onChange={(event) => setTemplateEnvironment(event.target.value)}
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          select
                          fullWidth
                          label="Base template"
                          value={templateId}
                          onChange={(event) => setTemplateId(event.target.value)}
                        >
                          {(deploymentTemplatesQuery.data ?? []).map((template) => (
                            <MenuItem key={template.id} value={template.id}>
                              {template.name}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Vector provisioning mode"
                          value={templateVectorProvisioningMode}
                          onChange={(event) => setTemplateVectorProvisioningMode(event.target.value)}
                          placeholder="Optional override"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Customer id"
                          value={templateCustomerId}
                          onChange={(event) => setTemplateCustomerId(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Tenant id"
                          value={templateTenantId}
                          onChange={(event) => setTemplateTenantId(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="contained"
                        startIcon={<DownloadRoundedIcon />}
                        onClick={() => {
                          setFormError(null)
                          setSuccessMessage(null)
                          bootstrapMutation.mutate()
                        }}
                        disabled={bootstrapMutation.isPending || !selectedPlugin}
                      >
                        Bootstrap deployment
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <ExtensionRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Install into selected deployment
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Action and data plugins compile into the selected deployment draft. Save, publish, and apply are still required before they affect the live runtime.
                    </Typography>
                    {!selectedDeploymentId ? (
                      <Alert severity="info">Choose a deployment in the workspace header to install or update this plugin.</Alert>
                    ) : !canEdit ? (
                      <Alert severity="warning">Your current assignment role is read-only for marketplace install changes on this deployment.</Alert>
                    ) : null}
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          fullWidth
                          multiline
                          minRows={8}
                          label="Config JSON"
                          value={configJson}
                          onChange={(event) => setConfigJson(event.target.value)}
                          disabled={!canEdit || !selectedDeploymentId}
                        />
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <TextField
                          fullWidth
                          multiline
                          minRows={8}
                          label="Secret refs JSON"
                          value={secretRefsJson}
                          onChange={(event) => setSecretRefsJson(event.target.value)}
                          disabled={!canEdit || !selectedDeploymentId}
                        />
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {selectedInstall ? (
                        <Chip label={`Live state: ${selectedInstall.liveState}`} variant="outlined" />
                      ) : null}
                      <Button
                        variant="contained"
                        onClick={() => {
                          setFormError(null)
                          setSuccessMessage(null)
                          installMutation.mutate()
                        }}
                        disabled={!canEdit || !selectedDeploymentId || installMutation.isPending}
                      >
                        {selectedInstall ? 'Save install' : 'Install plugin'}
                      </Button>
                      {selectedInstall ? (
                        <>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              statusMutation.mutate(selectedInstall.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
                            }}
                            disabled={!canEdit || statusMutation.isPending}
                          >
                            {selectedInstall.status === 'ENABLED' ? 'Disable' : 'Enable'}
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              resolveMutation.mutate()
                            }}
                            disabled={!selectedDeploymentId || resolveMutation.isPending}
                          >
                            Recompile draft
                          </Button>
                          <Button
                            variant="outlined"
                            color="error"
                            onClick={() => {
                              setFormError(null)
                              setSuccessMessage(null)
                              deleteMutation.mutate()
                            }}
                            disabled={!canEdit || deleteMutation.isPending}
                          >
                            Remove install
                          </Button>
                        </>
                      ) : null}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            )}

            {selectedDeploymentId ? (
              <>
                <Card>
                  <CardContent>
                    <Stack spacing={2}>
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Deployment installs
                      </Typography>
                      {installsQuery.isLoading ? (
                        <Typography color="text.secondary">Loading install records…</Typography>
                      ) : (installsQuery.data ?? []).length === 0 ? (
                        <Alert severity="info">No marketplace installs are recorded for this deployment yet.</Alert>
                      ) : (
                        <List disablePadding>
                          {(installsQuery.data ?? []).map((install) => (
                            <ListItemButton
                              key={install.id}
                              onClick={() => setSelectedPluginId(install.pluginId)}
                              sx={{ borderRadius: 2, mb: 1 }}
                            >
                              <ListItemText
                                primary={
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography sx={{ fontWeight: 700 }}>{install.pluginDisplayName}</Typography>
                                    <Chip size="small" label={install.pluginType} color={pluginTypeColor(install.pluginType)} />
                                    <Chip size="small" label={install.status} variant="outlined" />
                                    <Chip size="small" label={install.liveState} color={install.liveState === 'LIVE' ? 'success' : 'default'} variant="outlined" />
                                  </Stack>
                                }
                                secondary={
                                  <Typography variant="body2" color="text.secondary">
                                    v{install.pluginVersion} · updated {formatTimestamp(install.updatedAt)}
                                  </Typography>
                                }
                              />
                            </ListItemButton>
                          ))}
                        </List>
                      )}
                    </Stack>
                  </CardContent>
                </Card>

                <Card>
                  <CardContent>
                    <Stack spacing={2}>
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Deployment marketplace impact
                      </Typography>
                      {impactQuery.isLoading ? (
                        <Typography color="text.secondary">Loading deployment impact…</Typography>
                      ) : impactQuery.data ? (
                        <>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip label={`${impactQuery.data.totalInstalls} installs`} variant="outlined" />
                            <Chip label={`${impactQuery.data.actionPluginCount} action plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.dataPluginCount} data plugins`} variant="outlined" />
                            <Chip label={`${impactQuery.data.templatePluginCount} template plugins`} variant="outlined" />
                          </Stack>
                          <Grid container spacing={2}>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Action ids
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.actionIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Knowledge sources
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.knowledgeSourceIds)}
                                  </Typography>
                                </CardContent>
                              </Card>
                            </Grid>
                          </Grid>
                          {impactQuery.data.warnings.length > 0 ? (
                            <Alert severity="warning" icon={<WarningAmberRoundedIcon />}>
                              {impactQuery.data.warnings.join(' ')}
                            </Alert>
                          ) : null}
                        </>
                      ) : (
                        <Alert severity="info">Impact preview will appear once a deployment is selected.</Alert>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              </>
            ) : null}
          </Stack>
        </Grid>
      </Grid>
    </Stack>
  )
}
