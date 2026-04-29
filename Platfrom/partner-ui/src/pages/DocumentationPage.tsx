import { Box, Paper, Stack, Typography } from '@mui/material'
import { PageHeader } from '../components/PageHeader'

const sections = [
  { title: 'Tier truth', body: 'Free is AI search only. Starter is read-only embedded intelligence. Elite is required for verified governed actions.' },
  { title: 'Access model', body: 'Signup creates an empty workspace. Store access requires merchant approval or assignment and can be revoked.' },
  { title: 'Partner boundary', body: 'Partners handle implementation, setup, verification, evidence, and escalation. Deployment controls remain operator-only.' },
  { title: 'Support handoff', body: 'Escalations are structured cases with partner-visible replies and merchant-safe evidence references.' },
]

export function DocumentationPage() {
  return (
    <>
      <PageHeader title="Documentation" subtitle="Operational reference for scoped Partner Enablement." />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 2 }}>
        {sections.map((section) => (
          <Paper key={section.title} sx={{ p: 2 }}>
            <Stack spacing={1}>
              <Typography variant="h3">{section.title}</Typography>
              <Typography color="text.secondary">{section.body}</Typography>
            </Stack>
          </Paper>
        ))}
      </Box>
    </>
  )
}
