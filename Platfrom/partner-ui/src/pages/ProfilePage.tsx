import { Paper, Stack, Typography } from '@mui/material'
import type { PartnerSession } from '../api/schemas'
import { PageHeader } from '../components/PageHeader'
import { titleize } from '../utils/format'

export function ProfilePage({ session }: { session: PartnerSession }) {
  return (
    <>
      <PageHeader title="Profile" subtitle="Partner identity and role supplied by Supabase Auth and Platform authorization." />
      <Paper sx={{ p: 2, maxWidth: 640 }}>
        <Stack spacing={1}>
          <Typography><strong>Name:</strong> {session.member?.displayName ?? 'Not set'}</Typography>
          <Typography><strong>Email:</strong> {session.member?.email ?? 'Not set'}</Typography>
          <Typography><strong>Role:</strong> {titleize(session.member?.role)}</Typography>
          <Typography><strong>Status:</strong> {titleize(session.member?.status)}</Typography>
        </Stack>
      </Paper>
    </>
  )
}
