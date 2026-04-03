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
  clearPlatformSecret,
  fetchDeploymentDraft,
  fetchDeploymentSecurityGovernance,
  fetchDeploymentSecretUsage,
  fetchPlatformSecretAuditEvents,
  fetchPlatformSecrets,
  fetchRailwayPreflight,
  type PlatformAuditEventSummary,
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

function secretStatusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (status === 'READY') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'MISSING' || status === 'BLOCKED') {
    return 'error'
  }
  return 'default'
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
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

  const platformSecretAuditQuery = useQuery({
    queryKey: ['platform-secret-audit-events'],
    queryFn: fetchPlatformSecretAuditEvents,
    enabled: canManageSecrets,
  })

  const railwayPreflightQuery = useQuery({
    queryKey: ['railway-preflight'],
    queryFn: fetchRailwayPreflight,
  })

  const secretUsageQuery = useQuery({
    queryKey: ['deployment-secret-usage', selectedDeploymentId],
    queryFn: () => fetchDeploymentSecretUsage(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const securityGovernanceQuery = useQuery({
    queryKey: ['deployment-security-governance', selectedDeploymentId],
    queryFn: () => fetchDeploymentSecurityGovernance(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
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

  const secretAuditByName = useMemo(() => {
    const grouped = new Map<string, PlatformAuditEventSummary[]>()
    for (const event of platformSecretAuditQuery.data ?? []) {
      const existing = grouped.get(event.targetId) ?? []
      existing.push(event)
      grouped.set(event.targetId, existing)
    }
    return grouped
  }, [platformSecretAuditQuery.data])

  const saveMutation = useMutation({
    mutationFn: ({ draftId, securityConfig }: { draftId: string; securityConfig: unknown }) =>
      updateDeploymentDraft(draftId, { securityConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-secret-usage', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-security-governance', selectedDeploymentId] }),
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
        queryClient.invalidateQueries({ queryKey: ['deployment-secret-usage', selectedDeploymentId] }),
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
        queryClient.invalidateQueries({ queryKey: ['deployment-secret-usage', selectedDeploymentId] }),
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

      {selectedDeploymentId ? (
        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Box>
                <Typography variant="h6">Secret and config separation</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                  This deployment-scoped view shows which managed secrets are referenced by the current draft,
                  whether the platform secret store can satisfy them, and whether any credential was typed directly
                  into versioned config instead of using a placeholder.
                </Typography>
              </Box>

              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label="Change type: Governance view" color="secondary" variant="outlined" />
                {secretUsageQuery.data ? (
                  <>
                    <Chip
                      label={`${secretUsageQuery.data.secrets.length} secret reference${secretUsageQuery.data.secrets.length === 1 ? '' : 's'}`}
                      color="primary"
                    />
                    <Chip
                      label={`${secretUsageQuery.data.missingRequiredCount} required missing`}
                      color={secretUsageQuery.data.missingRequiredCount > 0 ? 'warning' : 'success'}
                    />
                    <Chip
                      label={`${secretUsageQuery.data.literalRiskCount} literal risk${secretUsageQuery.data.literalRiskCount === 1 ? '' : 's'}`}
                      color={secretUsageQuery.data.literalRiskCount > 0 ? 'error' : 'success'}
                    />
                  </>
                ) : null}
              </Stack>

              {secretUsageQuery.isLoading ? (
                <Typography color="text.secondary">Inspecting deployment secret usage...</Typography>
              ) : secretUsageQuery.isError ? (
                <Alert severity="error">
                  {secretUsageQuery.error instanceof Error
                    ? secretUsageQuery.error.message
                    : 'Failed to inspect deployment secret usage'}
                </Alert>
              ) : secretUsageQuery.data ? (
                <>
                  <Alert
                    severity={
                      secretUsageQuery.data.literalRiskCount > 0
                        ? 'error'
                        : secretUsageQuery.data.missingRequiredCount > 0
                          ? 'warning'
                          : 'success'
                    }
                  >
                    {secretUsageQuery.data.summaryMessage}
                  </Alert>

                  <Grid container spacing={2}>
                    <Grid item xs={12} lg={8}>
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={2}>
                            <Box>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Deployment secret usage
                              </Typography>
                              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                Secret values remain masked. This table shows reference posture only.
                              </Typography>
                            </Box>

                            <Table size="small">
                              <TableHead>
                                <TableRow>
                                  <TableCell>Secret</TableCell>
                                  <TableCell>Status</TableCell>
                                  <TableCell>Used by</TableCell>
                                  <TableCell>Config paths</TableCell>
                                </TableRow>
                              </TableHead>
                              <TableBody>
                                {secretUsageQuery.data.secrets.map((secret) => (
                                  <TableRow key={secret.secretName} hover>
                                    <TableCell>
                                      <Stack spacing={0.5}>
                                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                          {secret.displayName}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                          {secret.secretName} • Source {secret.source}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                          {secret.summaryMessage}
                                        </Typography>
                                      </Stack>
                                    </TableCell>
                                    <TableCell>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                        <Chip
                                          label={secret.status}
                                          color={secretStatusColor(secret.status)}
                                          size="small"
                                        />
                                        <Chip
                                          label={secret.required ? 'Required' : 'Optional'}
                                          variant="outlined"
                                          size="small"
                                        />
                                      </Stack>
                                    </TableCell>
                                    <TableCell>
                                      <Stack spacing={0.5}>
                                        {secret.usedByServices.map((service) => (
                                          <Typography key={`${secret.secretName}-${service}`} variant="body2">
                                            {service}
                                          </Typography>
                                        ))}
                                      </Stack>
                                    </TableCell>
                                    <TableCell>
                                      <Stack spacing={0.5}>
                                        {secret.configPaths.map((path) => (
                                          <Typography
                                            key={`${secret.secretName}-${path}`}
                                            variant="caption"
                                            color="text.secondary"
                                          >
                                            {path}
                                          </Typography>
                                        ))}
                                      </Stack>
                                    </TableCell>
                                  </TableRow>
                                ))}
                              </TableBody>
                            </Table>
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>

                    <Grid item xs={12} lg={4}>
                      <Stack spacing={2}>
                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Role-safe boundaries
                              </Typography>
                              <List dense disablePadding>
                                <ListItem disableGutters>
                                  <ListItemText
                                    primary="Draft security config"
                                    secondary="Deployment editors or admins change versioned security posture, then publish and apply it."
                                  />
                                </ListItem>
                                <ListItem disableGutters>
                                  <ListItemText
                                    primary="Platform secret store"
                                    secondary="Only PLATFORM_ADMIN can rotate or clear managed deployment secrets."
                                  />
                                </ListItem>
                                <ListItem disableGutters>
                                  <ListItemText
                                    primary="Operational guardrails"
                                    secondary="Deployment admins control immediate apply and delete approval policy."
                                  />
                                </ListItem>
                              </List>
                            </Stack>
                          </CardContent>
                        </Card>

                        {secretUsageQuery.data.literalRisks.length > 0 ? (
                          <Card variant="outlined" sx={{ borderColor: 'error.main' }}>
                            <CardContent>
                              <Stack spacing={1.5}>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                  Literal credential risks
                                </Typography>
                                {secretUsageQuery.data.literalRisks.map((risk) => (
                                  <Alert key={`${risk.service}-${risk.path}`} severity="error">
                                    <strong>{risk.service}</strong>
                                    <br />
                                    {risk.path}
                                    <br />
                                    {risk.message}
                                  </Alert>
                                ))}
                              </Stack>
                            </CardContent>
                          </Card>
                        ) : (
                          <Alert severity="success">
                            No literal credentials were detected in the current deployment draft.
                          </Alert>
                        )}
                      </Stack>
                    </Grid>
                  </Grid>
                </>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      {selectedDeploymentId ? (
        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Box>
                <Typography variant="h6">Auth, upstream, and CORS governance</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                  These checks translate deployment draft fields into operator guidance for runtime admin exposure,
                  connector ingress, upstream trust boundaries, and browser origins.
                </Typography>
              </Box>

              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label="Change type: Governance policy" color="secondary" variant="outlined" />
                {securityGovernanceQuery.data ? (
                  <>
                    <Chip
                      label={`${securityGovernanceQuery.data.blockedCount} blocked`}
                      color={securityGovernanceQuery.data.blockedCount > 0 ? 'error' : 'success'}
                    />
                    <Chip
                      label={`${securityGovernanceQuery.data.warningCount} warnings`}
                      color={securityGovernanceQuery.data.warningCount > 0 ? 'warning' : 'success'}
                    />
                  </>
                ) : null}
              </Stack>

              {securityGovernanceQuery.isLoading ? (
                <Typography color="text.secondary">Evaluating deployment governance posture...</Typography>
              ) : securityGovernanceQuery.isError ? (
                <Alert severity="error">
                  {securityGovernanceQuery.error instanceof Error
                    ? securityGovernanceQuery.error.message
                    : 'Failed to evaluate deployment governance posture'}
                </Alert>
              ) : securityGovernanceQuery.data ? (
                <>
                  <Alert
                    severity={
                      securityGovernanceQuery.data.blockedCount > 0
                        ? 'error'
                        : securityGovernanceQuery.data.warningCount > 0
                          ? 'warning'
                          : 'success'
                    }
                  >
                    {securityGovernanceQuery.data.summaryMessage}
                  </Alert>

                  <Grid container spacing={2}>
                    {securityGovernanceQuery.data.areas.map((area) => (
                      <Grid item xs={12} md={6} key={area.key}>
                        <Card variant="outlined" sx={{ height: '100%' }}>
                          <CardContent>
                            <Stack spacing={2}>
                              <Box>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
                                  <Chip
                                    label={area.status}
                                    color={secretStatusColor(area.status)}
                                    size="small"
                                  />
                                  <Chip label={`${area.blockedCount} blocked`} variant="outlined" size="small" />
                                  <Chip label={`${area.warningCount} warnings`} variant="outlined" size="small" />
                                </Stack>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                  {area.label}
                                </Typography>
                                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                  {area.summaryMessage}
                                </Typography>
                              </Box>

                              <Table size="small">
                                <TableHead>
                                  <TableRow>
                                    <TableCell>Check</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Current value</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {area.checks.map((check) => (
                                    <TableRow key={`${area.key}-${check.key}`} hover>
                                      <TableCell>
                                        <Stack spacing={0.5}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                            {check.label}
                                          </Typography>
                                          <Typography variant="caption" color="text.secondary">
                                            {check.message}
                                          </Typography>
                                          <Typography variant="caption" color="text.secondary">
                                            {check.guidance}
                                          </Typography>
                                        </Stack>
                                      </TableCell>
                                      <TableCell>
                                        <Chip
                                          label={check.status}
                                          color={secretStatusColor(check.status)}
                                          size="small"
                                        />
                                      </TableCell>
                                      <TableCell>
                                        <Typography variant="body2">{check.valueSummary}</Typography>
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                </>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      ) : null}

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
                  const recentAuditEvents = (secretAuditByName.get(secret.name) ?? []).slice(0, 3)

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

                            {canManageSecrets ? (
                              <Stack spacing={1}>
                                <Divider />
                                <Box>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                    Recent audit history
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    See who changed this secret, what action happened, and when.
                                  </Typography>
                                </Box>
                                {platformSecretAuditQuery.isLoading ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Loading secret audit history...
                                  </Typography>
                                ) : platformSecretAuditQuery.isError ? (
                                  <Alert severity="warning">
                                    {platformSecretAuditQuery.error instanceof Error
                                      ? platformSecretAuditQuery.error.message
                                      : 'Failed to load secret audit history'}
                                  </Alert>
                                ) : recentAuditEvents.length === 0 ? (
                                  <Alert severity="info">No audit events recorded yet for this secret.</Alert>
                                ) : (
                                  <List dense disablePadding>
                                    {recentAuditEvents.map((event) => (
                                      <ListItem key={event.id} disableGutters>
                                        <ListItemText
                                          primary={`${event.action} · ${event.actorId}`}
                                          secondary={`${event.actorRole} · ${formatTimestamp(event.createdAt)}`}
                                        />
                                      </ListItem>
                                    ))}
                                  </List>
                                )}
                              </Stack>
                            ) : null}
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
