import LockRoundedIcon from '@mui/icons-material/LockRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { FormEvent, useEffect, useState } from 'react'

type PlatformLoginPageProps = {
  headerName: string
  sessionAuthEnabled: boolean
  apiKeyAuthEnabled: boolean
  errorMessage: string | null
  onPasswordSubmit: (email: string, password: string) => Promise<void>
  onApiKeySubmit: (apiKey: string) => void
}

export function PlatformLoginPage({
  headerName,
  sessionAuthEnabled,
  apiKeyAuthEnabled,
  errorMessage,
  onPasswordSubmit,
  onApiKeySubmit,
}: PlatformLoginPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [submittingPassword, setSubmittingPassword] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)

  useEffect(() => {
    if (!errorMessage) {
      return
    }
    setPassword('')
    setApiKey('')
    setLocalError(null)
  }, [errorMessage])

  const handlePasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmittingPassword(true)
    setLocalError(null)
    try {
      await onPasswordSubmit(email, password)
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : 'Platform sign-in failed.')
    } finally {
      setSubmittingPassword(false)
    }
  }

  const handleApiKeySubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onApiKeySubmit(apiKey)
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        px: 3,
        background:
          'radial-gradient(circle at top left, rgba(75,156,211,0.22), transparent 38%), linear-gradient(180deg, #0c1322, #101827)',
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 520, borderRadius: 4, boxShadow: '0 24px 80px rgba(0,0,0,0.28)' }}>
        <CardContent sx={{ p: 4 }}>
          <Stack spacing={3}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Box
                sx={{
                  width: 48,
                  height: 48,
                  borderRadius: 2,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                }}
              >
                <LockRoundedIcon />
              </Box>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  Platform sign-in
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Use a platform user session by default. API key login remains available as an operator fallback.
                </Typography>
              </Box>
            </Stack>

            {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}
            {!errorMessage && localError ? <Alert severity="error">{localError}</Alert> : null}

            {sessionAuthEnabled ? (
              <Box component="form" onSubmit={handlePasswordSubmit}>
                <Stack spacing={2}>
                  <TextField
                    label="Email"
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    autoFocus
                    fullWidth
                  />
                  <TextField
                    label="Password"
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    fullWidth
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    disabled={submittingPassword || email.trim().length === 0 || password.trim().length === 0}
                  >
                    {submittingPassword ? 'Signing in...' : 'Sign in'}
                  </Button>
                </Stack>
              </Box>
            ) : null}

            {sessionAuthEnabled && apiKeyAuthEnabled ? <Divider>Or use API key</Divider> : null}

            {apiKeyAuthEnabled ? (
              <Box component="form" onSubmit={handleApiKeySubmit}>
                <Stack spacing={2}>
                  <Typography variant="body2" color="text.secondary">
                    Fallback access for internal operators and local/dev automation.
                  </Typography>
                  <TextField
                    label={headerName}
                    type="password"
                    value={apiKey}
                    onChange={(event) => setApiKey(event.target.value)}
                    fullWidth
                    autoFocus={!sessionAuthEnabled}
                  />
                  <Button type="submit" variant="outlined" size="large" disabled={apiKey.trim().length === 0}>
                    Continue with API key
                  </Button>
                </Stack>
              </Box>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
