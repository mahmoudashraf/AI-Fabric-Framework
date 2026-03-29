import LockRoundedIcon from '@mui/icons-material/LockRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { FormEvent, useEffect, useState } from 'react'

type PlatformLoginPageProps = {
  headerName: string
  errorMessage: string | null
  onSubmit: (apiKey: string) => void
}

export function PlatformLoginPage({ headerName, errorMessage, onSubmit }: PlatformLoginPageProps) {
  const [apiKey, setApiKey] = useState('')

  useEffect(() => {
    if (!errorMessage) {
      return
    }
    setApiKey('')
  }, [errorMessage])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit(apiKey)
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
                  Platform operator sign-in
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Enter the platform API key used for backend requests from this browser.
                </Typography>
              </Box>
            </Stack>

            {errorMessage ? <Alert severity="error">{errorMessage}</Alert> : null}

            <Box component="form" onSubmit={handleSubmit}>
              <Stack spacing={2}>
                <TextField
                  label={headerName}
                  type="password"
                  value={apiKey}
                  onChange={(event) => setApiKey(event.target.value)}
                  autoFocus
                  fullWidth
                />
                <Button type="submit" variant="contained" size="large" disabled={apiKey.trim().length === 0}>
                  Continue
                </Button>
              </Stack>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
