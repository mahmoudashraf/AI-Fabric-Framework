import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import WorkspacePremiumRoundedIcon from '@mui/icons-material/WorkspacePremiumRounded'
import {
  Alert,
  Box,
  Checkbox,
  Chip,
  FormControlLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  fetchPlatformPartnerMembers,
  updatePlatformPartnerMember,
  type PlatformPartnerMemberSummary,
} from '../api/platformApi'

const roles = ['PARTNER_ADMIN', 'PARTNER_IMPLEMENTER', 'PARTNER_DEVELOPER', 'PARTNER_SUPPORT']
const statuses = ['ACTIVE', 'SUSPENDED', 'REVOKED']
const privileges = [
  {
    key: 'PACKAGE_TRIAL_ACTIVATE',
    label: 'Package trial activation',
    detail: 'Allows activating Starter or Elite trials for assigned Shopify stores and manually deactivating past-due trials.',
  },
]

function titleize(value: string) {
  return value
    .toLowerCase()
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatDateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : 'Never'
}

export function PartnerPrivilegesPage() {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [notice, setNotice] = useState('')
  const membersQuery = useQuery({ queryKey: ['platform-partner-members'], queryFn: fetchPlatformPartnerMembers })
  const mutation = useMutation({
    mutationFn: ({ member, role, status, privilegeList }: {
      member: PlatformPartnerMemberSummary
      role?: string
      status?: string
      privilegeList?: string[]
    }) => updatePlatformPartnerMember(member.id, {
      role: role ?? member.role,
      status: status ?? member.status,
      privileges: privilegeList ?? member.privileges,
    }),
    onSuccess: async () => {
      setNotice('Partner privilege configuration updated.')
      await queryClient.invalidateQueries({ queryKey: ['platform-partner-members'] })
    },
  })

  const members = membersQuery.data ?? []
  const filteredMembers = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) {
      return members
    }
    return members.filter((member) => [
      member.partnerAccountName,
      member.email,
      member.displayName ?? '',
      member.role,
      member.status,
      ...member.privileges,
    ].some((value) => value.toLowerCase().includes(needle)))
  }, [members, query])

  const togglePrivilege = (member: PlatformPartnerMemberSummary, privilege: string) => {
    const next = member.privileges.includes(privilege)
      ? member.privileges.filter((value) => value !== privilege)
      : [...member.privileges, privilege]
    mutation.mutate({ member, privilegeList: next })
  }

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <Box>
          <Typography variant="h3">Partner Privileges</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            Platform-controlled privileges gate partner actions that change commercial product state.
          </Typography>
        </Box>
        <Chip icon={<WorkspacePremiumRoundedIcon />} label="Package trials require explicit privilege" color="primary" variant="outlined" />
      </Stack>

      {notice ? <Alert severity="success" onClose={() => setNotice('')}>{notice}</Alert> : null}
      {mutation.isError ? <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Partner privilege update failed.'}</Alert> : null}

      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ManageAccountsRoundedIcon color="primary" />
            <Box>
              <Typography sx={{ fontWeight: 800 }}>Partner member control</Typography>
              <Typography variant="body2" color="text.secondary">
                Trial activation stays unavailable until a Platform admin grants the member privilege.
              </Typography>
            </Box>
          </Stack>
          <TextField size="small" label="Search partners" value={query} onChange={(event) => setQuery(event.target.value)} sx={{ minWidth: { md: 320 } }} />
        </Stack>
      </Paper>

      {membersQuery.isLoading ? <LinearProgress /> : null}
      {membersQuery.isError ? <Alert severity="error">{membersQuery.error instanceof Error ? membersQuery.error.message : 'Partner members could not be loaded.'}</Alert> : null}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', xl: '1fr 1fr' }, gap: 2 }}>
        {filteredMembers.map((member) => (
          <Paper key={member.id} sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
                <Box>
                  <Typography sx={{ fontWeight: 800 }}>{member.displayName ?? member.email}</Typography>
                  <Typography variant="body2" color="text.secondary">{member.email}</Typography>
                  <Typography variant="caption" color="text.secondary">{member.partnerAccountName}</Typography>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip size="small" label={titleize(member.status)} color={member.status === 'ACTIVE' ? 'success' : 'warning'} />
                  <Chip size="small" label={titleize(member.role)} variant="outlined" />
                </Stack>
              </Stack>

              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
                <TextField
                  select
                  size="small"
                  label="Role"
                  value={member.role}
                  disabled={mutation.isPending}
                  onChange={(event) => mutation.mutate({ member, role: event.target.value })}
                >
                  {roles.map((role) => <MenuItem key={role} value={role}>{titleize(role)}</MenuItem>)}
                </TextField>
                <TextField
                  select
                  size="small"
                  label="Status"
                  value={member.status}
                  disabled={mutation.isPending}
                  onChange={(event) => mutation.mutate({ member, status: event.target.value })}
                >
                  {statuses.map((status) => <MenuItem key={status} value={status}>{titleize(status)}</MenuItem>)}
                </TextField>
              </Box>

              <Stack spacing={1}>
                {privileges.map((privilege) => (
                  <FormControlLabel
                    key={privilege.key}
                    control={<Checkbox checked={member.privileges.includes(privilege.key)} onChange={() => togglePrivilege(member, privilege.key)} />}
                    label={(
                      <Box>
                        <Typography sx={{ fontWeight: 700 }}>{privilege.label}</Typography>
                        <Typography variant="caption" color="text.secondary">{privilege.detail}</Typography>
                      </Box>
                    )}
                    disabled={mutation.isPending || member.status !== 'ACTIVE'}
                  />
                ))}
              </Stack>

              <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                <Typography variant="caption" color="text.secondary">Last login: {formatDateTime(member.lastLoginAt)}</Typography>
                <Chip size="small" variant="outlined" label={`${member.effectivePermissions.length} effective permissions`} />
              </Stack>
            </Stack>
          </Paper>
        ))}
      </Box>
    </Stack>
  )
}
