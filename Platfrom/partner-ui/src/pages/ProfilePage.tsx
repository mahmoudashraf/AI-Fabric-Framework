import { Alert, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { updateProfile } from '../api/members'
import type { PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { titleize } from '../utils/format'

export function ProfilePage({ session }: { session: PartnerSession }) {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(session.member?.displayName ?? '')
  const [avatarUrl, setAvatarUrl] = useState(session.member?.avatarUrl ?? '')
  const [notice, setNotice] = useState('')
  const mutation = useMutation({
    mutationFn: () => updateProfile(api, { displayName, avatarUrl }),
    onSuccess: async () => {
      setNotice('Profile updated.')
      await queryClient.invalidateQueries({ queryKey: ['partner-session'] })
    },
  })

  return (
    <>
      <PageHeader title="Profile" subtitle="Partner identity is supplied by Supabase Auth; Platform stores the workspace authorization profile." />
      {notice ? <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice('')}>{notice}</Alert> : null}
      {mutation.isError ? <Alert severity="error" sx={{ mb: 2 }}>{mutation.error instanceof Error ? mutation.error.message : 'Profile update failed.'}</Alert> : null}
      <Paper sx={{ p: 2, maxWidth: 720 }}>
        <Stack spacing={2}>
          <TextField label="Display name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          <TextField label="Avatar URL" value={avatarUrl} onChange={(event) => setAvatarUrl(event.target.value)} />
          <Stack spacing={0.5}>
            <Typography><strong>Email:</strong> {session.member?.email ?? 'Not set'}</Typography>
            <Typography><strong>Role:</strong> {titleize(session.member?.role)}</Typography>
            <Typography><strong>Status:</strong> {titleize(session.member?.status)}</Typography>
          </Stack>
          <Button variant="contained" sx={{ alignSelf: 'flex-end' }} onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            Save profile
          </Button>
        </Stack>
      </Paper>
    </>
  )
}
