import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import CloudUploadRoundedIcon from '@mui/icons-material/CloudUploadRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import ExtensionRoundedIcon from '@mui/icons-material/ExtensionRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import PublishRoundedIcon from '@mui/icons-material/PublishRounded'
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
import { useLocation, useNavigate } from 'react-router-dom'
import {
  bootstrapMarketplaceTemplatePlugin,
  createMarketplacePublisher,
  createMarketplacePublisherSubmission,
  createDeploymentMarketplaceInstall,
  deleteDeploymentMarketplaceInstall,
  fetchDeployments,
  fetchDeploymentMarketplaceImpact,
  fetchDeploymentMarketplaceInstalls,
  fetchDeploymentTemplates,
  fetchDeploymentWorkspace,
  fetchMarketplaceCategories,
  fetchMarketplacePlugin,
  fetchMarketplacePlugins,
  fetchMarketplacePublisher,
  fetchMarketplacePublishers,
  publishMarketplaceSubmission,
  rejectMarketplaceSubmission,
  resolveDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstallEntitlement,
  updateMarketplacePublisherVerification,
  validateMarketplaceSubmission,
  type CreateMarketplacePublisherRequest,
  type CreateMarketplacePublisherSubmissionRequest,
  type CreateMarketplaceTemplateBootstrapRequest,
  type DeploymentSummary,
  type DeploymentMarketplaceInstallSummary,
  type MarketplacePluginDetailSummary,
  type MarketplacePluginSummary,
  type MarketplacePublisherSummary,
} from '../api/platformApi'

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

function pluginTypeColor(pluginType: string): 'primary' | 'secondary' | 'success' | 'warning' {
  switch (pluginType) {
    case 'TEMPLATE':
      return 'secondary'
    case 'ACTION':
      return 'primary'
    case 'DATA':
      return 'success'
    default:
      return 'warning'
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

function readinessColor(value: string): 'success' | 'warning' | 'default' {
  switch (value) {
    case 'READY':
    case 'LIVE':
      return 'success'
    case 'BOOTSTRAPPED':
      return 'default'
    default:
      return 'warning'
  }
}

function entitlementColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'ACTIVE':
      return 'success'
    case 'PAST_DUE':
    case 'PENDING':
      return 'warning'
    case 'SUSPENDED':
    case 'CANCELLED':
      return 'error'
    default:
      return 'default'
  }
}

function publisherVerificationColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'VERIFIED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'error'
    default:
      return 'default'
  }
}

function publisherStatusColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'ACTIVE':
      return 'success'
    case 'SUSPENDED':
      return 'error'
    default:
      return 'default'
  }
}

function submissionStatusColor(value: string): 'success' | 'warning' | 'error' | 'default' {
  switch (value) {
    case 'PUBLISHED':
      return 'success'
    case 'VALIDATED':
      return 'warning'
    case 'REJECTED':
      return 'error'
    default:
      return 'default'
  }
}

function pricingLabel(
  pricing: { pricingModel: string; amount: number | null; currency: string | null; billingInterval: string | null } | null | undefined,
): string {
  if (!pricing) {
    return 'Free'
  }
  if (pricing.pricingModel === 'FREE') {
    return 'Free'
  }
  const amount = pricing.amount == null ? '—' : `${pricing.currency ?? ''} ${pricing.amount.toFixed(2)}`.trim()
  if (pricing.pricingModel === 'ONE_OFF') {
    return `${amount} one-off`
  }
  return `${amount} / ${normalizeText(pricing.billingInterval ?? 'monthly')}`
}

function toDatetimeLocal(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  const pad = (part: number) => `${part}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toIsoOrNull(value: string): string | null {
  return value.trim().length > 0 ? new Date(value).toISOString() : null
}

function defaultBootstrapName(detail: MarketplacePluginDetailSummary | null): string {
  if (!detail) {
    return 'Marketplace Deployment'
  }
  return `${detail.plugin.displayName} Deployment`
}

export function MarketplacePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
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
  const [entitlementStatus, setEntitlementStatus] = useState('PENDING')
  const [entitlementGraceEndsAt, setEntitlementGraceEndsAt] = useState('')
  const [entitlementAccessEndsAt, setEntitlementAccessEndsAt] = useState('')
  const [entitlementNote, setEntitlementNote] = useState('')
  const [selectedPublisherId, setSelectedPublisherId] = useState('')
  const [publisherSlug, setPublisherSlug] = useState('')
  const [publisherDisplayName, setPublisherDisplayName] = useState('')
  const [publisherContactEmail, setPublisherContactEmail] = useState('')
  const [publisherVerificationStatus, setPublisherVerificationStatus] = useState('PENDING')
  const [publisherStatus, setPublisherStatus] = useState('ACTIVE')
  const [submissionPluginSlug, setSubmissionPluginSlug] = useState('')
  const [submissionReleaseChannel, setSubmissionReleaseChannel] = useState('DRAFT')
  const [submissionManifestJson, setSubmissionManifestJson] = useState('{\n  "schemaVersion": 1,\n  "pluginId": "",\n  "version": "1.0.0",\n  "pluginType": "TEMPLATE",\n  "displayName": "",\n  "description": "",\n  "compatibility": {\n    "requiredCapabilities": ["templates", "shellConfig"]\n  },\n  "pricing": {\n    "pricingModel": "FREE"\n  },\n  "permissions": {\n    "contributesTemplate": true,\n    "contributesShellPresentation": true\n  },\n  "contributions": {\n    "template": {\n      "curatedModuleId": "commerce",\n      "shell": {\n        "enabledModuleIds": ["docs", "actions"]\n      }\n    }\n  }\n}')
  const [submissionReviewNotes, setSubmissionReviewNotes] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const requestedTargetDeploymentId = useMemo(() => {
    const searchParams = new URLSearchParams(location.search)
    return searchParams.get('targetDeploymentId') ?? searchParams.get('deploymentId') ?? ''
  }, [location.search])

  const pluginsQuery = useQuery({
    queryKey: ['marketplace-plugins'],
    queryFn: fetchMarketplacePlugins,
  })
  const categoriesQuery = useQuery({
    queryKey: ['marketplace-categories'],
    queryFn: fetchMarketplaceCategories,
  })
  const publishersQuery = useQuery({
    queryKey: ['marketplace-publishers'],
    queryFn: fetchMarketplacePublishers,
  })
  const deploymentTemplatesQuery = useQuery({
    queryKey: ['deployment-templates'],
    queryFn: fetchDeploymentTemplates,
  })
  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })
  const targetDeploymentSummary = useMemo<DeploymentSummary | null>(
    () => (deploymentsQuery.data ?? []).find((deployment) => deployment.id === requestedTargetDeploymentId) ?? null,
    [deploymentsQuery.data, requestedTargetDeploymentId],
  )
  const targetDeploymentId = targetDeploymentSummary?.id ?? ''
  const targetWorkspaceQuery = useQuery({
    queryKey: ['deployment-workspace', targetDeploymentId],
    queryFn: () => fetchDeploymentWorkspace(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const targetWorkspace = targetWorkspaceQuery.data ?? null
  const canEdit = targetWorkspace?.access.canEdit ?? false
  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', targetDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const impactQuery = useQuery({
    queryKey: ['deployment-marketplace-impact', targetDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceImpact(targetDeploymentId),
    enabled: targetDeploymentId.length > 0,
  })
  const publisherDetailQuery = useQuery({
    queryKey: ['marketplace-publisher', selectedPublisherId],
    queryFn: () => fetchMarketplacePublisher(selectedPublisherId),
    enabled: selectedPublisherId.length > 0,
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

  useEffect(() => {
    const publishers = publishersQuery.data ?? []
    if (publishers.length === 0) {
      setSelectedPublisherId('')
      return
    }
    if (!publishers.some((publisher) => publisher.id === selectedPublisherId)) {
      setSelectedPublisherId(publishers[0].id)
    }
  }, [publishersQuery.data, selectedPublisherId])

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
  const selectedPublisher = publisherDetailQuery.data?.publisher ?? null

  const selectedVersionSummary = pluginDetailQuery.data?.versions.find((version) => version.version === selectedVersion)
    ?? pluginDetailQuery.data?.versions[0]
    ?? null

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
    const publisher = publisherDetailQuery.data?.publisher
    if (!publisher) {
      return
    }
    setPublisherVerificationStatus(publisher.verificationStatus)
    setPublisherStatus(publisher.status)
  }, [publisherDetailQuery.data?.publisher])

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
    if (selectedInstall) {
      setEntitlementStatus(selectedInstall.entitlement.status)
      setEntitlementGraceEndsAt(toDatetimeLocal(selectedInstall.entitlement.graceEndsAt))
      setEntitlementAccessEndsAt(toDatetimeLocal(selectedInstall.entitlement.accessEndsAt))
      setEntitlementNote(selectedInstall.entitlement.note ?? '')
      return
    }
    setEntitlementStatus(selectedVersionSummary?.pricing.requiresEntitlement ? 'PENDING' : 'ACTIVE')
    setEntitlementGraceEndsAt('')
    setEntitlementAccessEndsAt('')
    setEntitlementNote('')
  }, [selectedInstall?.id, selectedInstall?.entitlement.status, selectedVersionSummary?.pricing.requiresEntitlement])

  useEffect(() => {
    if (targetWorkspace?.deployment.binding) {
      setTemplateCustomerId((current) => current || targetWorkspace.deployment.binding?.customerId || '')
      setTemplateTenantId((current) => current || targetWorkspace.deployment.binding?.tenantId || '')
    }
  }, [targetWorkspace?.deployment.binding])

  useEffect(() => {
    const templates = deploymentTemplatesQuery.data ?? []
    if (templates.length === 0) {
      return
    }
    setTemplateId((current) => current || templates[0].id)
  }, [deploymentTemplatesQuery.data])

  const setTargetDeploymentId = (deploymentId: string) => {
    const nextParams = new URLSearchParams(location.search)
    nextParams.delete('deploymentId')
    if (deploymentId.trim().length === 0) {
      nextParams.delete('targetDeploymentId')
    } else {
      nextParams.set('targetDeploymentId', deploymentId)
    }
    const nextSearch = nextParams.toString()
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch.length > 0 ? `?${nextSearch}` : '',
      },
      { replace: false },
    )
  }

  const invalidateDeploymentState = async () => {
    if (!targetDeploymentId) {
      await queryClient.invalidateQueries({ queryKey: ['deployments'] })
      return
    }
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-impact', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-draft', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-workspace', targetDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
    ])
  }

  const invalidatePublisherState = async (publisherId?: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['marketplace-publishers'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace-plugins'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace-plugin'] }),
      ...(publisherId ? [queryClient.invalidateQueries({ queryKey: ['marketplace-publisher', publisherId] })] : []),
    ])
  }

  const installMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPlugin || targetDeploymentId.length === 0) {
        throw new Error('Select a target deployment and a marketplace plugin first.')
      }
      const payload = {
        pluginId: selectedPlugin.id,
        pluginVersion: selectedVersion,
        config: parseJsonObject(configJson, 'Config JSON'),
        secretRefs: parseJsonObject(secretRefsJson, 'Secret refs JSON'),
      }
      if (selectedInstall) {
        return updateDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id, {
          pluginVersion: payload.pluginVersion,
          status: selectedInstall.status,
          config: payload.config,
          secretRefs: payload.secretRefs,
        })
      }
      return createDeploymentMarketplaceInstall(targetDeploymentId, payload)
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`${install.pluginDisplayName} is now ${selectedInstall ? 'updated' : 'installed'} for ${targetDeploymentId}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const statusMutation = useMutation({
    mutationFn: async (status: string) => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return updateDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id, {
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
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return resolveDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id)
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

  const entitlementMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      return updateDeploymentMarketplaceInstallEntitlement(targetDeploymentId, selectedInstall.id, {
        status: entitlementStatus,
        graceEndsAt: toIsoOrNull(entitlementGraceEndsAt),
        accessEndsAt: toIsoOrNull(entitlementAccessEndsAt),
        note: entitlementNote.trim().length > 0 ? entitlementNote.trim() : null,
      })
    },
    onSuccess: async (install) => {
      setFormError(null)
      setSuccessMessage(`Entitlement for ${install.pluginDisplayName} is now ${install.entitlement.status}.`)
      await invalidateDeploymentState()
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedInstall || targetDeploymentId.length === 0) {
        throw new Error('No marketplace install selected.')
      }
      await deleteDeploymentMarketplaceInstall(targetDeploymentId, selectedInstall.id)
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

  const createPublisherMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateMarketplacePublisherRequest = {
        slug: publisherSlug.trim(),
        displayName: publisherDisplayName.trim(),
        contactEmail: publisherContactEmail.trim(),
      }
      if (!payload.slug || !payload.displayName || !payload.contactEmail) {
        throw new Error('Publisher slug, display name, and contact email are required.')
      }
      return createMarketplacePublisher(payload)
    },
    onSuccess: async (publisher) => {
      setFormError(null)
      setSuccessMessage(`Publisher ${publisher.displayName} created.`)
      setSelectedPublisherId(publisher.id)
      await invalidatePublisherState(publisher.id)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const updatePublisherVerificationMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPublisherId) {
        throw new Error('Select a publisher first.')
      }
      return updateMarketplacePublisherVerification(selectedPublisherId, {
        verificationStatus: publisherVerificationStatus,
        status: publisherStatus,
      })
    },
    onSuccess: async (publisher) => {
      setFormError(null)
      setSuccessMessage(`Publisher ${publisher.displayName} is now ${publisher.verificationStatus} / ${publisher.status}.`)
      await invalidatePublisherState(publisher.id)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const createSubmissionMutation = useMutation({
    mutationFn: async () => {
      if (!selectedPublisherId) {
        throw new Error('Select a publisher first.')
      }
      const payload: CreateMarketplacePublisherSubmissionRequest = {
        pluginSlug: submissionPluginSlug.trim() || undefined,
        releaseChannel: submissionReleaseChannel.trim() || undefined,
        manifest: parseJsonObject(submissionManifestJson, 'Submission manifest'),
      }
      return createMarketplacePublisherSubmission(selectedPublisherId, payload)
    },
    onSuccess: async (submission) => {
      setFormError(null)
      setSuccessMessage(`Submission ${submission.pluginId}@${submission.version} created.`)
      await invalidatePublisherState(selectedPublisherId)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  const reviewSubmissionMutation = useMutation({
    mutationFn: async ({ pluginVersionId, action }: { pluginVersionId: string; action: 'validate' | 'publish' | 'reject' }) => {
      const payload = {
        reviewNotes: submissionReviewNotes.trim().length > 0 ? submissionReviewNotes.trim() : null,
      }
      if (action === 'validate') {
        return validateMarketplaceSubmission(pluginVersionId, payload)
      }
      if (action === 'publish') {
        return publishMarketplaceSubmission(pluginVersionId, payload)
      }
      return rejectMarketplaceSubmission(pluginVersionId, payload)
    },
    onSuccess: async (submission) => {
      setFormError(null)
      setSuccessMessage(`Submission ${submission.pluginId}@${submission.version} is now ${submission.status}.`)
      await invalidatePublisherState(submission.publisherId)
    },
    onError: (error: Error) => {
      setSuccessMessage(null)
      setFormError(error.message)
    },
  })

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.5 }}>
            Marketplace
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75, maxWidth: 900 }}>
            Browse first-party marketplace plugins, bootstrap template-based deployments, and assign action, data, or automation plugins into an explicitly selected deployment draft through the normal publish and apply lifecycle.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip icon={<StorefrontRoundedIcon />} label={`${pluginsQuery.data?.length ?? 0} plugins`} variant="outlined" />
          <Chip
            icon={<ExtensionRoundedIcon />}
            label={targetDeploymentId ? `Target: ${targetDeploymentId}` : 'No target deployment selected'}
            color={targetDeploymentId ? 'primary' : 'default'}
          />
          <Chip label={`${deploymentsQuery.data?.length ?? 0} accessible deployments`} variant="outlined" />
          {impactQuery.data ? (
            <Chip icon={<SyncRoundedIcon />} label={`${impactQuery.data.totalInstalls} active install records`} variant="outlined" />
          ) : null}
        </Stack>
      </Stack>

      {formError ? <Alert severity="error">{formError}</Alert> : null}
      {successMessage ? <Alert severity="success">{successMessage}</Alert> : null}
      {!targetDeploymentId ? (
        <Alert severity="info">
          Marketplace is global. Template plugins can create new deployments directly, and action, data, or automation plugins install into the target deployment you choose below.
        </Alert>
      ) : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Target deployment
                </Typography>
                <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                  Marketplace installs and entitlement changes are assigned to a deployment you select here. This page no longer inherits the deployment workspace selection.
                </Typography>
              </Box>
              {targetDeploymentId ? (
                <Button
                  variant="outlined"
                  onClick={() => navigate(`/overview?deploymentId=${encodeURIComponent(targetDeploymentId)}`)}
                >
                  Open target workspace
                </Button>
              ) : null}
            </Stack>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField
                  select
                  fullWidth
                  label="Target deployment"
                  value={targetDeploymentId}
                  onChange={(event) => setTargetDeploymentId(event.target.value)}
                  disabled={deploymentsQuery.isLoading || (deploymentsQuery.data ?? []).length === 0}
                  helperText={targetWorkspace
                    ? `${targetWorkspace.deployment.name} · ${targetWorkspace.access.assignmentRole}`
                    : 'Select the deployment that should receive installs and subscriptions.'}
                >
                  <MenuItem value="">No target deployment</MenuItem>
                  {(deploymentsQuery.data ?? []).map((deployment) => (
                    <MenuItem key={deployment.id} value={deployment.id}>
                      {deployment.name} · {deployment.environment} · {deployment.id}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                {targetDeploymentId ? (
                  targetWorkspaceQuery.isLoading ? (
                    <Alert severity="info">Loading target deployment access…</Alert>
                  ) : !canEdit ? (
                    <Alert severity="warning">Your current assignment role is read-only for marketplace changes on this deployment.</Alert>
                  ) : (
                    <Alert severity="success">Marketplace changes will compile into the active draft for this deployment.</Alert>
                  )
                ) : (
                  <Alert severity="info">Choose a target deployment when you want to install or subscribe to a plugin.</Alert>
                )}
              </Grid>
            </Grid>
          </Stack>
        </CardContent>
      </Card>

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
                                {plugin.publisherDisplayName} · latest {plugin.latestVersion ?? '—'} · {pricingLabel(plugin.pricing)}
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
                          <Chip label={pricingLabel(selectedVersionSummary?.pricing ?? selectedPlugin.pricing)} variant="outlined" />
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
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Compatibility
                            </Typography>
                            <Stack spacing={1} sx={{ mt: 1 }}>
                              <Typography variant="body2">
                                Deployment targets: {contributionList(selectedVersionSummary?.compatibility.supportedDeploymentTargets ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Auth modes: {contributionList(selectedVersionSummary?.compatibility.supportedAuthModes ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Provider modes: {contributionList(selectedVersionSummary?.compatibility.supportedProviderModes ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Required capabilities: {contributionList(selectedVersionSummary?.compatibility.requiredCapabilities ?? [])}
                              </Typography>
                              <Typography variant="body2">
                                Pricing: {pricingLabel(selectedVersionSummary?.pricing)}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="text.secondary">
                              Install requirements
                            </Typography>
                            {selectedVersionSummary && selectedVersionSummary.installForm.length > 0 ? (
                              <Stack spacing={1} sx={{ mt: 1 }}>
                                {selectedVersionSummary.installForm.map((field) => (
                                  <Typography key={field.id} variant="body2">
                                    {field.label} ({field.type}){field.required ? ' required' : ' optional'}
                                    {field.options.length > 0 ? ` · ${field.options.join(', ')}` : ''}
                                  </Typography>
                                ))}
                              </Stack>
                            ) : (
                              <Typography sx={{ mt: 1 }}>No operator input is required for this version.</Typography>
                            )}
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                    {selectedVersionSummary?.recommendedPluginIds.length ? (
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle2" color="text.secondary">
                            Recommended add-ons
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                            {selectedVersionSummary.recommendedPluginIds.map((pluginId) => (
                              <Chip
                                key={pluginId}
                                label={pluginId}
                                variant="outlined"
                                onClick={() => setSelectedPluginId(pluginId)}
                              />
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
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
                        Install into target deployment
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Action and data plugins compile into the target deployment draft. Save, publish, and apply are still required before they affect the live runtime.
                    </Typography>
                    {!targetDeploymentId ? (
                      <Alert severity="info">Choose a target deployment above to install or update this plugin.</Alert>
                    ) : !canEdit ? (
                      <Alert severity="warning">Your current assignment role is read-only for marketplace install changes on this deployment.</Alert>
                    ) : null}
                    {selectedVersionSummary?.installForm.length ? (
                      <Alert severity="info">
                        This plugin declares install fields. Provide config values in `Config JSON` and secret references in `Secret refs JSON`.
                        Required fields: {selectedVersionSummary.installForm.filter((field) => field.required).map((field) => `${field.id} (${field.type})`).join(', ')}
                      </Alert>
                    ) : null}
                    <Alert severity={selectedVersionSummary?.pricing.requiresEntitlement ? 'warning' : 'info'}>
                      Pricing: {pricingLabel(selectedVersionSummary?.pricing)}.
                      {selectedVersionSummary?.pricing.requiresEntitlement
                        ? ' Paid marketplace installs remain out of the deployment draft until entitlement is activated.'
                        : ' Free marketplace installs are entitled immediately.'}
                    </Alert>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          fullWidth
                          multiline
                          minRows={8}
                          label="Config JSON"
                          value={configJson}
                          onChange={(event) => setConfigJson(event.target.value)}
                          disabled={!canEdit || !targetDeploymentId}
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
                          disabled={!canEdit || !targetDeploymentId}
                        />
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {selectedInstall ? (
                        <Chip label={`Live state: ${selectedInstall.liveState}`} variant="outlined" />
                      ) : null}
                      {selectedInstall ? (
                        <Chip label={`Readiness: ${selectedInstall.readinessStatus}`} color={readinessColor(selectedInstall.readinessStatus)} variant="outlined" />
                      ) : null}
                      {selectedInstall ? (
                        <Chip
                          label={`Entitlement: ${selectedInstall.entitlement.status}`}
                          color={entitlementColor(selectedInstall.entitlement.status)}
                          variant="outlined"
                        />
                      ) : null}
                      <Button
                        variant="contained"
                        onClick={() => {
                          setFormError(null)
                          setSuccessMessage(null)
                          installMutation.mutate()
                        }}
                        disabled={!canEdit || !targetDeploymentId || installMutation.isPending}
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
                            disabled={!targetDeploymentId || resolveMutation.isPending}
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
                    {selectedInstall?.entitlement.requiresEntitlement ? (
                      <Card variant="outlined">
                        <CardContent>
                          <Stack spacing={2}>
                            <Typography variant="subtitle2" color="text.secondary">
                              Entitlement lifecycle
                            </Typography>
                            <Grid container spacing={2}>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  select
                                  fullWidth
                                  label="Entitlement status"
                                  value={entitlementStatus}
                                  onChange={(event) => setEntitlementStatus(event.target.value)}
                                  disabled={!canEdit}
                                >
                                  {['PENDING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'].map((value) => (
                                    <MenuItem key={value} value={value}>
                                      {value}
                                    </MenuItem>
                                  ))}
                                </TextField>
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Grace ends"
                                  type="datetime-local"
                                  value={entitlementGraceEndsAt}
                                  onChange={(event) => setEntitlementGraceEndsAt(event.target.value)}
                                  InputLabelProps={{ shrink: true }}
                                  disabled={!canEdit}
                                />
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Access ends"
                                  type="datetime-local"
                                  value={entitlementAccessEndsAt}
                                  onChange={(event) => setEntitlementAccessEndsAt(event.target.value)}
                                  InputLabelProps={{ shrink: true }}
                                  disabled={!canEdit}
                                />
                              </Grid>
                              <Grid item xs={12} md={3}>
                                <TextField
                                  fullWidth
                                  label="Note"
                                  value={entitlementNote}
                                  onChange={(event) => setEntitlementNote(event.target.value)}
                                  disabled={!canEdit}
                                />
                              </Grid>
                            </Grid>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip label={pricingLabel(selectedInstall.entitlement)} variant="outlined" />
                              <Chip
                                label={`Compiles into draft: ${selectedInstall.entitlement.entitledForCompilation ? 'yes' : 'no'}`}
                                color={selectedInstall.entitlement.entitledForCompilation ? 'success' : 'warning'}
                                variant="outlined"
                              />
                              <Button
                                variant="outlined"
                                onClick={() => {
                                  setFormError(null)
                                  setSuccessMessage(null)
                                  entitlementMutation.mutate()
                                }}
                                disabled={!canEdit || entitlementMutation.isPending}
                              >
                                Save entitlement
                              </Button>
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    ) : null}
                    {selectedInstall?.warnings.length ? (
                      <Alert severity="warning">
                        {selectedInstall.warnings.join(' ')}
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            )}

            {targetDeploymentId ? (
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
                        <Alert severity="info">No marketplace installs are recorded for the selected target deployment yet.</Alert>
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
                                    <Chip size="small" label={install.readinessStatus} color={readinessColor(install.readinessStatus)} variant="outlined" />
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
                            <Grid item xs={12} md={6}>
                              <Card variant="outlined">
                                <CardContent>
                                  <Typography variant="subtitle2" color="text.secondary">
                                    Shell modules
                                  </Typography>
                                  <Typography sx={{ mt: 0.75 }}>
                                    {contributionList(impactQuery.data.shellModuleIds)}
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
                          {impactQuery.data.recommendedPluginIds.length > 0 ? (
                            <Alert severity="info">
                              Recommended add-ons from template plugins: {impactQuery.data.recommendedPluginIds.join(', ')}
                            </Alert>
                          ) : null}
                        </>
                      ) : (
                        <Alert severity="info">Impact preview will appear once a target deployment is selected.</Alert>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
              </>
            ) : null}
          </Stack>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <ManageAccountsRoundedIcon color="primary" />
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Publisher workspace
                  </Typography>
                </Stack>
                <Typography color="text.secondary">
                  Manage publisher identities, review posture, and external plugin submissions. Backend governance still enforces owner and admin boundaries.
                </Typography>
                {publishersQuery.isLoading ? (
                  <Typography color="text.secondary">Loading publishers…</Typography>
                ) : (publishersQuery.data ?? []).length === 0 ? (
                  <Alert severity="info">No publisher accounts exist yet. Create one to start submitting external plugins.</Alert>
                ) : (
                  <List disablePadding>
                    {(publishersQuery.data ?? []).map((publisher: MarketplacePublisherSummary) => (
                      <ListItemButton
                        key={publisher.id}
                        selected={publisher.id === selectedPublisherId}
                        onClick={() => setSelectedPublisherId(publisher.id)}
                        sx={{ borderRadius: 2, mb: 1 }}
                      >
                        <ListItemText
                          primary={
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{publisher.displayName}</Typography>
                              <Chip size="small" label={publisher.verificationStatus} color={publisherVerificationColor(publisher.verificationStatus)} />
                            </Stack>
                          }
                          secondary={
                            <Typography variant="body2" color="text.secondary">
                              {publisher.slug} · {publisher.submissionCount} submissions
                            </Typography>
                          }
                        />
                      </ListItemButton>
                    ))}
                  </List>
                )}
                <Divider />
                <Typography variant="subtitle2" color="text.secondary">
                  Create publisher
                </Typography>
                <TextField
                  fullWidth
                  label="Slug"
                  value={publisherSlug}
                  onChange={(event) => setPublisherSlug(event.target.value)}
                  placeholder="acme-marketplace"
                />
                <TextField
                  fullWidth
                  label="Display name"
                  value={publisherDisplayName}
                  onChange={(event) => setPublisherDisplayName(event.target.value)}
                  placeholder="Acme Marketplace"
                />
                <TextField
                  fullWidth
                  label="Contact email"
                  value={publisherContactEmail}
                  onChange={(event) => setPublisherContactEmail(event.target.value)}
                  placeholder="support@acme.example"
                />
                <Button
                  variant="contained"
                  startIcon={<CloudUploadRoundedIcon />}
                  onClick={() => {
                    setFormError(null)
                    setSuccessMessage(null)
                    createPublisherMutation.mutate()
                  }}
                  disabled={createPublisherMutation.isPending}
                >
                  Create publisher
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                {!selectedPublisher ? (
                  <Alert severity="info">Select a publisher to review submissions or create a new one.</Alert>
                ) : publisherDetailQuery.isLoading ? (
                  <Typography color="text.secondary">Loading publisher detail…</Typography>
                ) : (
                  <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                      <Box>
                        <Typography variant="h5" sx={{ fontWeight: 800 }}>
                          {selectedPublisher.displayName}
                        </Typography>
                        <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                          {selectedPublisher.slug} · {selectedPublisher.contactEmail}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                          Owner: {selectedPublisher.ownerUserId} · Created {formatTimestamp(selectedPublisher.createdAt)}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={selectedPublisher.verificationStatus} color={publisherVerificationColor(selectedPublisher.verificationStatus)} />
                        <Chip label={selectedPublisher.status} color={publisherStatusColor(selectedPublisher.status)} variant="outlined" />
                        <Chip label={`${publisherDetailQuery.data?.submissions.length ?? 0} submissions`} variant="outlined" />
                      </Stack>
                    </Stack>
                    <Alert severity="info">
                      Admin review is required for verification changes and submission state transitions. Non-admin users can still create publishers and submit versions they own.
                    </Alert>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Verification"
                          value={publisherVerificationStatus}
                          onChange={(event) => setPublisherVerificationStatus(event.target.value)}
                        >
                          {['PENDING', 'VERIFIED', 'REJECTED'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Publisher status"
                          value={publisherStatus}
                          onChange={(event) => setPublisherStatus(event.target.value)}
                        >
                          {['ACTIVE', 'SUSPENDED'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Button
                          fullWidth
                          variant="outlined"
                          startIcon={<FactCheckRoundedIcon />}
                          onClick={() => {
                            setFormError(null)
                            setSuccessMessage(null)
                            updatePublisherVerificationMutation.mutate()
                          }}
                          disabled={updatePublisherVerificationMutation.isPending}
                          sx={{ height: '100%' }}
                        >
                          Save review posture
                        </Button>
                      </Grid>
                    </Grid>
                  </Stack>
                )}
              </CardContent>
            </Card>

            {selectedPublisher ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <CloudUploadRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Submit plugin version
                      </Typography>
                    </Stack>
                    <Typography color="text.secondary">
                      Submit an external plugin manifest for review. The catalog only exposes versions after admin validation and publication.
                    </Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Plugin slug override"
                          value={submissionPluginSlug}
                          onChange={(event) => setSubmissionPluginSlug(event.target.value)}
                          placeholder="Optional"
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          select
                          fullWidth
                          label="Release channel"
                          value={submissionReleaseChannel}
                          onChange={(event) => setSubmissionReleaseChannel(event.target.value)}
                        >
                          {['DRAFT', 'BETA', 'GA'].map((value) => (
                            <MenuItem key={value} value={value}>
                              {value}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Review notes"
                          value={submissionReviewNotes}
                          onChange={(event) => setSubmissionReviewNotes(event.target.value)}
                          placeholder="Optional admin notes for validate/publish/reject"
                        />
                      </Grid>
                    </Grid>
                    <TextField
                      fullWidth
                      multiline
                      minRows={16}
                      label="Manifest JSON"
                      value={submissionManifestJson}
                      onChange={(event) => setSubmissionManifestJson(event.target.value)}
                    />
                    <Button
                      variant="contained"
                      startIcon={<CloudUploadRoundedIcon />}
                      onClick={() => {
                        setFormError(null)
                        setSuccessMessage(null)
                        createSubmissionMutation.mutate()
                      }}
                      disabled={createSubmissionMutation.isPending}
                    >
                      Submit version
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {selectedPublisher ? (
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <PublishRoundedIcon color="primary" />
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        Submission review queue
                      </Typography>
                    </Stack>
                    {(publisherDetailQuery.data?.submissions ?? []).length === 0 ? (
                      <Alert severity="info">No submissions exist for this publisher yet.</Alert>
                    ) : (
                      <Stack spacing={2}>
                        {(publisherDetailQuery.data?.submissions ?? []).map((submission) => (
                          <Card key={submission.pluginVersionId} variant="outlined">
                            <CardContent>
                              <Stack spacing={2}>
                                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                                  <Box>
                                    <Typography sx={{ fontWeight: 700 }}>
                                      {submission.pluginDisplayName} · {submission.version}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                                      {submission.pluginType} · {submission.pluginSlug} · {submission.releaseChannel}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                                      Submitted {formatTimestamp(submission.submittedAt)} by {submission.submittedByActorId ?? '—'}
                                      {submission.reviewedAt ? ` · reviewed ${formatTimestamp(submission.reviewedAt)} by ${submission.reviewedByActorId ?? '—'}` : ''}
                                    </Typography>
                                  </Box>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                    <Chip label={submission.status} color={submissionStatusColor(submission.status)} />
                                    {submission.bundleSha256 ? <Chip label={submission.bundleSha256.slice(0, 12)} variant="outlined" /> : null}
                                  </Stack>
                                </Stack>
                                {submission.reviewNotes ? (
                                  <Alert severity="info">{submission.reviewNotes}</Alert>
                                ) : null}
                                <TextField
                                  fullWidth
                                  multiline
                                  minRows={8}
                                  label="Submission manifest"
                                  value={prettifyJson(submission.manifest)}
                                  InputProps={{ readOnly: true }}
                                />
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Button
                                    variant="outlined"
                                    startIcon={<FactCheckRoundedIcon />}
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'validate',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || !['SUBMITTED', 'REJECTED'].includes(submission.status)}
                                  >
                                    Validate
                                  </Button>
                                  <Button
                                    variant="contained"
                                    startIcon={<PublishRoundedIcon />}
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'publish',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || submission.status !== 'VALIDATED'}
                                  >
                                    Publish
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    color="error"
                                    onClick={() => {
                                      setFormError(null)
                                      setSuccessMessage(null)
                                      reviewSubmissionMutation.mutate({
                                        pluginVersionId: submission.pluginVersionId,
                                        action: 'reject',
                                      })
                                    }}
                                    disabled={reviewSubmissionMutation.isPending || !['SUBMITTED', 'VALIDATED'].includes(submission.status)}
                                  >
                                    Reject
                                  </Button>
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}
          </Stack>
        </Grid>
      </Grid>
    </Stack>
  )
}
