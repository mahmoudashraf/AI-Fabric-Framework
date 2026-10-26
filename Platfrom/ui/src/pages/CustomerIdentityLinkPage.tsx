import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { startCustomerIdentityLink, usePlatformAuth } from '../auth/PlatformAuthProvider'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'

export function CustomerIdentityLinkPage() {
  const auth = usePlatformAuth()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => { startCustomerIdentityLink() }, [])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ p: 4, maxWidth: 500, width: '100%' }}><Stack spacing={3}>
      <Typography variant="h5">Connect your customer identity</Typography>
      <Typography color="text.secondary">Keep your existing {auth.session?.customerName ?? 'customer'} account and deployments. Connect your verified Supabase account once to use Console and LoomAI Desk.</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {auth.supabaseSession ? <>
        <Alert severity="info">Connect {auth.supabaseSession.user.email} to this customer account. This link cannot be reassigned.</Alert>
        <Button variant="contained" disabled={busy} onClick={async () => {
          setBusy(true); setError(null)
          try { await auth.linkCustomer() } catch (ex) { setError(ex instanceof Error ? ex.message : 'Identity linking failed.') }
          finally { setBusy(false) }
        }}>Connect verified identity</Button>
      </> : <SupabaseIdentityForm linking allowRegistration />}
      <Button onClick={() => { void auth.signOut() }}>Sign out</Button>
    </Stack></Paper>
  </Box>
}
