import BoltOutlinedIcon from '@mui/icons-material/BoltOutlined'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import DeleteSweepOutlinedIcon from '@mui/icons-material/DeleteSweepOutlined'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import {
  Alert,
  Box,
  Button,
  Chip,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import {
  deletePartnerMaxWidgetConversation,
  fetchPartnerMaxWidgetRuntimeAuthContext,
  PARTNER_MAX_WIDGET_AUTH_PATHS,
  partnerMaxWidgetAbsoluteBaseUrl,
  partnerMaxWidgetAuthPathQuery,
  queryPartnerMaxWidget,
  type PartnerMaxWidgetAnyResponse,
  type PartnerMaxWidgetAuthPath,
} from '../api/maxWidget'
import type { PartnerProductControl } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { formatDateTime, titleize } from '../utils/format'

const MAX_MODE_WIDGET_SCRIPT_ID = 'partner-max-mode-widget-script'
const MAX_MODE_WIDGET_SCRIPT_VERSION = '2026-04-14-opaque-shell-v1'
const MAX_MODE_WIDGET_SCRIPT_SRC = `/max-mode-widget.iife.js?v=${MAX_MODE_WIDGET_SCRIPT_VERSION}`
const MAX_MODE_WIDGET_STATE_KEY = 'maxmode_widget_state'
const MAX_MODE_WIDGET_PENDING_ATTACHMENTS_KEY = 'maxmode_widget_pending_attachments'

let maxModeWidgetScriptPromise: Promise<void> | null = null

function loadMaxModeWidgetScript() {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Max widget can only load in a browser context.'))
  }
  if (window.MaxMode) {
    return Promise.resolve()
  }
  if (maxModeWidgetScriptPromise) {
    return maxModeWidgetScriptPromise
  }

  maxModeWidgetScriptPromise = new Promise<void>((resolve, reject) => {
    const existingScript = document.getElementById(MAX_MODE_WIDGET_SCRIPT_ID) as HTMLScriptElement | null
    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Failed to load Max widget bundle.')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.id = MAX_MODE_WIDGET_SCRIPT_ID
    script.src = MAX_MODE_WIDGET_SCRIPT_SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Max widget bundle.'))
    document.head.appendChild(script)
  }).catch((error) => {
    maxModeWidgetScriptPromise = null
    throw error
  })

  return maxModeWidgetScriptPromise
}

function clearMaxModeWidgetSession() {
  if (typeof window === 'undefined') {
    return
  }
  window.sessionStorage.removeItem(MAX_MODE_WIDGET_STATE_KEY)
  window.sessionStorage.removeItem(MAX_MODE_WIDGET_PENDING_ATTACHMENTS_KEY)
}

function readMaxModeWidgetConversationId() {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    const raw = window.sessionStorage.getItem(MAX_MODE_WIDGET_STATE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as unknown
    if (!isRecord(parsed)) {
      return null
    }
    const conversationId = parsed.conversationId
    return typeof conversationId === 'string' && conversationId.trim().length > 0 ? conversationId.trim() : null
  } catch {
    return null
  }
}

function integrationModeFor(authPath: PartnerMaxWidgetAuthPath) {
  if (authPath === 'PUBLIC_AUTHENTICATED') {
    return 'public-runtime-authenticated' as const
  }
  if (authPath === 'PUBLIC_ANONYMOUS') {
    return 'public-runtime-anonymous' as const
  }
  return 'backend-mediated-private-runtime' as const
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function responseText(response: PartnerMaxWidgetAnyResponse | null) {
  if (!response) {
    return null
  }
  const directMessage = response.message
  if (typeof directMessage === 'string' && directMessage.trim().length > 0) {
    return directMessage.trim()
  }
  const result = response.result
  if (isRecord(result)) {
    for (const key of ['answer', 'response', 'text', 'content']) {
      const value = result[key]
      if (typeof value === 'string' && value.trim().length > 0) {
        return value.trim()
      }
    }
  }
  return 'Max responded without a displayable message.'
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

export function PartnerMaxWidgetLiveTest({ controls }: { controls: PartnerProductControl }) {
  const { api, getSession } = useSupabaseAuth()
  const [selectedAuthPath, setSelectedAuthPath] = useState<PartnerMaxWidgetAuthPath>('PLATFORM_PRIVATE')
  const [widgetScriptReady, setWidgetScriptReady] = useState(false)
  const [widgetScriptError, setWidgetScriptError] = useState<string | null>(null)
  const [widgetLifecycleLabel, setWidgetLifecycleLabel] = useState('Not opened')
  const [widgetLifecycleDetail, setWidgetLifecycleDetail] = useState('Bundle has not been attached yet.')
  const [widgetVersion, setWidgetVersion] = useState<string | null>(null)
  const [widgetReloadNonce, setWidgetReloadNonce] = useState(0)
  const [resetMessage, setResetMessage] = useState<string | null>(null)

  const widgetBaseUrl = useMemo(() => partnerMaxWidgetAbsoluteBaseUrl(controls.storeId), [controls.storeId])
  const selectedAuthPathMeta = PARTNER_MAX_WIDGET_AUTH_PATHS.find((item) => item.value === selectedAuthPath)
  const canReadProductConfig = controls.capabilities.includes('PRODUCT_CONFIG_READ')
  const smokePrompt = `Run a live Max widget smoke test for ${controls.shopDomain}. Confirm product discovery, storefront-safe boundaries, and the selected access path.`

  const authContextQuery = useQuery({
    queryKey: ['partner-max-widget-auth-context', controls.storeId, selectedAuthPath],
    queryFn: () => fetchPartnerMaxWidgetRuntimeAuthContext(api, controls.storeId, selectedAuthPath),
    enabled: Boolean(controls.storeId) && canReadProductConfig,
    retry: false,
  })

  const smokeMutation = useMutation({
    mutationFn: () => queryPartnerMaxWidget(api, controls.storeId, selectedAuthPath, smokePrompt, readMaxModeWidgetConversationId()),
  })

  const resetMutation = useMutation({
    mutationFn: async () => {
      const conversationId = readMaxModeWidgetConversationId()
      clearMaxModeWidgetSession()
      if (conversationId) {
        await deletePartnerMaxWidgetConversation(api, controls.storeId, selectedAuthPath, conversationId)
        return `Reset conversation ${conversationId}.`
      }
      return 'Cleared local widget session.'
    },
    onSuccess: (message) => {
      setResetMessage(message)
      setWidgetReloadNonce((current) => current + 1)
    },
  })

  useEffect(() => {
    let cancelled = false
    setWidgetScriptReady(false)
    setWidgetScriptError(null)
    setWidgetLifecycleLabel('Loading bundle')
    setWidgetLifecycleDetail('Attaching the Max widget to the partner store workspace.')

    loadMaxModeWidgetScript()
      .then(async () => {
        if (cancelled) {
          return
        }
        const session = await getSession()
        const token = session?.access_token
        if (!token) {
          throw new Error('Partner session token is unavailable. Sign in again before opening the live widget.')
        }
        const authPathQuery = partnerMaxWidgetAuthPathQuery(selectedAuthPath)
        window.MaxMode?.destroy()
        window.MaxMode?.init({
          apiConfig: {
            chatBaseUrl: widgetBaseUrl,
            defaultHeaders: { Authorization: `Bearer ${token}` },
            fetchCredentials: 'omit',
            runtimeRoutes: {
              chatQueryUrl: `${widgetBaseUrl}/chat/me/query?${authPathQuery}`,
              suggestionsUrl: `${widgetBaseUrl}/chat/me/suggestions?${authPathQuery}`,
              authContextUrl: `${widgetBaseUrl}/chat/me/auth-context?${authPathQuery}`,
              shellConfigUrl: `${widgetBaseUrl}/chat/me/shell-config?${authPathQuery}`,
              conversationsUrl: `${widgetBaseUrl}/chat/me/conversations?${authPathQuery}`,
              conversationItemUrlTemplate: `${widgetBaseUrl}/chat/me/conversations/{conversationId}?${authPathQuery}`,
            },
            runtimeAuth: {
              probeAuthContextOnOpen: false,
            },
          },
          integrationMode: integrationModeFor(selectedAuthPath),
          launcher: false,
          position: 'bottom-right',
          features: {
            cart: false,
            debug: true,
            conversations: true,
            quickActions: true,
          },
          theme: {
            darkMode: true,
            primaryColor: '#38bdf8',
            borderRadius: '10px',
          },
          onEvent: (event) => {
            setWidgetLifecycleLabel(titleize(event.type))
            setWidgetLifecycleDetail(event.timestamp)
          },
          onClose: () => {
            setWidgetLifecycleLabel('Closed')
            setWidgetLifecycleDetail('Widget overlay was closed from the partner workspace.')
          },
        })
        setWidgetVersion(window.MaxMode?.version ?? null)
        setWidgetScriptReady(true)
        setWidgetLifecycleLabel('Ready')
        setWidgetLifecycleDetail('Real Max widget is attached through partner-secured routes.')
      })
      .catch((error) => {
        if (!cancelled) {
          setWidgetScriptError(errorMessage(error, 'Failed to load Max widget.'))
          setWidgetLifecycleLabel('Blocked')
          setWidgetLifecycleDetail(errorMessage(error, 'Widget bundle could not be attached.'))
        }
      })

    return () => {
      cancelled = true
      window.MaxMode?.destroy()
    }
  }, [getSession, selectedAuthPath, widgetBaseUrl, widgetReloadNonce])

  const openWidget = () => {
    window.MaxMode?.open()
    setWidgetLifecycleLabel('Opened')
    setWidgetLifecycleDetail('Widget overlay opened from the partner workspace.')
  }

  const closeWidget = () => {
    window.MaxMode?.close()
    setWidgetLifecycleLabel('Closed')
    setWidgetLifecycleDetail('Widget overlay closed from the partner workspace.')
  }

  const switchAuthPath = (value: string) => {
    setSelectedAuthPath(value as PartnerMaxWidgetAuthPath)
    setResetMessage(null)
    setWidgetReloadNonce((current) => current + 1)
  }

  return (
    <Paper sx={{ p: 2 }}>
      <PanelHeading icon={<BoltOutlinedIcon />} title="Live Max widget test" />
      <Typography color="text.secondary" sx={{ mt: 1 }}>
        Opens the real Max widget for this assigned store with selectable partner-secured and storefront access paths.
      </Typography>
      <Stack spacing={1.5} sx={{ mt: 2 }}>
        {!canReadProductConfig ? (
          <Alert severity="error">Partner assignment does not include PRODUCT_CONFIG_READ.</Alert>
        ) : null}
        {controls.installStatus !== 'INSTALLED' ? (
          <Alert severity="warning">Shopify Companion is {titleize(controls.installStatus)}. Live widget testing requires an installed store.</Alert>
        ) : null}
        {controls.widgetStatus !== 'ENABLED' ? (
          <Alert severity="warning">Storefront widget status is {titleize(controls.widgetStatus)}. The live test can still prove partner route wiring, but merchant storefront visibility is not green.</Alert>
        ) : null}
        {widgetScriptError ? <Alert severity="error">{widgetScriptError}</Alert> : null}
        {authContextQuery.isError ? (
          <Alert severity="warning">{errorMessage(authContextQuery.error, 'Selected auth path could not be verified.')}</Alert>
        ) : null}
        {smokeMutation.isError ? (
          <Alert severity="error">{errorMessage(smokeMutation.error, 'Live smoke query failed.')}</Alert>
        ) : null}
        {resetMutation.isError ? (
          <Alert severity="error">{errorMessage(resetMutation.error, 'Conversation reset failed.')}</Alert>
        ) : null}
        {resetMessage ? <Alert severity="success">{resetMessage}</Alert> : null}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 1.5 }}>
          <TextField
            select
            label="Access path"
            value={selectedAuthPath}
            onChange={(event) => switchAuthPath(event.target.value)}
          >
            {PARTNER_MAX_WIDGET_AUTH_PATHS.map((path) => (
              <MenuItem key={path.value} value={path.value}>{path.label}</MenuItem>
            ))}
          </TextField>
          <Box sx={{ p: 1.25, border: 1, borderColor: 'divider', borderRadius: 1 }}>
            <Typography variant="caption" color="text.secondary">Selected path</Typography>
            <Typography fontWeight={700}>{selectedAuthPathMeta?.detail ?? selectedAuthPath}</Typography>
          </Box>
        </Box>

        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Chip size="small" variant="outlined" label={`Bundle ${widgetScriptReady ? 'loaded' : 'loading'}`} />
          <Chip size="small" variant="outlined" label={`Lifecycle ${widgetLifecycleLabel}`} />
          <Chip size="small" variant="outlined" label={`Auth ${selectedAuthPath}`} />
          <Chip size="small" variant="outlined" label="Partner-secured routes" />
          <Chip size="small" variant="outlined" label={`Widget ${widgetVersion ?? MAX_MODE_WIDGET_SCRIPT_VERSION}`} />
        </Stack>

        {authContextQuery.isFetching ? <LinearProgress /> : null}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1 }}>
          <InfoCell label="Subject" value={authContextQuery.data?.subjectType ?? 'Not verified'} />
          <InfoCell label="Auth mode" value={authContextQuery.data?.authMode ?? selectedAuthPath} />
          <InfoCell label="Store link" value={authContextQuery.data?.deploymentId ?? 'Partner gated'} />
          <InfoCell label="Expires" value={formatDateTime(authContextQuery.data?.expiresAt ?? null)} />
        </Box>

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <Tooltip title="Open the real Max widget overlay for this store">
            <span>
              <Button variant="contained" startIcon={<OpenInNewOutlinedIcon />} onClick={openWidget} disabled={!widgetScriptReady || !canReadProductConfig}>
                Open Max
              </Button>
            </span>
          </Tooltip>
          <Tooltip title="Close the widget overlay">
            <span>
              <Button startIcon={<CloseOutlinedIcon />} onClick={closeWidget} disabled={!widgetScriptReady}>
                Close
              </Button>
            </span>
          </Tooltip>
          <Tooltip title="Send a real Max query through the selected access path">
            <span>
              <Button
                startIcon={<BoltOutlinedIcon />}
                onClick={() => smokeMutation.mutate()}
                disabled={!canReadProductConfig || smokeMutation.isPending}
              >
                Run smoke query
              </Button>
            </span>
          </Tooltip>
          <Tooltip title="Reset the current widget conversation">
            <span>
              <Button
                color="inherit"
                startIcon={<DeleteSweepOutlinedIcon />}
                onClick={() => resetMutation.mutate()}
                disabled={resetMutation.isPending}
              >
                Reset conversation
              </Button>
            </span>
          </Tooltip>
        </Stack>

        <Box sx={{ p: 1.25, border: 1, borderColor: 'divider', borderRadius: 1 }}>
          <Typography variant="caption" color="text.secondary">Live status</Typography>
          <Typography fontWeight={700}>{widgetLifecycleDetail}</Typography>
          {smokeMutation.data ? (
            <Typography color="text.secondary" sx={{ mt: 1 }}>{responseText(smokeMutation.data)}</Typography>
          ) : null}
        </Box>
      </Stack>
    </Paper>
  )
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ p: 1, border: 1, borderColor: 'divider', borderRadius: 1, minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
    </Box>
  )
}

function PanelHeading({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Box sx={{ width: 32, height: 32, borderRadius: 1.5, bgcolor: 'action.hover', display: 'grid', placeItems: 'center', color: 'primary.main' }}>
        {icon}
      </Box>
      <Typography variant="h3">{title}</Typography>
    </Stack>
  )
}
