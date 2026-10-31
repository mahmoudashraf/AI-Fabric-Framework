import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { consumeCustomerAuthReturn, identitySignInPath } from '../auth/customerAuthReturn'
import { emailCallbackError } from '../auth/identityNavigation'

export function CustomerAuthCallbackPage() {
  const auth = usePlatformAuth()
  const [callback] = useState(() => ({ error: emailCallbackError(window.location.search, window.location.hash),
    hasCredential: new URLSearchParams(window.location.search).has('code') || new URLSearchParams(window.location.hash.slice(1)).has('access_token') }))
  const error = callback.error || auth.customerAuthError?.message || (!callback.hasCredential ? 'Open the newest verification or sign-in email to continue.' : null)
  useEffect(() => {
    if (!error && !auth.customerAuthLoading && auth.supabaseSession) window.location.replace(consumeCustomerAuthReturn())
  }, [error, auth.customerAuthLoading, auth.supabaseSession?.user.id])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ maxWidth: 480, p: 4 }}><Stack spacing={2}>
      <Typography variant="h5">Complete your sign-in</Typography>
      {auth.customerAuthLoading ? <CircularProgress /> : error ? <Alert severity="error">{error}</Alert>
        : !auth.supabaseSession ? <Alert severity="warning">This email link has expired or could not be completed. Request a new link and open it in the browser where you requested it.</Alert>
          : <Typography>Account verified. Returning to your sign-in flow...</Typography>}
      <Button href={identitySignInPath()}>Return to sign in</Button>
    </Stack></Paper>
  </Box>
}
