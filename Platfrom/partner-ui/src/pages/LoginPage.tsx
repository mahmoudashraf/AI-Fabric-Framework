import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import AppleIcon from '@mui/icons-material/Apple'
import GoogleIcon from '@mui/icons-material/Google'
import LinkedInIcon from '@mui/icons-material/LinkedIn'
import MailOutlineIcon from '@mui/icons-material/MailOutline'
import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Provider } from '@supabase/supabase-js'
import { strings } from '../i18n/en'
import { useSupabaseAuth } from '../auth/SupabaseProvider'

export function LoginPage() {
  const { configured, session, signInWithProvider, signInWithEmail } = useSupabaseAuth()
  const location = useLocation()
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/'
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  if (session) {
    return <Navigate to={from} replace />
  }

  const providerSignIn = async (provider: Provider) => {
    setBusy(provider)
    setError(null)
    try {
      await signInWithProvider(provider)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Sign-in failed.')
      setBusy(null)
    }
  }

  const emailSignIn = async () => {
    setBusy('email')
    setError(null)
    setSent(false)
    try {
      await signInWithEmail(email.trim())
      setSent(true)
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Email sign-in failed.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Paper sx={{ width: '100%', maxWidth: 420, p: 3 }}>
        <Stack spacing={2.25}>
          <Stack direction="row" spacing={1.25} alignItems="center">
            <Box sx={{ width: 32, height: 32, borderRadius: 2, bgcolor: 'primary.main', color: 'white', display: 'grid', placeItems: 'center', fontWeight: 800 }}>
              L
            </Box>
            <Typography variant="h2">{strings.appName}</Typography>
          </Stack>
          <Box>
            <Typography variant="h1" sx={{ fontSize: '1.25rem' }}>
              Sign in to your partner workspace
            </Typography>
            <Typography color="text.secondary" sx={{ mt: 0.75 }}>
              Implement verified LoomAI intelligence surfaces for approved client stores.
            </Typography>
          </Box>
          {!configured ? (
            <Alert severity="warning">Supabase sign-in is not configured for this environment.</Alert>
          ) : null}
          {error ? <Alert severity="error">{error}</Alert> : null}
          {sent ? <Alert severity="success">Check your email for a sign-in link.</Alert> : null}
          <Stack spacing={1}>
            <Button
              variant="contained"
              startIcon={busy === 'google' ? <CircularProgress size={16} color="inherit" /> : <GoogleIcon />}
              disabled={!configured || Boolean(busy)}
              onClick={() => providerSignIn('google')}
            >
              Continue with Google
            </Button>
            <Button
              variant="outlined"
              startIcon={busy === 'apple' ? <CircularProgress size={16} /> : <AppleIcon />}
              disabled={!configured || Boolean(busy)}
              onClick={() => providerSignIn('apple')}
            >
              Continue with Apple
            </Button>
            <Button
              variant="outlined"
              startIcon={busy === 'linkedin_oidc' ? <CircularProgress size={16} /> : <LinkedInIcon />}
              disabled={!configured || Boolean(busy)}
              onClick={() => providerSignIn('linkedin_oidc' as Provider)}
            >
              Continue with LinkedIn
            </Button>
          </Stack>
          <Divider>or</Divider>
          <Stack spacing={1.25}>
            <TextField
              label="Email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
            <Button
              variant="outlined"
              startIcon={busy === 'email' ? <CircularProgress size={16} /> : <MailOutlineIcon />}
              disabled={!configured || Boolean(busy) || !email.includes('@')}
              onClick={emailSignIn}
            >
              Send sign-in link
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            By continuing you agree to the Partner Terms and scoped implementation access policy.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  )
}
