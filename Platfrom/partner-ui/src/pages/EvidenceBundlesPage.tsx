import { Paper, Stack, Typography } from '@mui/material'
import { PageHeader } from '../components/PageHeader'

export function EvidenceBundlesPage() {
  return (
    <>
      <PageHeader title="Evidence bundles" subtitle="Merchant-safe proof packets for verification, launch review, and support escalation." />
      <Paper sx={{ p: 2 }}>
        <Typography variant="h3">Evidence metadata shell</Typography>
        <Stack component="ul" spacing={1} sx={{ pl: 3 }}>
          <Typography component="li">Storefront screenshots and URLs</Typography>
          <Typography component="li">Question, answer, and source-state summary</Typography>
          <Typography component="li">Verification result and failure signs</Typography>
          <Typography component="li">No secrets, raw runtime data, provider data, or operator-only notes</Typography>
        </Stack>
      </Paper>
    </>
  )
}
