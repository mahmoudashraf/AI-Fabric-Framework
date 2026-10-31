import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { consumeCustomerAuthReturn, identitySignInPath } from '../auth/customerAuthReturn'
import { emailCallbackError } from '../auth/identityNavigation'
import { useEmailLinkVerification } from '../auth/useEmailLinkVerification'

export function CustomerAuthCallbackPage() {
  const auth = usePlatformAuth()
  const link = useEmailLinkVerification('email')
  const [callback] = useState(() => ({ error: emailCallbackError(window.location.search, window.location.hash),
    hasCredential: new URLSearchParams(window.location.search).has('code') || new URLSearchParams(window.location.hash.slice(1)).has('access_token') }))
  const error = callback.error || link.error || auth.customerAuthError?.message
    || (!callback.hasCredential && !link.present ? 'Open the newest verification or sign-in email to continue.' : null)
  useEffect(() => {
    if (!error && !link.pending && !auth.customerAuthLoading && auth.supabaseSession
      && (!link.present || link.subject === auth.supabaseSession.user.id)) window.location.replace(consumeCustomerAuthReturn())
  }, [error, link.pending, link.present, link.subject, auth.customerAuthLoading, auth.supabaseSession?.user.id])
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ maxWidth: 480, p: 4 }}><Stack spacing={2}>
      <Typography variant="h5">Complete your sign-in</Typography>
      {auth.customerAuthLoading || link.pending ? <CircularProgress /> : error ? <Alert severity="error">{error}</Alert>
        : !auth.supabaseSession ? <Alert severity="warning">This email link has expired or could not be completed. Request a new link and open it in the browser where you requested it.</Alert>
          : <Typography>Account verified. Returning to your sign-in flow...</Typography>}
      <Button href={identitySignInPath()}>Return to sign in</Button>
    </Stack></Paper>
  </Box>
}
