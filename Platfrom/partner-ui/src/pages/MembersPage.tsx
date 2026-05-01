import { Alert, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { listMembers, updateMember } from '../api/members'
import type { PartnerMember, PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { titleize } from '../utils/format'

const roles = ['PARTNER_ADMIN', 'PARTNER_IMPLEMENTER', 'PARTNER_DEVELOPER', 'PARTNER_SUPPORT']
const statuses = ['ACTIVE', 'SUSPENDED', 'REVOKED']

export function MembersPage({ session }: { session: PartnerSession }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState('')
  const membersQuery = useQuery({ queryKey: ['members'], queryFn: () => listMembers(api), enabled: session.permissions.includes('WORKSPACE_ADMIN') })
  const mutation = useMutation({
    mutationFn: ({ member, role, status }: { member: PartnerMember; role?: string; status?: string }) => updateMember(api, member.id, { role, status }),
    onSuccess: async () => {
      setNotice('Member updated.')
      await queryClient.invalidateQueries({ queryKey: ['members'] })
      await queryClient.invalidateQueries({ queryKey: ['partner-session'] })
    },
  })

  if (!session.permissions.includes('WORKSPACE_ADMIN')) {
    return <Alert severity="warning">Partner admin permission is required to manage workspace members.</Alert>
  }

  const columns: DataColumn<PartnerMember>[] = [
    {
      key: 'member',
      header: 'Member',
      render: (member) => (
        <Stack spacing={0.25}>
          <Typography fontWeight={700}>{member.displayName ?? member.email}</Typography>
          <Typography variant="caption" color="text.secondary">{member.email}</Typography>
        </Stack>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (member) => (
        <TextField select size="small" value={member.role} onChange={(event) => mutation.mutate({ member, role: event.target.value })}>
          {roles.map((role) => <MenuItem key={role} value={role}>{titleize(role)}</MenuItem>)}
        </TextField>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (member) => (
        <Stack direction="row" spacing={1} alignItems="center">
          <StatusChip status={member.status} />
          <TextField select size="small" value={member.status} onChange={(event) => mutation.mutate({ member, status: event.target.value })}>
            {statuses.map((status) => <MenuItem key={status} value={status}>{titleize(status)}</MenuItem>)}
          </TextField>
        </Stack>
      ),
    },
    { key: 'verified', header: 'Email', render: (member) => <Typography>{member.emailVerified ? 'Verified' : 'Unverified'}</Typography> },
  ]

  return (
    <>
      <PageHeader title="Members" subtitle="Workspace members are enforced by Platform authorization after Supabase identity verification." />
      {notice ? <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice('')}>{notice}</Alert> : null}
      {mutation.isError ? <Alert severity="error" sx={{ mb: 2 }}>{mutation.error instanceof Error ? mutation.error.message : 'Member update failed.'}</Alert> : null}
      <Paper sx={{ p: 2 }}>
        <DataTable columns={columns} rows={membersQuery.data ?? []} getRowKey={(row) => row.id} loading={membersQuery.isLoading} />
      </Paper>
    </>
  )
}
