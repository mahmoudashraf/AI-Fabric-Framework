import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'
import { requireDeskAuthorization, requireDeskCallback, type DeskAuthorizationDetails } from '../auth/deskOAuthPolicy'
import { authorizeDeskOnce } from '../auth/deskAuthorizationAttempt'

function authorizationError(error: unknown) {
  if (error && typeof error === 'object' && 'status' in error && typeof error.status === 'number' && error.status >= 500) {
    return `Supabase authorization failed (${error.status}). Open LoomAI Desk again to start a new request.`
  }
  return error instanceof Error ? error.message : 'Authorization failed.'
}

export function DeskOAuthAuthorizationPage() {
  const auth = usePlatformAuth()
  const authorizationId = new URLSearchParams(window.location.search).get('authorization_id') ?? ''
  const [details, setDetails] = useState<DeskAuthorizationDetails | null>(null)
  const [existingRedirect, setExistingRedirect] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    let active = true
    setDetails(null); setExistingRedirect(null); setError(null)
    if (!authorizationId) { setError('Missing OAuth authorization request. Open LoomAI Desk again.'); return }
    if (!auth.supabase || !auth.supabaseSession || !auth.customerConfig) return
    setBusy(true)
    const client = auth.supabase, config = auth.customerConfig
    void (async () => {
      try {
        const user = await client.auth.getUser()
        if (user.error) throw user.error
        if (!user.data.user?.email_confirmed_at) throw new Error('Verify your email before opening LoomAI Desk.')
        const { data, error } = await client.auth.oauth.getAuthorizationDetails(authorizationId)
        if (error) throw error
        if (!data) throw new Error('Authorization request is unavailable.')
        if (!active) return
        if ('authorization_id' in data) {
          requireDeskAuthorization(config, data, authorizationId)
          if (active) setDetails(data)
          const redirect = await authorizeDeskOnce(authorizationId, `${config.projectUrl}:${user.data.user.id}:${data.client.id}`, async () => {
            const approved = await client.auth.oauth.approveAuthorization(authorizationId, { skipBrowserRedirect: true })
            if (approved.error) throw approved.error
            if (!approved.data) throw new Error('Authorization decision was not returned.')
            return requireDeskCallback(config, approved.data.redirect_url, data.redirect_uri)
          })
          if (active) window.location.replace(redirect)
        } else {
          const redirect = requireDeskCallback(config, data.redirect_url)
          if (active) setExistingRedirect(redirect)
          if (active) window.location.replace(redirect)
        }
      } catch (ex) { if (active) setError(authorizationError(ex)) }
      finally { if (active) setBusy(false) }
    })()
    return () => { active = false }
  }, [authorizationId, auth.supabase, auth.supabaseSession?.user.id, auth.customerConfig])

  const cancel = async () => {
    if (!auth.supabase || !auth.customerConfig) return
    setBusy(true); setError(null)
    try {
      if (!details) throw new Error('A configured Desk authorization request is required.')
      requireDeskAuthorization(auth.customerConfig, details, authorizationId)
      const { data, error } = await auth.supabase.auth.oauth.denyAuthorization(authorizationId, { skipBrowserRedirect: true })
      if (error) throw error
      if (!data) throw new Error('Authorization decision was not returned.')
      window.location.assign(requireDeskCallback(auth.customerConfig, data.redirect_url, details.redirect_uri))
    } catch (ex) { setError(ex instanceof Error ? ex.message : 'Authorization failed.'); setBusy(false) }
  }
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ maxWidth: 480, width: '100%', p: 4 }}><Stack spacing={3}>
      <Typography variant="overline" color="primary">Loom AI Labs</Typography>
      <Typography variant="h5">Open LoomAI Desk</Typography>
      {auth.customerAuthLoading ? <CircularProgress /> : !auth.customerConfig?.enabled ? <Alert severity="warning">Desk sign-in is not configured.</Alert>
        : !auth.supabaseSession ? <><SupabaseIdentityForm allowRegistration /><Button href="/">Cancel</Button></> : <>
          <Typography color="text.secondary">Signed in as {auth.supabaseSession.user.email}</Typography>
          {details && <Typography>Share your verified identity with LoomAI Desk ({details.scope}). Deployment access is managed separately.</Typography>}
          {(details || existingRedirect) && !error && <Typography>Opening your authorized Desk workspace...</Typography>}
          {details && <Button disabled={busy} onClick={() => { void cancel() }}>Cancel</Button>}
          <Button disabled={busy} onClick={() => { void auth.signOut() }}>Use another account</Button>
        </>}
      {busy && <CircularProgress size={24} />}
      {error && <Alert severity="error">{error}</Alert>}
      {error && !details && <Button href="/">Cancel</Button>}
    </Stack></Paper>
  </Box>
}
