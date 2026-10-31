import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'
import { deskIdentityClient, deskSignInUrl, identityMethod } from '../auth/identityNavigation'

export function DeskIdentitySignInPage() {
  const auth = usePlatformAuth()
  const [method] = useState(() => identityMethod(window.location.search))
  const [client] = useState(() => {
    try { return { id: deskIdentityClient(window.location.pathname), error: null } }
    catch { return { id: null, error: 'The Desk sign-in client is invalid.' } }
  })
  const [error, setError] = useState<string | null>(null)
  const continueToDesk = () => {
    try {
      if (client.error) throw new Error(client.error)
      window.location.replace(deskSignInUrl(auth.customerConfig, client.id))
    }
    catch (ex) { setError(ex instanceof Error ? ex.message : 'Desk sign-in is unavailable.') }
  }
  useEffect(() => {
    if (method === 'login' && !auth.customerAuthLoading && auth.supabaseSession && auth.customerConfig) continueToDesk()
  }, [method, auth.customerAuthLoading, auth.supabaseSession?.user.id, auth.customerConfig])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ width: '100%', maxWidth: 480, p: { xs: 3, sm: 4 } }}><Stack spacing={3}>
      <Typography variant="overline" color="text.secondary">LoomAI Account</Typography>
      <Typography variant="h4">{method === 'recovery' ? 'Reset your password' : method === 'verify' ? 'Verify your email' : 'Sign in to Desk'}</Typography>
      {(error || client.error) && <Alert severity="error">{error || client.error}</Alert>}
      {auth.supabaseSession && <Alert severity="info">Signed in as {auth.supabaseSession.user.email}.
        <Button onClick={continueToDesk}>Continue to Desk</Button>
        <Button onClick={() => auth.signOut().catch(ex => setError(ex.message))}>Use another account</Button>
      </Alert>}
      {!client.error && <SupabaseIdentityForm allowRegistration initialMode={method} />}
      <Button href="/">LoomAI products</Button>
    </Stack></Paper>
  </Box>
}
