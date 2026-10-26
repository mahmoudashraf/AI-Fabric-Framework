import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useEffect } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { consumeCustomerAuthReturn } from '../auth/customerAuthReturn'

export function CustomerAuthCallbackPage() {
  const auth = usePlatformAuth()
  const error = new URLSearchParams(window.location.search).get('error_description')
  useEffect(() => {
    if (!error && !auth.customerAuthLoading && auth.supabaseSession) window.location.replace(consumeCustomerAuthReturn())
  }, [error, auth.customerAuthLoading, auth.supabaseSession?.user.id])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ maxWidth: 480, p: 4 }}><Stack spacing={2}>
      <Typography variant="h5">Verify your account</Typography>
      {auth.customerAuthLoading ? <CircularProgress /> : error ? <Alert severity="error">{error}</Alert>
        : !auth.supabaseSession ? <Alert severity="warning">This verification link has expired or could not be completed. Sign in to request a new link.</Alert>
          : <Typography>Account verified. Returning to your sign-in flow...</Typography>}
      <Button href="/">Return to Console</Button>
    </Stack></Paper>
  </Box>
}
