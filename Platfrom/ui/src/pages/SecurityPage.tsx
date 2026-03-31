import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  clearPlatformSecret,
  fetchDeploymentDraft,
  fetchPlatformSecrets,
  fetchRailwayPreflight,
  updateDeploymentGuardrails,
  updatePlatformSecret,
  updateDeploymentDraft,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type SecurityFormState = {
  authzMode: string
  adminApiKeyEnabled: boolean
  connectorApiKeyEnabled: boolean
  authzBaseUrl: string
  corsAllowedOrigins: string
  corsAllowedOriginPatterns: string
  corsAllowCredentials: boolean
}

type GuardrailFormState = {
  approvalRequiredForApply: boolean
  approvalRequiredForDelete: boolean
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function readString(config: Record<string, unknown>, key: string, fallback = ''): string {
  const value = config[key]
  return typeof value === 'string' ? value : fallback
}

function readBoolean(config: Record<string, unknown>, key: string, fallback = false): boolean {
  const value = config[key]
  return typeof value === 'boolean' ? value : fallback
}

function readSecurityForm(config: unknown): SecurityFormState {
  const record = isRecord(config) ? config : {}
  return {
    authzMode: readString(record, 'authzMode', 'REMOTE_HTTP'),
    adminApiKeyEnabled: readBoolean(record, 'adminApiKeyEnabled', true),
    connectorApiKeyEnabled: readBoolean(record, 'connectorApiKeyEnabled', true),
    authzBaseUrl: readString(record, 'authzBaseUrl'),
    corsAllowedOrigins: readString(record, 'corsAllowedOrigins'),
    corsAllowedOriginPatterns: readString(record, 'corsAllowedOriginPatterns'),
    corsAllowCredentials: readBoolean(record, 'corsAllowCredentials', false),
  }
}

function summarizeSecurityConfig(form: SecurityFormState) {
  return {
    authzMode: form.authzMode.trim() || 'Not configured',
    adminApiKeyEnabled: String(form.adminApiKeyEnabled),
    connectorApiKeyEnabled: String(form.connectorApiKeyEnabled),
    authzBaseUrl: form.authzBaseUrl.trim() || 'Not configured',
    corsAllowedOrigins: form.corsAllowedOrigins.trim() || 'Not configured',
    corsAllowedOriginPatterns: form.corsAllowedOriginPatterns.trim() || 'Not configured',
    corsAllowCredentials: String(form.corsAllowCredentials),
    configuredCount: [
      form.authzMode.trim().length > 0,
      true,
      true,
      form.authzBaseUrl.trim().length > 0,
      form.corsAllowedOrigins.trim().length > 0,
      form.corsAllowedOriginPatterns.trim().length > 0,
      true,
    ].filter(Boolean).length,
  }
}

function securityFormsEqual(left: SecurityFormState, right: SecurityFormState): boolean {
  return (
    left.authzMode === right.authzMode &&
    left.adminApiKeyEnabled === right.adminApiKeyEnabled &&
    left.connectorApiKeyEnabled === right.connectorApiKeyEnabled &&
    left.authzBaseUrl.trim() === right.authzBaseUrl.trim() &&
    left.corsAllowedOrigins.trim() === right.corsAllowedOrigins.trim() &&
    left.corsAllowedOriginPatterns.trim() === right.corsAllowedOriginPatterns.trim() &&
    left.corsAllowCredentials === right.corsAllowCredentials
  )
}

export function SecurityPage() {
  const auth = usePlatformAuth()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [secretInputs, setSecretInputs] = useState<Record<string, string>>({})
  const [secretActionNotice, setSecretActionNotice] = useState<string | null>(null)
  const [formState, setFormState] = useState<SecurityFormState>({
    authzMode: 'REMOTE_HTTP',
    adminApiKeyEnabled: true,
    connectorApiKeyEnabled: true,
    authzBaseUrl: '',
    corsAllowedOrigins: '',
    corsAllowedOriginPatterns: '',
    corsAllowCredentials: false,
  })
  const [guardrailState, setGuardrailState] = useState<GuardrailFormState>({
    approvalRequiredForApply: false,
    approvalRequiredForDelete: false,
  })

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (draftQuery.data) {
      setFormState(readSecurityForm(draftQuery.data.securityConfig))
    }
  }, [draftQuery.data])

  useEffect(() => {
    if (!workspace) {
      return
    }
    setGuardrailState({
      approvalRequiredForApply: workspace.deployment.approvalRequiredForApply,
      approvalRequiredForDelete: workspace.deployment.approvalRequiredForDelete,
    })
  }, [workspace])

  const summary = useMemo(() => summarizeSecurityConfig(formState), [formState])
  const savedFormState = useMemo(
    () => readSecurityForm(draftQuery.data?.securityConfig),
    [draftQuery.data?.securityConfig],
  )
  const draftDirty = useMemo(
    () => (draftQuery.data ? !securityFormsEqual(formState, savedFormState) : false),
    [draftQuery.data, formState, savedFormState],
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Security config',
      description: draftDirty
        ? 'Security settings have unsaved browser-only changes until you save the deployment draft.'
        : 'Security editor matches the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId ? editorState : null)
  const canEdit = workspace?.access.canEdit ?? false
  const canAdmin = workspace?.access.canAdmin ?? false
  const canManageSecrets = auth.session?.enabled ? auth.session.canManageSecrets : true
  const canManageGuardrails = canAdmin
  const guardrailsDirty = workspace != null
    && (
      guardrailState.approvalRequiredForApply !== workspace.deployment.approvalRequiredForApply
      || guardrailState.approvalRequiredForDelete !== workspace.deployment.approvalRequiredForDelete
    )

  const platformSecretsQuery = useQuery({
    queryKey: ['platform-secrets'],
    queryFn: fetchPlatformSecrets,
  })

  const railwayPreflightQuery = useQuery({
    queryKey: ['railway-preflight'],
    queryFn: fetchRailwayPreflight,
  })

  useEffect(() => {
    if (!platformSecretsQuery.data) {
      return
    }
    setSecretInputs((previous) => {
      const next = { ...previous }
      for (const secret of platformSecretsQuery.data) {
        if (!(secret.name in next)) {
          next[secret.name] = ''
        }
      }
      return next
    })
  }, [platformSecretsQuery.data])

  const saveMutation = useMutation({
    mutationFn: ({ draftId, securityConfig }: { draftId: string; securityConfig: unknown }) =>
      updateDeploymentDraft(draftId, { securityConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const secretMutation = useMutation({
    mutationFn: ({ name, value }: { name: string; value: string }) => updatePlatformSecret(name, value),
    onSuccess: async (_data, variables) => {
      setSecretActionNotice(
        `${variables.name} updated. Re-apply the current version to push the new secret to deployed services. No draft publish is needed.`,
      )
      setSecretInputs((previous) => ({
        ...previous,
        [variables.name]: '',
      }))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['platform-secrets'] }),
        queryClient.invalidateQueries({ queryKey: ['railway-preflight'] }),
      ])
    },
  })

  const clearSecretMutation = useMutation({
    mutationFn: (name: string) => clearPlatformSecret(name),
    onSuccess: async (data) => {
      setSecretActionNotice(
        `${data.name} DB override cleared. Re-apply the current version if deployed services should pick up the new effective secret source.`,
      )
      setSecretInputs((previous) => ({
        ...previous,
        [data.name]: '',
      }))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['platform-secrets'] }),
        queryClient.invalidateQueries({ queryKey: ['railway-preflight'] }),
      ])
    },
  })

  const guardrailMutation = useMutation({
    mutationFn: () => updateDeploymentGuardrails(selectedDeploymentId, guardrailState),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    const nextConfig = cloneJson(
      isRecord(draftQuery.data.securityConfig) ? draftQuery.data.securityConfig : {},
    )
    nextConfig.authzMode = formState.authzMode.trim()
    nextConfig.adminApiKeyEnabled = formState.adminApiKeyEnabled
    nextConfig.connectorApiKeyEnabled = formState.connectorApiKeyEnabled
    nextConfig.authzBaseUrl = formState.authzBaseUrl.trim()
    nextConfig.corsAllowedOrigins = formState.corsAllowedOrigins.trim()
    nextConfig.corsAllowedOriginPatterns = formState.corsAllowedOriginPatterns.trim()
    nextConfig.corsAllowCredentials = formState.corsAllowCredentials

    saveMutation.mutate({
      draftId: draftQuery.data.id,
      securityConfig: nextConfig,
    })
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Security" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Security config editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Security stays bounded and versioned at the platform layer. This screen edits the draft values
          that control runtime access mode, admin protection, and connector protection.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Security and secret operations now follow the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            {workspace ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={workspace.deployment.name} variant="outlined" />
                <Chip label={workspace.deployment.environment} variant="outlined" />
                <Chip label={workspace.deployment.status} color="primary" />
                <Chip label={workspace.template.name} variant="outlined" />
              </Stack>
            ) : null}

            {draftQuery.data ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={`Draft: ${draftQuery.data.id}`} variant="outlined" />
                <Chip label={`Revision ${draftQuery.data.revisionNumber}`} variant="outlined" />
                <Chip label={draftQuery.data.status} color="primary" />
              </Stack>
            ) : null}
            {!canAdmin && workspace ? (
              <Alert severity="info">
                Guardrail changes require deployment admin access. Draft-backed security config requires deployment editor access.
              </Alert>
            ) : !canEdit && workspace ? (
              <Alert severity="info">
                Draft-backed security config requires deployment editor access or higher.
              </Alert>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Operational guardrails</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                These guardrails protect platform-side operations such as apply and permanent delete. They take effect
                immediately after save and do not require publish or apply.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label="Change type: Immediate platform policy" color="secondary" variant="outlined" />
              <Chip label="Action path: Save only" color="warning" />
            </Stack>

            <Alert severity={guardrailsDirty ? 'warning' : 'info'}>
              {guardrailsDirty
                ? 'Guardrail changes are pending. Save them to protect future apply and delete operations immediately.'
                : 'Guardrails are already enforcing the current deployment operation policy.'}
            </Alert>

            {!canManageGuardrails ? (
              <Alert severity="info">
                Guardrail changes require deployment admin access.
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={guardrailState.approvalRequiredForApply}
                      disabled={!canManageGuardrails}
                      onChange={(event) =>
                        setGuardrailState((previous) => ({
                          ...previous,
                          approvalRequiredForApply: event.target.checked,
                        }))
                      }
                    />
                  }
                  label="Require approval before operators can apply versions"
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={guardrailState.approvalRequiredForDelete}
                      disabled={!canManageGuardrails}
                      onChange={(event) =>
                        setGuardrailState((previous) => ({
                          ...previous,
                          approvalRequiredForDelete: event.target.checked,
                        }))
                      }
                    />
                  }
                  label="Require approval before operators can delete deployments"
                />
              </Grid>
            </Grid>

            {guardrailMutation.isError ? (
              <Alert severity="error">
                {guardrailMutation.error instanceof Error
                  ? guardrailMutation.error.message
                  : 'Failed to update deployment guardrails'}
              </Alert>
            ) : null}
            {guardrailMutation.isSuccess ? (
              <Alert severity="success">Deployment guardrails updated.</Alert>
            ) : null}

            <Stack direction="row" spacing={1.5}>
              <Button
                variant="contained"
                startIcon={<SaveRoundedIcon />}
                disabled={!canManageGuardrails || guardrailMutation.isPending || !guardrailsDirty}
                onClick={() => guardrailMutation.mutate()}
              >
                {guardrailMutation.isPending ? 'Saving...' : 'Save guardrails'}
              </Button>
              <Button
                variant="outlined"
                onClick={() => {
                  if (workspace) {
                    setGuardrailState({
                      approvalRequiredForApply: workspace.deployment.approvalRequiredForApply,
                      approvalRequiredForDelete: workspace.deployment.approvalRequiredForDelete,
                    })
                  }
                }}
              >
                Reset guardrails
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Platform deployment secrets</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                Phase 12 moves required deployment secrets into the platform layer. Railway provisioning now
                resolves required secret placeholders from this store first, with process env as a fallback for
                local development and transition.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip label="Change type: Secrets" color="secondary" variant="outlined" />
              <Chip label="Action path: Apply only" color="warning" />
            </Stack>

            <Alert severity="info">
              Secret changes do not use drafts or publishing. Save the secret here, then re-apply the current
              version when you want Railway deployments to receive the new value.
            </Alert>

            {railwayPreflightQuery.data ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip
                  label={railwayPreflightQuery.data.ready ? 'Railway Preflight Ready' : 'Railway Preflight Blocked'}
                  color={railwayPreflightQuery.data.ready ? 'success' : 'warning'}
                />
                <Chip
                  label={`Mode: ${railwayPreflightQuery.data.mode}`}
                  variant="outlined"
                />
              </Stack>
            ) : null}

            {!canManageSecrets ? (
              <Alert severity="info">
                Secret rotation and clear actions require the <code>PLATFORM_ADMIN</code> role.
              </Alert>
            ) : null}

            {platformSecretsQuery.isLoading ? (
              <Typography color="text.secondary">Loading platform secrets...</Typography>
            ) : platformSecretsQuery.isError ? (
              <Alert severity="error">
                {platformSecretsQuery.error instanceof Error
                  ? platformSecretsQuery.error.message
                  : 'Failed to load platform secrets'}
              </Alert>
            ) : (
              <Grid container spacing={2}>
                {(platformSecretsQuery.data ?? []).map((secret) => {
                  const inputValue = secretInputs[secret.name] ?? ''
                  const isSaving = secretMutation.isPending && secretMutation.variables?.name === secret.name
                  const isClearing =
                    clearSecretMutation.isPending && clearSecretMutation.variables === secret.name

                  return (
                    <Grid item xs={12} md={6} key={secret.name}>
                      <Card variant="outlined" sx={{ height: '100%' }}>
                        <CardContent>
                          <Stack spacing={2}>
                            <Box>
                              <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 1 }}>
                                <Chip
                                  label={secret.present ? 'Present' : 'Missing'}
                                  color={secret.present ? 'success' : 'warning'}
                                  size="small"
                                />
                                <Chip label={secret.source} variant="outlined" size="small" />
                              </Stack>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {secret.displayName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                {secret.description}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                                Key: {secret.name}
                                {secret.updatedAt ? ` • Updated ${new Date(secret.updatedAt).toLocaleString()}` : ''}
                              </Typography>
                            </Box>

                            <TextField
                              label="New secret value"
                              type="password"
                              value={inputValue}
                              disabled={!canManageSecrets}
                              onChange={(event) =>
                                setSecretInputs((previous) => ({
                                  ...previous,
                                  [secret.name]: event.target.value,
                                }))
                              }
                              helperText={
                                secret.source === 'ENV'
                                  ? 'Currently falling back to platform process env. Saving here creates a DB-backed override.'
                                  : 'Existing secret values are never returned to the UI. Enter a new value to rotate.'
                              }
                            />

                            <Stack direction="row" spacing={1.5}>
                              <Button
                                variant="contained"
                                startIcon={<SaveRoundedIcon />}
                                disabled={!canManageSecrets || inputValue.trim().length === 0 || isSaving}
                                onClick={() =>
                                  secretMutation.mutate({
                                    name: secret.name,
                                    value: inputValue,
                                  })
                                }
                              >
                                {isSaving ? 'Saving...' : 'Save secret'}
                              </Button>
                              <Button
                                variant="outlined"
                                disabled={!canManageSecrets || isClearing || secret.source !== 'DATABASE'}
                                onClick={() => clearSecretMutation.mutate(secret.name)}
                              >
                                {isClearing ? 'Clearing...' : 'Clear DB override'}
                              </Button>
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  )
                })}
              </Grid>
            )}

            {secretMutation.isError ? (
              <Alert severity="error">
                {secretMutation.error instanceof Error
                  ? secretMutation.error.message
                  : 'Failed to update platform secret'}
              </Alert>
            ) : null}
            {clearSecretMutation.isError ? (
              <Alert severity="error">
                {clearSecretMutation.error instanceof Error
                  ? clearSecretMutation.error.message
                  : 'Failed to clear platform secret'}
              </Alert>
            ) : null}
            {secretMutation.isSuccess ? (
              <Alert severity="success">Platform secret updated.</Alert>
            ) : null}
            {clearSecretMutation.isSuccess ? (
              <Alert severity="success">Platform secret override cleared.</Alert>
            ) : null}
            {secretActionNotice ? <Alert severity="warning">{secretActionNotice}</Alert> : null}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeploymentId ? (
        <Grid container spacing={2.5}>
          <Grid item xs={12} lg={7}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2.5}>
                  <Box>
                    <Typography variant="h6">Structured security settings</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Remote authz path and connector authz upstream wiring remain on the Actions/Routing page.
                      This page controls the higher-level deployment security posture, including browser CORS.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label="Change type: Versioned config" color="primary" variant="outlined" />
                    <Chip
                      label={draftDirty ? 'Draft changes pending' : 'Draft saved'}
                      color={draftDirty ? 'warning' : 'success'}
                    />
                    <Chip label="Action path: Save Draft -> Publish -> Apply" color="info" />
                  </Stack>

                  <Alert severity={draftDirty ? 'warning' : 'info'}>
                    {draftDirty
                      ? 'These security settings are draft-backed config. Save the draft first, then publish a version, then apply it to Railway.'
                      : 'These settings are versioned config. Any future change here will require Save Draft, Publish, and Apply.'}
                  </Alert>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading security config...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load security config'}
                    </Alert>
                  ) : (
                    <>
                      <Grid container spacing={2}>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Runtime authz mode"
                            value={formState.authzMode}
                            onChange={(event) =>
                              setFormState((previous) => ({
                                ...previous,
                                authzMode: event.target.value,
                              }))
                            }
                          >
                            <MenuItem value="REMOTE_HTTP">REMOTE_HTTP</MenuItem>
                            <MenuItem value="DENY_ALL">DENY_ALL</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Runtime authz base URL"
                            value={formState.authzBaseUrl}
                            onChange={(event) =>
                              setFormState((previous) => ({
                                ...previous,
                                authzBaseUrl: event.target.value,
                              }))
                            }
                            helperText="Optional if runtime should fall back to the connector base URL."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={formState.adminApiKeyEnabled}
                                onChange={(event) =>
                                  setFormState((previous) => ({
                                    ...previous,
                                    adminApiKeyEnabled: event.target.checked,
                                  }))
                                }
                              />
                            }
                            label="Enable runtime admin API key"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={formState.connectorApiKeyEnabled}
                                onChange={(event) =>
                                  setFormState((previous) => ({
                                    ...previous,
                                    connectorApiKeyEnabled: event.target.checked,
                                  }))
                                }
                              />
                            }
                            label="Enable connector API key"
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <TextField
                            fullWidth
                            multiline
                            minRows={2}
                            label="CORS allowed origins"
                            value={formState.corsAllowedOrigins}
                            onChange={(event) =>
                              setFormState((previous) => ({
                                ...previous,
                                corsAllowedOrigins: event.target.value,
                              }))
                            }
                            helperText="Comma-separated exact browser origins, for example https://ai-fabric.dev,http://localhost:8080"
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <TextField
                            fullWidth
                            multiline
                            minRows={2}
                            label="CORS allowed origin patterns"
                            value={formState.corsAllowedOriginPatterns}
                            onChange={(event) =>
                              setFormState((previous) => ({
                                ...previous,
                                corsAllowedOriginPatterns: event.target.value,
                              }))
                            }
                            helperText="Comma-separated Spring origin patterns, for example https://*lovable*"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={formState.corsAllowCredentials}
                                onChange={(event) =>
                                  setFormState((previous) => ({
                                    ...previous,
                                    corsAllowCredentials: event.target.checked,
                                  }))
                                }
                              />
                            }
                            label="Allow browser credentials"
                          />
                        </Grid>
                      </Grid>

                      {saveMutation.isError ? (
                        <Alert severity="error">
                          {saveMutation.error instanceof Error
                            ? saveMutation.error.message
                            : 'Failed to save security config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Security config draft saved.</Alert>
                      ) : null}

                      <Stack direction="row" spacing={1.5}>
                        <Button
                          variant="contained"
                          startIcon={<SaveRoundedIcon />}
                          onClick={handleSave}
                          disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading || !draftDirty}
                        >
                          {saveMutation.isPending ? 'Saving...' : 'Save security config'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setFormState(readSecurityForm(draftQuery.data.securityConfig))
                            }
                          }}
                        >
                          Reset form
                        </Button>
                      </Stack>
                    </>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} lg={5}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">Security summary</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Readable summary of the security settings currently in the form.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${summary.configuredCount}/7 fields configured`} color="primary" />
                    <Chip label={summary.authzMode} variant="outlined" />
                  </Stack>

                  <Divider />

                  <List dense disablePadding>
                    <ListItem disableGutters>
                      <ListItemText primary="Authorization mode" secondary={summary.authzMode} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Admin API key enabled" secondary={summary.adminApiKeyEnabled} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Connector API key enabled" secondary={summary.connectorApiKeyEnabled} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Runtime authz base URL" secondary={summary.authzBaseUrl} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="CORS allowed origins" secondary={summary.corsAllowedOrigins} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText
                        primary="CORS allowed origin patterns"
                        secondary={summary.corsAllowedOriginPatterns}
                      />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText
                        primary="CORS allow credentials"
                        secondary={summary.corsAllowCredentials}
                      />
                    </ListItem>
                  </List>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  )
}
