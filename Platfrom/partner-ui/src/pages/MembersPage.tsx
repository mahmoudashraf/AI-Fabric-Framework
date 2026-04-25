import { Paper, Stack, Typography } from '@mui/material'
import type { PartnerSession } from '../api/schemas'
import { PageHeader } from '../components/PageHeader'
import { titleize } from '../utils/format'

export function MembersPage({ session }: { session: PartnerSession }) {
  return (
    <>
      <PageHeader title="Members" subtitle="Workspace membership is Platform-owned. Invite and revocation flows are reserved for the next partner-admin slice." />
      <Paper sx={{ p: 2, maxWidth: 720 }}>
        <Stack spacing={0.75}>
          <Typography variant="h3">{session.member?.displayName ?? session.member?.email ?? 'Current member'}</Typography>
          <Typography color="text.secondary">{session.member?.email}</Typography>
          <Typography>{titleize(session.member?.role)} · {titleize(session.member?.status)}</Typography>
        </Stack>
      </Paper>
    </>
  )
}
