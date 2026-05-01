import { Alert, Box, CircularProgress, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSupabaseAuth } from '../auth/SupabaseProvider'

export function AuthCallbackPage() {
  const navigate = useNavigate()
  const { getSession } = useSupabaseAuth()
  const [timedOut, setTimedOut] = useState(false)

  useEffect(() => {
    const timeout = window.setTimeout(() => setTimedOut(true), 5000)
    getSession().then((session) => {
      if (session) {
        navigate('/', { replace: true })
      }
    })
    return () => window.clearTimeout(timeout)
  }, [getSession, navigate])

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
      <Stack spacing={2} alignItems="center">
        <CircularProgress size={28} />
        <Typography>Completing sign-in...</Typography>
        {timedOut ? <Alert severity="info">Still waiting for the auth session. Return to sign-in if this page does not continue.</Alert> : null}
      </Stack>
    </Box>
  )
}
