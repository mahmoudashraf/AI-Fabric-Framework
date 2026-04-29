import BlockRoundedIcon from '@mui/icons-material/BlockRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import TuneRoundedIcon from '@mui/icons-material/TuneRounded'
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
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchShopifyPackageProfiles,
  type ShopifyCompanionPackageProfileSummary,
  type UpsertShopifyCompanionPackageProfileRequest,
  updateShopifyPackageProfileStatus,
  upsertShopifyPackageProfile,
} from '../api/platformApi'

type ProfileFormState = UpsertShopifyCompanionPackageProfileRequest & {
  profileKey: string
  detailsJson: string
  reason: string
}

const statusOptions = ['ACTIVE', 'DRAFT', 'DISABLED']
const packageOptions = ['FREE', 'STARTER', 'ELITE', 'ENTERPRISE']
const tierOptions = ['FREE', 'STARTER', 'ELITE', 'ENTERPRISE']
const costPostureOptions = ['LOW', 'STANDARD', 'QUALITY', 'HIGH', 'ENTERPRISE']
const vectorProvisioningModes = ['EXTERNAL_EXISTING', 'PLATFORM_MANAGED', 'LOCAL_MANAGED']
const vectorStoragePostures = ['SHARED', 'DEDICATED', 'EMBEDDED']

const emptyProfileForm: ProfileFormState = {
  profileKey: '',
  packageKey: 'STARTER',
  tierKey: 'STARTER',
  runtimeProfileKey: 'BALANCED',
  vectorProfileKey: 'QDRANT_SHARED',
  displayName: '',
  description: '',
  costPosture: 'STANDARD',
  templatePluginId: 'mkp-template-shopify-companion',
  templatePluginVersion: '',
  deploymentTemplateId: 'dev-openai-qdrant',
  inferencePluginId: 'mkp-inference-shared-embeddings',
  vectorStrategy: 'qdrant',
  vectorProvisioningMode: 'EXTERNAL_EXISTING',
  vectorStoragePosture: 'SHARED',
  verificationPackId: 'starter-launch-readiness',
  status: 'DRAFT',
  detailsJson: '{}',
  reason: '',
}

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'default' | 'error' {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE':
      return 'success'
    case 'DRAFT':
      return 'warning'
    case 'DISABLED':
      return 'default'
    default:
      return 'error'
  }
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function profileToForm(profile: ShopifyCompanionPackageProfileSummary): ProfileFormState {
  return {
    profileKey: profile.profileKey,
    packageKey: profile.packageKey,
    tierKey: profile.tierKey,
    runtimeProfileKey: profile.runtimeProfileKey,
    vectorProfileKey: profile.vectorProfileKey,
    displayName: profile.displayName,
    description: profile.description ?? '',
    costPosture: profile.costPosture ?? 'STANDARD',
    templatePluginId: profile.templatePluginId ?? '',
    templatePluginVersion: profile.templatePluginVersion ?? '',
    deploymentTemplateId: profile.deploymentTemplateId ?? '',
    inferencePluginId: profile.inferencePluginId ?? '',
    vectorStrategy: profile.vectorStrategy ?? 'qdrant',
    vectorProvisioningMode: profile.vectorProvisioningMode ?? 'EXTERNAL_EXISTING',
    vectorStoragePosture: profile.vectorStoragePosture ?? 'SHARED',
    verificationPackId: profile.verificationPackId ?? '',
    status: profile.status,
    detailsJson: profile.detailsJson ?? '{}',
    reason: '',
  }
}

function buildPayload(form: ProfileFormState): UpsertShopifyCompanionPackageProfileRequest {
  return {
    packageKey: form.packageKey,
    tierKey: form.tierKey,
    runtimeProfileKey: form.runtimeProfileKey,
    vectorProfileKey: form.vectorProfileKey,
    displayName: form.displayName,
    description: form.description,
    costPosture: form.costPosture,
    templatePluginId: form.templatePluginId,
    templatePluginVersion: form.templatePluginVersion,
    deploymentTemplateId: form.deploymentTemplateId,
    inferencePluginId: form.inferencePluginId,
    vectorStrategy: form.vectorStrategy,
    vectorProvisioningMode: form.vectorProvisioningMode,
    vectorStoragePosture: form.vectorStoragePosture,
    verificationPackId: form.verificationPackId,
    status: form.status,
    detailsJson: form.detailsJson,
    reason: form.reason,
  }
}

function validateDetailsJson(value: string): string | null {
  if (!value.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(value)
    if (parsed == null || Array.isArray(parsed) || typeof parsed !== 'object') {
      return 'Details must be a JSON object.'
    }
    return null
  } catch {
    return 'Details must be valid JSON.'
  }
}

export function ShopifyPackageProfilesPage() {
  const queryClient = useQueryClient()
  const [selectedProfileKey, setSelectedProfileKey] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [packageFilter, setPackageFilter] = useState<string>('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const [form, setForm] = useState<ProfileFormState>(emptyProfileForm)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const profilesQuery = useQuery({
    queryKey: ['shopify-package-profiles', 'all'],
    queryFn: () => fetchShopifyPackageProfiles(false),
  })

  const profiles = profilesQuery.data ?? []
  const selectedProfile = profiles.find((profile) => profile.profileKey === selectedProfileKey) ?? null

  useEffect(() => {
    if (!selectedProfileKey && profiles.length > 0) {
      setSelectedProfileKey(profiles[0].profileKey)
    }
  }, [profiles, selectedProfileKey])

  useEffect(() => {
    if (selectedProfile) {
      setForm(profileToForm(selectedProfile))
    }
  }, [selectedProfile])

  const detailsJsonError = useMemo(() => validateDetailsJson(form.detailsJson), [form.detailsJson])

  const filteredProfiles = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase()
    return profiles.filter((profile) => {
      const statusMatches = statusFilter === 'ALL' || profile.status === statusFilter
      const packageMatches = packageFilter === 'ALL' || profile.packageKey === packageFilter
      const textMatches = !normalizedSearch
        || [profile.profileKey, profile.displayName, profile.inferencePluginId, profile.deploymentTemplateId, profile.verificationPackId]
          .filter(Boolean)
          .some((value) => value!.toLowerCase().includes(normalizedSearch))
      return statusMatches && packageMatches && textMatches
    })
  }, [packageFilter, profiles, searchTerm, statusFilter])

  const activeProfiles = profiles.filter((profile) => profile.status === 'ACTIVE')
  const selectedActiveConflict = form.status === 'ACTIVE'
    ? activeProfiles.find(
        (profile) =>
          profile.profileKey !== form.profileKey
          && profile.packageKey === form.packageKey
          && profile.tierKey === form.tierKey,
      )
    : null

  const upsertMutation = useMutation({
    mutationFn: (payload: ProfileFormState) => upsertShopifyPackageProfile(payload.profileKey, buildPayload(payload)),
    onSuccess: async (profile) => {
      setMessage({ type: 'success', text: `Saved ${profile.profileKey}.` })
      setSelectedProfileKey(profile.profileKey)
      await queryClient.invalidateQueries({ queryKey: ['shopify-package-profiles'] })
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to save package profile.' })
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ profileKey, status }: { profileKey: string; status: string }) =>
      updateShopifyPackageProfileStatus(profileKey, {
        status,
        reason: form.reason || `Set profile ${status.toLowerCase()}`,
      }),
    onSuccess: async (profile) => {
      setMessage({ type: 'success', text: `${profile.profileKey} is now ${profile.status}.` })
      setSelectedProfileKey(profile.profileKey)
      await queryClient.invalidateQueries({ queryKey: ['shopify-package-profiles'] })
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to update profile status.' })
    },
  })

  const updateForm = (patch: Partial<ProfileFormState>) => setForm((current) => ({ ...current, ...patch }))

  const startNewProfile = () => {
    setSelectedProfileKey('')
    setForm({ ...emptyProfileForm, profileKey: 'NEW_PROFILE', displayName: 'New profile', reason: 'Create package profile' })
  }

  const duplicateSelectedProfile = () => {
    const source = selectedProfile ?? profiles.find((profile) => profile.profileKey === form.profileKey)
    if (!source) {
      return
    }
    setSelectedProfileKey('')
    setForm({
      ...profileToForm(source),
      profileKey: `${source.profileKey}_COPY`,
      displayName: `${source.displayName} copy`,
      status: 'DRAFT',
      reason: `Duplicate ${source.profileKey}`,
    })
  }

  const resetForm = () => {
    setForm(selectedProfile ? profileToForm(selectedProfile) : emptyProfileForm)
  }

  const canSave = form.profileKey.trim().length > 0
    && form.displayName.trim().length > 0
    && form.templatePluginId.trim().length > 0
    && form.deploymentTemplateId.trim().length > 0
    && form.inferencePluginId.trim().length > 0
    && form.verificationPackId.trim().length > 0
    && !detailsJsonError

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', lg: 'center' }}>
        <div>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Shopify Profiles
          </Typography>
          <Typography color="text.secondary">
            Package, runtime, inference, vector, template, and verification catalog for Shopify Companion provisioning.
          </Typography>
        </div>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button component={Link} to="/shopify-stores" variant="outlined" startIcon={<StorefrontRoundedIcon />}>
            Shopify stores
          </Button>
          <Button component={Link} to="/marketplace" variant="outlined">
            Marketplace
          </Button>
          <Button component={Link} to="/inference-services" variant="outlined">
            Inference
          </Button>
          <Button component={Link} to="/verification-ops" variant="outlined" startIcon={<FactCheckRoundedIcon />}>
            Verification
          </Button>
          <Button variant="contained" startIcon={<TuneRoundedIcon />} onClick={startNewProfile}>
            New profile
          </Button>
        </Stack>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}

      <Grid container spacing={2}>
        {[
          ['Total profiles', profiles.length],
          ['Active', activeProfiles.length],
          ['Draft', profiles.filter((profile) => profile.status === 'DRAFT').length],
          ['Disabled', profiles.filter((profile) => profile.status === 'DISABLED').length],
        ].map(([label, value]) => (
          <Grid item xs={12} sm={6} md={3} key={label}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="caption" color="text.secondary">
                  {label}
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {value}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Alert severity="info">
        Active catalog edits apply to future provisioning. Existing stores pick up a new package profile through explicit store reconciliation.
      </Alert>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={4}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                  <Typography sx={{ fontWeight: 700 }}>Catalog</Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<RefreshRoundedIcon />}
                    onClick={() => profilesQuery.refetch()}
                    disabled={profilesQuery.isFetching}
                  >
                    Refresh
                  </Button>
                </Stack>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                  <TextField
                    size="small"
                    label="Search"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                    fullWidth
                  />
                  <TextField
                    size="small"
                    select
                    label="Status"
                    value={statusFilter}
                    onChange={(event) => setStatusFilter(event.target.value)}
                    sx={{ minWidth: 128 }}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    {statusOptions.map((status) => (
                      <MenuItem key={status} value={status}>
                        {status}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    size="small"
                    select
                    label="Package"
                    value={packageFilter}
                    onChange={(event) => setPackageFilter(event.target.value)}
                    sx={{ minWidth: 140 }}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    {packageOptions.map((option) => (
                      <MenuItem key={option} value={option}>
                        {option}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
                {profilesQuery.isError ? (
                  <Alert severity="error">
                    {profilesQuery.error instanceof Error ? profilesQuery.error.message : 'Failed to load package profiles.'}
                  </Alert>
                ) : profilesQuery.isLoading ? (
                  <Typography variant="body2" color="text.secondary">
                    Loading package profiles...
                  </Typography>
                ) : filteredProfiles.length === 0 ? (
                  <Alert severity="info">No profiles match the current filters.</Alert>
                ) : (
                  <List disablePadding>
                    {filteredProfiles.map((profile) => (
                      <ListItemButton
                        key={profile.profileKey}
                        selected={profile.profileKey === selectedProfileKey}
                        onClick={() => setSelectedProfileKey(profile.profileKey)}
                        sx={{ borderRadius: 2, mb: 0.5 }}
                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{profile.profileKey}</Typography>
                              <Chip size="small" label={profile.status} color={statusColor(profile.status)} />
                            </Stack>
                          }
                          secondary={`${profile.packageKey}/${profile.tierKey} · ${profile.runtimeProfileKey} · ${profile.vectorProfileKey}`}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2.5}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
                  <Stack spacing={0.5}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                      <Typography variant="h5" sx={{ fontWeight: 700 }}>
                        {form.profileKey || 'Profile draft'}
                      </Typography>
                      <Chip size="small" label={form.status} color={statusColor(form.status)} />
                      {selectedActiveConflict ? <Chip size="small" color="error" label={`Conflicts with ${selectedActiveConflict.profileKey}`} /> : null}
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Created {formatTimestamp(selectedProfile?.createdAt)} · Updated {formatTimestamp(selectedProfile?.updatedAt)}
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Tooltip title="Duplicate selected profile as draft">
                      <span>
                        <Button
                          variant="outlined"
                          startIcon={<ContentCopyRoundedIcon />}
                          onClick={duplicateSelectedProfile}
                          disabled={!selectedProfile}
                        >
                          Duplicate
                        </Button>
                      </span>
                    </Tooltip>
                    <Button variant="outlined" onClick={resetForm}>
                      Reset
                    </Button>
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<PlayCircleOutlineRoundedIcon />}
                      onClick={() => statusMutation.mutate({ profileKey: form.profileKey, status: 'ACTIVE' })}
                      disabled={!selectedProfile || form.status === 'ACTIVE' || statusMutation.isPending}
                    >
                      Activate
                    </Button>
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<BlockRoundedIcon />}
                      onClick={() => statusMutation.mutate({ profileKey: form.profileKey, status: 'DISABLED' })}
                      disabled={!selectedProfile || form.status === 'DISABLED' || statusMutation.isPending}
                    >
                      Disable
                    </Button>
                    <Button
                      variant="contained"
                      startIcon={<SaveRoundedIcon />}
                      onClick={() => upsertMutation.mutate(form)}
                      disabled={!canSave || Boolean(selectedActiveConflict) || upsertMutation.isPending}
                    >
                      Save
                    </Button>
                  </Stack>
                </Stack>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <TextField label="Profile key" value={form.profileKey} onChange={(event) => updateForm({ profileKey: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField label="Display name" value={form.displayName} onChange={(event) => updateForm({ displayName: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField select label="Status" value={form.status} onChange={(event) => updateForm({ status: event.target.value })} fullWidth>
                      {statusOptions.map((status) => (
                        <MenuItem key={status} value={status}>
                          {status}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField select label="Package" value={form.packageKey} onChange={(event) => updateForm({ packageKey: event.target.value })} fullWidth>
                      {packageOptions.map((option) => (
                        <MenuItem key={option} value={option}>
                          {option}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField select label="Tier" value={form.tierKey} onChange={(event) => updateForm({ tierKey: event.target.value })} fullWidth>
                      {tierOptions.map((option) => (
                        <MenuItem key={option} value={option}>
                          {option}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField select label="Cost posture" value={form.costPosture} onChange={(event) => updateForm({ costPosture: event.target.value })} fullWidth>
                      {costPostureOptions.map((option) => (
                        <MenuItem key={option} value={option}>
                          {option}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField label="Runtime profile key" value={form.runtimeProfileKey} onChange={(event) => updateForm({ runtimeProfileKey: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField label="Vector profile key" value={form.vectorProfileKey} onChange={(event) => updateForm({ vectorProfileKey: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      label="Description"
                      value={form.description}
                      onChange={(event) => updateForm({ description: event.target.value })}
                      minRows={2}
                      multiline
                      fullWidth
                    />
                  </Grid>
                </Grid>

                <Divider />

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField label="Template plugin ID" value={form.templatePluginId} onChange={(event) => updateForm({ templatePluginId: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField
                      label="Template version"
                      value={form.templatePluginVersion ?? ''}
                      onChange={(event) => updateForm({ templatePluginVersion: event.target.value })}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField label="Deployment template" value={form.deploymentTemplateId} onChange={(event) => updateForm({ deploymentTemplateId: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField label="Inference plugin ID" value={form.inferencePluginId} onChange={(event) => updateForm({ inferencePluginId: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField label="Verification pack ID" value={form.verificationPackId} onChange={(event) => updateForm({ verificationPackId: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField label="Vector strategy" value={form.vectorStrategy} onChange={(event) => updateForm({ vectorStrategy: event.target.value })} fullWidth />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      select
                      label="Vector provisioning"
                      value={form.vectorProvisioningMode}
                      onChange={(event) => updateForm({ vectorProvisioningMode: event.target.value })}
                      fullWidth
                    >
                      {vectorProvisioningModes.map((option) => (
                        <MenuItem key={option} value={option}>
                          {option}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <TextField
                      select
                      label="Vector storage"
                      value={form.vectorStoragePosture}
                      onChange={(event) => updateForm({ vectorStoragePosture: event.target.value })}
                      fullWidth
                    >
                      {vectorStoragePostures.map((option) => (
                        <MenuItem key={option} value={option}>
                          {option}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                </Grid>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={8}>
                    <TextField
                      label="Details JSON"
                      value={form.detailsJson}
                      onChange={(event) => updateForm({ detailsJson: event.target.value })}
                      error={Boolean(detailsJsonError)}
                      helperText={detailsJsonError ?? ' '}
                      minRows={6}
                      multiline
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Stack spacing={1.5}>
                      <TextField
                        label="Audit reason"
                        value={form.reason}
                        onChange={(event) => updateForm({ reason: event.target.value })}
                        minRows={3}
                        multiline
                        fullWidth
                      />
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={1}>
                            <Typography sx={{ fontWeight: 700 }}>Resolution preview</Typography>
                            {[
                              ['Package', `${form.packageKey} / ${form.tierKey}`],
                              ['Runtime', form.runtimeProfileKey],
                              ['Vector', `${form.vectorStrategy} · ${form.vectorProfileKey}`],
                              ['Provisioning', `${form.vectorProvisioningMode} · ${form.vectorStoragePosture}`],
                              ['Inference', form.inferencePluginId],
                              ['Template', `${form.templatePluginId} · ${form.deploymentTemplateId}`],
                              ['Verification', form.verificationPackId],
                            ].map(([label, value]) => (
                              <Box key={label}>
                                <Typography variant="caption" color="text.secondary">
                                  {label}
                                </Typography>
                                <Typography variant="body2">{value}</Typography>
                              </Box>
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Stack>
                  </Grid>
                </Grid>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  )
}
