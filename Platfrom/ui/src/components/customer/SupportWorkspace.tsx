import { useEffect, useRef, useState } from 'react'
import { Alert, Box, CircularProgress, IconButton, Stack, Typography, useTheme } from '@mui/material'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { mountInstallation, SupportApiError } from '../../../../../experience-packs/support-experience/src/index'
import type { SupportChatApi, SupportExperienceController, SupportHostAdapter, SupportMountContext } from '../../../../../experience-packs/support-experience/src/types'
import { createIsolatedWorkspaceHost, type IsolatedFrameHost } from '../../../../../max-mode-widget/src/installer/isolatedHost'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { getPlatformApiBaseUrl } from '../../api/platformApi'
import { createConsoleSupportApi, getSupportWorkspace, supportErrorMessage, type ConsoleSupportWorkspaceBootstrap } from '../../api/supportApi'
import { useCustomerSupport } from './CustomerSupportContext'

function approvedBootstrap(value: ConsoleSupportWorkspaceBootstrap, requestId?: string) {
  const origin = new URL(getPlatformApiBaseUrl(), document.baseURI).origin
  const runtime = new URL(value.apiBaseUrl, origin)
  if (typeof value.conversationId !== 'string' || !value.conversationId.trim()) throw new Error('A support conversation scope is required.')
  const expected = requestId ? `/api/support/requests/${encodeURIComponent(requestId)}/runtime/api`
    : `/api/support/conversations/${encodeURIComponent(value.conversationId)}/runtime/api`
  if (runtime.origin !== origin || runtime.pathname !== expected || runtime.username || runtime.password || runtime.search || runtime.hash)
    throw new Error('Invalid support runtime target.')
  for (const [asset, integrity] of [[value.workspaceAssetUrl, value.workspaceIntegrity], [value.experienceModuleUrl, value.experienceIntegrity]]) {
    const url = new URL(asset, origin)
    if (url.origin !== origin || !url.pathname.startsWith('/api/public/ai-workspace/') || url.username || url.password || url.search || url.hash || !/^sha384-[A-Za-z0-9+/=]+$/.test(integrity))
      throw new Error('Invalid support workspace asset.')
  }
  if (!value.installationId || !value.assignmentRevision || !value.principalNamespace) throw new Error('Incomplete support installation.')
  return { ...value, apiBaseUrl: runtime.href, workspaceAssetUrl: new URL(value.workspaceAssetUrl, origin).href }
}

/** One real pack controller and one isolated chat frame for the signed-in owner, independent of routes. */
export function SupportWorkspace({ visible, layer }: { visible: boolean; layer: number }) {
  const container = useRef<HTMLDivElement>(null)
  const auth = usePlatformAuth()
  const latestAuth = useRef(auth); latestAuth.current = auth
  const support = useCustomerSupport()
  const { attachController, selectedRequest } = support
  const dark = useTheme().palette.mode === 'dark'
  const latestTheme = useRef(dark); latestTheme.current = dark
  const latestVisibility = useRef(visible); latestVisibility.current = visible
  const latestLayer = useRef(layer); latestLayer.current = layer
  const workspaceFrame = useRef<IsolatedFrameHost<SupportChatApi> | null>(null)
  const current = useRef<SupportExperienceController | null>(null)
  const [ready, setReady] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [retry, setRetry] = useState(0)
  const identity = `${auth.session?.customerId}:${auth.session?.actorId}:${auth.supabaseSession?.user.id ?? ''}`
  useEffect(() => {
    let active = true
    let frame: IsolatedFrameHost<SupportChatApi> | undefined
    let mounted: SupportExperienceController | undefined
    let adapter: ReturnType<typeof createConsoleSupportApi> | undefined
    let releaseState: (() => void) | undefined
    const abort = new AbortController()
    const assertOwner = () => {
      const latest = latestAuth.current
      if (!active || `${latest.session?.customerId}:${latest.session?.actorId}:${latest.supabaseSession?.user.id ?? ''}` !== identity || !latest.supabaseSession)
        throw new SupportApiError(401, 'IDENTITY_CHANGED', 'Sign in again before accessing support.')
    }
    const token = async () => {
      assertOwner()
      const owner = latestAuth.current
      if (!owner.supabase) throw new SupportApiError(401, 'SIGN_IN_REQUIRED', 'Sign in to continue.')
      const result = await owner.supabase.auth.getSession()
      assertOwner()
      if (result.error || !result.data.session || result.data.session.user.id !== latestAuth.current.supabaseSession?.user.id)
        throw new SupportApiError(401, 'SIGN_IN_REQUIRED', 'Sign in again to continue.')
      return result.data.session.access_token
    }
    setReady(false); setError(null)
    const start = async () => {
      assertOwner()
      const initial = approvedBootstrap(await getSupportWorkspace(undefined, abort.signal))
      assertOwner()
      let first: ConsoleSupportWorkspaceBootstrap | undefined = initial
      const fetchWorkspace = async (requestId?: string) => {
        assertOwner()
        const value = !requestId && first ? first : approvedBootstrap(await getSupportWorkspace(requestId, abort.signal), requestId)
        if (!requestId) first = undefined
        assertOwner()
        if (value.installationId !== initial.installationId || value.assignmentRevision !== initial.assignmentRevision)
          throw new SupportApiError(409, 'ASSIGNMENT_CHANGED', 'The support assignment changed. Reload support before continuing.')
        return value
      }
      frame = await createIsolatedWorkspaceHost<SupportChatApi>({ url: initial.workspaceAssetUrl, integrity: initial.workspaceIntegrity }, { layer: latestLayer.current })
      if (!active) { frame.destroy(); return }
      workspaceFrame.current = frame
      const host: SupportHostAdapter = { runtimeCredentialMode: 'host-mediated',
        apiBaseUrl: new URL('/api/support', new URL(getPlatformApiBaseUrl(), document.baseURI)).href,
        getIdentityAccessToken: token,
        fetchWorkspace, onRequestSelected: request => { if (active) selectedRequest(request?.id ?? null) } }
      const context: SupportMountContext = {
        installationId: initial.installationId, assignmentRevision: initial.assignmentRevision,
        deliveryMode: 'embedded', connectionMode: 'backend-mediated-private-runtime',
        configuration: { ...initial.configuration, theme: { ...(initial.configuration.theme as object), darkMode: latestTheme.current } },
        widgetConfig: { storageNamespace: `${initial.installationId}:${initial.assignmentRevision}:${identity}`,
          features: { cart: false, debug: false, conversations: false } },
        hostSession: { namespace: initial.principalNamespace, accountKey: identity },
        maxMode: frame.maxMode, workspaceFrame: frame,
        presentation: { mode: 'page', container: container.current! },
        credentials: { getIdentityAccessToken: token, getIdentity: () => {
          try { assertOwner(); return { status: 'authenticated', subjectKey: identity } }
          catch { return { status: 'anonymous' } }
        } },
        supportHost: host,
        refreshAssignment: async () => { await fetchWorkspace() },
      }
      adapter = createConsoleSupportApi(context, host, assertOwner)
      host.api = adapter.api
      mounted = await mountInstallation(context)
      if (!active) { mounted.destroy(); adapter.destroy(); return }
      current.current = mounted
      mounted.setVisible(latestVisibility.current)
      mounted.setTheme(latestTheme.current ? 'dark' : 'light')
      let attached = false
      releaseState = mounted.onStateChange(state => {
        if (active && !state.busy && !attached) { attached = true; attachController(mounted!) }
      })
      setReady(true)
    }
    void start().catch(cause => {
      mounted?.destroy(); if (!mounted) frame?.destroy(); adapter?.destroy()
      if (active) setError(supportErrorMessage(cause))
    })
    return () => {
      active = false; abort.abort(); releaseState?.(); attachController(null)
      current.current = null; workspaceFrame.current = null; mounted?.destroy(); if (!mounted) frame?.destroy(); adapter?.destroy()
    }
  }, [identity, retry, attachController, selectedRequest])
  useEffect(() => { current.current?.setVisible(visible) }, [visible])
  useEffect(() => { current.current?.setTheme(dark ? 'dark' : 'light') }, [dark])
  useEffect(() => { workspaceFrame.current?.setLayer(layer) }, [layer])
  return <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0 }}>
    {error && <Alert severity="warning" action={<IconButton aria-label="Reload support workspace" size="small" onClick={() => setRetry(value => value + 1)}><RefreshRounded fontSize="small" /></IconButton>}>{error}</Alert>}
    {!ready && !error && <Stack direction="row" spacing={1} alignItems="center" sx={{ p: 2 }}><CircularProgress size={16} /><Typography variant="body2">Opening support...</Typography></Stack>}
    <Box id="console-support-experience" ref={container} sx={{ flex: 1, minHeight: 0, minWidth: 0, display: 'flex', '& > [data-loomai-support]': { flex: 1, minHeight: 0, minWidth: 0, width: '100%' } }} />
  </Box>
}
