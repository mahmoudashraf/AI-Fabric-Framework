import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  bootstrapDeploymentFromMarketplaceTemplate,
  deleteDeploymentMarketplacePluginInstall,
  fetchMarketplaceAllowedShellModules,
  fetchDeploymentMarketplaceImpact,
  fetchDeploymentMarketplacePluginInstalls,
  fetchMarketplacePlugins,
  fetchMarketplacePluginVersions,
  installDeploymentMarketplacePlugin,
  previewDeploymentMarketplacePluginInstall,
  type DeploymentMarketplacePluginInstallSummary,
  type InstallDeploymentMarketplacePluginRequest,
  type MarketplaceTemplateBootstrapRequest,
  type MarketplacePluginSummary,
  type MarketplacePluginVersionSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function buildInstallLookup(installs: DeploymentMarketplacePluginInstallSummary[]) {
  return installs.reduce<Record<string, DeploymentMarketplacePluginInstallSummary>>((acc, install) => {
    acc[install.pluginId] = install
    return acc
  }, {})
}

function prettyJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJsonInput(value: string, fallback: unknown): unknown {
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return fallback
  }
  return JSON.parse(trimmed)
}

function pluginVersionLabel(plugin: MarketplacePluginSummary): string {
  return plugin.latestVersion?.version ?? 'No published version'
}

export function MarketplacePage() {
  const queryClient = useQueryClient()
  const workspace = useDeploymentWorkspace()
  const deploymentId = workspace.selectedDeploymentId

  const pluginsQuery = useQuery({
    queryKey: ['marketplace-plugins'],
    queryFn: fetchMarketplacePlugins,
  })

  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', deploymentId],
    queryFn: () => fetchDeploymentMarketplacePluginInstalls(deploymentId),
    enabled: deploymentId.length > 0,
  })

  const impactQuery = useQuery({
    queryKey: ['deployment-marketplace-impact', deploymentId],
    queryFn: () => fetchDeploymentMarketplaceImpact(deploymentId),
    enabled: deploymentId.length > 0,
  })

  const [selectedPluginId, setSelectedPluginId] = useState('')
  const [selectedVersionId, setSelectedVersionId] = useState('')
  const [configJson, setConfigJson] = useState('{}')
  const [secretRefsJson, setSecretRefsJson] = useState('[]')
  const [formError, setFormError] = useState<string | null>(null)
  const [bootstrapName, setBootstrapName] = useState('marketplace-template-deployment')
  const [bootstrapEnvironment, setBootstrapEnvironment] = useState('prod')
  const [bootstrapTemplateId, setBootstrapTemplateId] = useState('ecommerce-assistant')

  const plugins = pluginsQuery.data ?? []
  const selectedPlugin = useMemo(
    () => plugins.find((plugin) => plugin.pluginId === selectedPluginId) ?? null,
    [plugins, selectedPluginId],
  )

  useEffect(() => {
    if (!selectedPlugin && plugins.length > 0) {
      setSelectedPluginId(plugins[0].pluginId)
    }
  }, [plugins, selectedPlugin])

  const versionsQuery = useQuery({
    queryKey: ['marketplace-plugin-versions', selectedPluginId],
    queryFn: () => fetchMarketplacePluginVersions(selectedPluginId),
    enabled: selectedPluginId.length > 0,
  })
  const allowedShellModulesQuery = useQuery({
    queryKey: ['marketplace-allowed-shell-modules'],
    queryFn: fetchMarketplaceAllowedShellModules,
  })

  const versions = versionsQuery.data ?? []
  const selectedVersion = useMemo(
    () => versions.find((version) => version.versionId === selectedVersionId) ?? null,
    [selectedVersionId, versions],
  )

  useEffect(() => {
    if (versions.length === 0) {
      setSelectedVersionId('')
      return
    }
    if (!versions.some((version) => version.versionId === selectedVersionId)) {
      setSelectedVersionId(versions[0].versionId)
    }
  }, [selectedVersionId, versions])

  const previewMutation = useMutation({
    mutationFn: (payload: InstallDeploymentMarketplacePluginRequest) =>
      previewDeploymentMarketplacePluginInstall(deploymentId, payload),
  })

  const installMutation = useMutation({
    mutationFn: (payload: InstallDeploymentMarketplacePluginRequest) =>
      installDeploymentMarketplacePlugin(deploymentId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', deploymentId] })
      void queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-impact', deploymentId] })
      void previewMutation.reset()
    },
  })

  const uninstallMutation = useMutation({
    mutationFn: (installId: string) => deleteDeploymentMarketplacePluginInstall(deploymentId, installId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', deploymentId] })
      void queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-impact', deploymentId] })
    },
  })

  const bootstrapMutation = useMutation({
    mutationFn: (payload: MarketplaceTemplateBootstrapRequest) =>
      bootstrapDeploymentFromMarketplaceTemplate(selectedPluginId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['deployments'] })
    },
  })

  const installs = installsQuery.data ?? []
  const installByPluginId = buildInstallLookup(installs)

  const buildPayload = (): InstallDeploymentMarketplacePluginRequest | null => {
    if (!selectedVersionId) {
      setFormError('Select a plugin version before preview/install.')
      return null
    }

    try {
      const parsedConfig = parseJsonInput(configJson, {})
      const parsedSecretRefs = parseJsonInput(secretRefsJson, [])
      if (typeof parsedConfig !== 'object' || parsedConfig == null || Array.isArray(parsedConfig)) {
        setFormError('Config JSON must be a JSON object.')
        return null
      }
      if (!Array.isArray(parsedSecretRefs)) {
        setFormError('Secret refs JSON must be a JSON array.')
        return null
      }
      setFormError(null)
      return {
        pluginVersionId: selectedVersionId,
        config: parsedConfig,
        secretRefs: parsedSecretRefs,
      }
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Invalid JSON payload.')
      return null
    }
  }

  const handlePreview = () => {
    const payload = buildPayload()
    if (!payload || deploymentId.length === 0) {
      return
    }
    previewMutation.mutate(payload)
  }

  const handleInstall = () => {
    const payload = buildPayload()
    if (!payload || deploymentId.length === 0) {
      return
    }
    installMutation.mutate(payload)
  }

  const handleTemplateBootstrap = () => {
    if (!selectedPlugin || selectedPlugin.pluginType !== 'TEMPLATE') {
      return
    }
    if (!selectedVersionId) {
      setFormError('Select a template plugin version before bootstrap.')
      return
    }
    bootstrapMutation.mutate({
      name: bootstrapName.trim(),
      environment: bootstrapEnvironment.trim(),
      templateId: bootstrapTemplateId.trim(),
      pluginVersionId: selectedVersionId,
    })
  }

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <StorefrontRoundedIcon color="primary" />
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Marketplace
          </Typography>
          <Typography color="text.secondary">
            Review catalog plugins, preview deployment impact, then install through deployment-scoped control-plane flow.
          </Typography>
        </Box>
      </Stack>

      {deploymentId.length === 0 ? <Alert severity="info">Select a deployment to manage marketplace installs.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} lg={7}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Catalog</Typography>
                {pluginsQuery.isLoading ? (
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CircularProgress size={18} />
                    <Typography color="text.secondary">Loading marketplace catalog…</Typography>
                  </Stack>
                ) : null}
                {pluginsQuery.error ? <Alert severity="error">Failed to load marketplace catalog.</Alert> : null}
                {!pluginsQuery.isLoading && !pluginsQuery.error ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Plugin</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Publisher</TableCell>
                        <TableCell>Latest</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {plugins.map((plugin) => {
                        const install = installByPluginId[plugin.pluginId]
                        const selected = plugin.pluginId === selectedPluginId
                        return (
                          <TableRow
                            key={plugin.pluginId}
                            hover
                            selected={selected}
                            onClick={() => setSelectedPluginId(plugin.pluginId)}
                            sx={{ cursor: 'pointer' }}
                          >
                            <TableCell>
                              <Stack spacing={0.4}>
                                <Typography sx={{ fontWeight: 600 }}>{plugin.displayName}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {plugin.shortDescription}
                                </Typography>
                                {install ? <Chip size="small" color="success" label={`Installed (${install.pluginVersion})`} /> : null}
                              </Stack>
                            </TableCell>
                            <TableCell>{plugin.pluginType}</TableCell>
                            <TableCell>{plugin.publisherDisplayName}</TableCell>
                            <TableCell>{pluginVersionLabel(plugin)}</TableCell>
                            <TableCell>{plugin.status}</TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Install workflow</Typography>
                <Stack spacing={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    Allowed shell modules
                  </Typography>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                    {(allowedShellModulesQuery.data ?? []).map((moduleId) => (
                      <Chip key={moduleId} size="small" variant="outlined" label={moduleId} />
                    ))}
                  </Stack>
                </Stack>
                {selectedPlugin ? (
                  <>
                    <Typography variant="body2" color="text.secondary">
                      {selectedPlugin.displayName} ({selectedPlugin.pluginType})
                    </Typography>
                    <TextField
                      select
                      label="Plugin version"
                      value={selectedVersionId}
                      onChange={(event) => setSelectedVersionId(event.target.value)}
                      size="small"
                      disabled={versionsQuery.isLoading || versions.length === 0}
                    >
                      {versions.map((version: MarketplacePluginVersionSummary) => (
                        <MenuItem key={version.versionId} value={version.versionId}>
                          {version.version} · {version.status}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      label="Config JSON"
                      value={configJson}
                      onChange={(event) => setConfigJson(event.target.value)}
                      multiline
                      minRows={5}
                      fullWidth
                    />
                    <TextField
                      label="Secret refs JSON"
                      value={secretRefsJson}
                      onChange={(event) => setSecretRefsJson(event.target.value)}
                      multiline
                      minRows={3}
                      fullWidth
                    />
                    {formError ? <Alert severity="error">{formError}</Alert> : null}
                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="outlined"
                        onClick={handlePreview}
                        disabled={deploymentId.length === 0 || previewMutation.isPending || installMutation.isPending}
                      >
                        Preview impact
                      </Button>
                      <Button
                        variant="contained"
                        onClick={handleInstall}
                        disabled={deploymentId.length === 0 || installMutation.isPending || previewMutation.isPending}
                      >
                        Install plugin
                      </Button>
                    </Stack>
                    {selectedPlugin.pluginType === 'TEMPLATE' ? (
                      <Stack spacing={1.25} sx={{ pt: 1 }}>
                        <Divider />
                        <Typography variant="subtitle2">Template bootstrap</Typography>
                        <TextField
                          label="Deployment name"
                          value={bootstrapName}
                          onChange={(event) => setBootstrapName(event.target.value)}
                          size="small"
                        />
                        <TextField
                          label="Environment"
                          value={bootstrapEnvironment}
                          onChange={(event) => setBootstrapEnvironment(event.target.value)}
                          size="small"
                        />
                        <TextField
                          label="Base template id"
                          value={bootstrapTemplateId}
                          onChange={(event) => setBootstrapTemplateId(event.target.value)}
                          size="small"
                        />
                        <Button
                          variant="outlined"
                          onClick={handleTemplateBootstrap}
                          disabled={bootstrapMutation.isPending}
                        >
                          Bootstrap deployment from template plugin
                        </Button>
                      </Stack>
                    ) : null}
                    {installByPluginId[selectedPlugin.pluginId] ? (
                      <Button
                        color="warning"
                        onClick={() => uninstallMutation.mutate(installByPluginId[selectedPlugin.pluginId].installId)}
                        disabled={deploymentId.length === 0 || uninstallMutation.isPending}
                      >
                        Uninstall current plugin
                      </Button>
                    ) : null}
                  </>
                ) : (
                  <Alert severity="info">Select a plugin from catalog to continue.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Deployment installs</Typography>
                {installsQuery.isLoading ? <CircularProgress size={18} /> : null}
                {installsQuery.error ? <Alert severity="error">Failed to load deployment installs.</Alert> : null}
                {!installsQuery.isLoading && installs.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No marketplace plugins are currently installed for this deployment.
                  </Typography>
                ) : null}
                {installs.length > 0 ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Plugin</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {installs.map((install) => (
                        <TableRow key={install.installId}>
                          <TableCell>{install.pluginDisplayName}</TableCell>
                          <TableCell>{install.pluginType}</TableCell>
                          <TableCell>{install.pluginVersion}</TableCell>
                          <TableCell>{install.status}</TableCell>
                          <TableCell align="right">
                            <Button
                              color="warning"
                              size="small"
                              onClick={() => uninstallMutation.mutate(install.installId)}
                              disabled={uninstallMutation.isPending}
                            >
                              Uninstall
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6">Impact snapshot</Typography>
                {impactQuery.isLoading ? <CircularProgress size={18} /> : null}
                {impactQuery.error ? <Alert severity="error">Failed to load deployment impact snapshot.</Alert> : null}
                {impactQuery.data ? (
                  <Stack spacing={1}>
                    <Typography variant="body2">
                      Installed plugins: <strong>{impactQuery.data.installedPluginCount}</strong>
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {impactQuery.data.affectedConfigKeys.map((key) => (
                        <Chip key={key} size="small" label={key} />
                      ))}
                    </Stack>
                  </Stack>
                ) : null}

                {previewMutation.data ? (
                  <>
                    <Divider />
                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                      Preview result ({previewMutation.data.installMode})
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {previewMutation.data.pluginDisplayName} v{previewMutation.data.pluginVersion}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {previewMutation.data.affectedConfigKeys.map((key) => (
                        <Chip key={key} size="small" color="primary" label={key} />
                      ))}
                    </Stack>
                    <TextField
                      label="Resolved shell defaults"
                      value={prettyJson(previewMutation.data.shellDefaults)}
                      multiline
                      minRows={4}
                      fullWidth
                      InputProps={{ readOnly: true }}
                    />
                  </>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {installMutation.error ? <Alert severity="error">Install failed. Validate version, config, and permissions.</Alert> : null}
      {previewMutation.error ? <Alert severity="error">Preview failed. Check config/secret refs payload.</Alert> : null}
      {uninstallMutation.error ? <Alert severity="error">Uninstall failed.</Alert> : null}
      {bootstrapMutation.error ? <Alert severity="error">Template bootstrap failed.</Alert> : null}
      {bootstrapMutation.data ? (
        <Alert severity="success">
          Template bootstrap created deployment <strong>{bootstrapMutation.data.name}</strong> ({bootstrapMutation.data.id}).
        </Alert>
      ) : null}
      {selectedVersion ? (
        <Alert severity="info">Selected version channel: {selectedVersion.releaseChannel}</Alert>
      ) : null}
    </Stack>
  )
}
