import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'

const checks = [
  'Free exposes AI search only.',
  'Starter surfaces are read-only embedded intelligence.',
  'Starter excludes order lookup and governed actions.',
  'Answers are grounded in store catalog, collection, page, policy, or article content.',
  'Evidence output excludes secrets, raw runtime data, provider data, and operator notes.',
]

export function VerificationPacksPage() {
  const navigate = useNavigate()
  return (
    <>
      <PageHeader title="Verification packs" subtitle="Partner-facing verification contracts for Free and Starter launch readiness." />
      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography variant="h3">Starter launch pack</Typography>
        <Stack component="ol" spacing={1} sx={{ pl: 3 }}>
          {checks.map((check) => (
            <Typography component="li" key={check}>{check}</Typography>
          ))}
        </Stack>
      </Paper>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Run path</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Select an approved store workspace, run the store-level pack, export the merchant-safe evidence bundle, then escalate blockers with the bundle attached.
          </Typography>
          <Button sx={{ mt: 2 }} onClick={() => navigate('/stores')}>Choose store</Button>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Output contract</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Evidence is product-facing only: screenshots, questions, answers, status summaries, and setup notes.
          </Typography>
          <Button sx={{ mt: 2 }} onClick={() => navigate('/evidence')}>Open evidence</Button>
        </Paper>
      </Box>
    </>
  )
}
