import { useEffect, useRef, useState } from 'react'
import { Alert, Box, CircularProgress, IconButton, Stack, Tooltip, Typography, useTheme } from '@mui/material'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { getPlatformApiBaseUrl } from '../../api/platformApi'
import type { SupportWorkspaceBootstrap } from '../../api/supportApi'
import type { GenericWorkspaceState } from '../../../../../experience-packs/generic-workspace-experience/src/types'
import { supportWorkspaceDocument } from './supportWorkspaceDocument'

export function SupportWorkspace({ caseId, bootstrap, readOnly, onStateChange }: {
  caseId: string; bootstrap: SupportWorkspaceBootstrap; readOnly: boolean; onStateChange(state: GenericWorkspaceState | null): void
}) {
  const frame = useRef<HTMLIFrameElement>(null)
  const auth = usePlatformAuth()
  const latestAuth = useRef(auth); latestAuth.current = auth
  const onState = useRef(onStateChange); onState.current = onStateChange
  const dark = useTheme().palette.mode === 'dark'
  const [nonce] = useState(() => crypto.randomUUID())
  const [error, setError] = useState<string | null>(null)
  const [ready, setReady] = useState(false)
  const [reload, setReload] = useState(0)
  const [document] = useState(supportWorkspaceDocument)
  const identityKey = `${auth.session?.customerId}:${auth.supabaseSession?.user.id ?? auth.session?.actorId}`
  const frameScope = JSON.stringify([caseId, bootstrap, dark, readOnly, identityKey, reload])
  const frameConfiguration = { type: 'loomai:support:configure', nonce, bootstrap, caseId, accountKey: identityKey,
    platformBaseUrl: getPlatformApiBaseUrl(), dark, readOnly }
  useEffect(() => {
    setReady(false); setError(null)
    const origin = window.location.origin
    const frameWindow = frame.current?.contentWindow
    let active = true
    const post = (message: Record<string, unknown>) => frameWindow?.postMessage({ nonce, ...message }, origin)
    const listener = async (event: MessageEvent) => {
      if (event.source !== frameWindow || event.origin !== origin || !event.data) return
      const message = event.data
      if (message.type === 'loomai:support:ready') {
        post(frameConfiguration)
        return
      }
      if (message.nonce !== nonce) return
      if (message.type === 'loomai:support:token-request' && typeof message.requestId === 'string') {
        try {
          const current = latestAuth.current
          if (`${current.session?.customerId}:${current.supabaseSession?.user.id ?? current.session?.actorId}` !== identityKey || !current.supabase) throw new Error('Sign in again to continue.')
          const { data, error: failure } = await current.supabase.auth.getSession()
          if (failure || !data.session || data.session.user.id !== current.supabaseSession?.user.id) throw new Error('Sign in again to continue.')
          const latest = latestAuth.current
          if (`${latest.session?.customerId}:${latest.supabaseSession?.user.id ?? latest.session?.actorId}` !== identityKey
            || latest.supabaseSession?.user.id !== data.session.user.id) throw new Error('Sign in again to continue.')
          if (active) post({ type: 'loomai:support:token-response', requestId: message.requestId, token: data.session.access_token })
        } catch {
          if (active) { post({ type: 'loomai:support:token-response', requestId: message.requestId }); setError('Your Console session is unavailable. Sign in again to continue.') }
        }
      }
      if (message.type === 'loomai:support:state') {
        const state = message.state as GenericWorkspaceState
        if (!state || !['initializing', 'ready', 'loading', 'error', 'destroyed'].includes(state.status)) return
        setReady(state.ready && state.conversationId === bootstrap.conversationId)
        if (state.status === 'ready' && state.conversationId) setError(null)
        if (state.error) setError(state.error.message)
        onState.current(state)
      }
      if (message.type === 'loomai:support:error') setError('The conversation is unavailable. Your saved request is unchanged.')
    }
    window.addEventListener('message', listener)
    return () => {
      active = false; post({ type: 'loomai:support:destroy' })
      window.removeEventListener('message', listener); onState.current(null)
    }
  }, [frameScope, nonce])
  const refresh = () => { setError(null); frame.current?.contentWindow?.postMessage({ type: 'loomai:support:refresh', nonce }, window.location.origin) }
  return <Box sx={{ minWidth: 0 }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ pb: 1.25 }}>
      <Typography variant="subtitle2">Conversation</Typography>
      <Tooltip title="Refresh conversation"><IconButton aria-label="Refresh support conversation" onClick={refresh} disabled={!ready}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>
    {error && <Alert severity="warning" sx={{ mb: 1.5 }} action={<IconButton aria-label="Reload support workspace" size="small" onClick={() => setReload(value => value + 1)}><RefreshRounded fontSize="small" /></IconButton>}>{error}</Alert>}
    {!ready && !error && <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 1 }}><CircularProgress size={15} /><Typography color="text.secondary" variant="body2">Opening your conversation...</Typography></Stack>}
    <Box component="iframe" key={frameScope} ref={frame} title="LoomAI integration support workspace" srcDoc={document}
      onLoad={() => frame.current?.contentWindow?.postMessage(frameConfiguration, window.location.origin)}
      sandbox="allow-scripts allow-same-origin" referrerPolicy="no-referrer"
      sx={{ display: 'block', width: '100%', height: 'min(760px, calc(100vh - 245px))', minHeight: 460, border: 1, borderColor: 'divider', borderRadius: 1 }} />
  </Box>
}
