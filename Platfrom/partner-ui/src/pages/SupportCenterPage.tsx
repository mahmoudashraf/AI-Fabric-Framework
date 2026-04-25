import { Alert, Box, Button, Typography } from '@mui/material'
import SupportAgentOutlinedIcon from '@mui/icons-material/SupportAgentOutlined'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { listEscalations } from '../api/escalations'
import type { PartnerEscalation, PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDate, formatDateTime, titleize } from '../utils/format'

export function SupportCenterPage({ session }: { session: PartnerSession }) {
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const escalationsQuery = useQuery({ queryKey: ['escalations'], queryFn: () => listEscalations(api), enabled: !session.signupRequired })
  const columns: DataColumn<PartnerEscalation>[] = [
    { key: 'title', header: 'Case', render: (row) => <Button sx={{ justifyContent: 'flex-start', px: 0 }} onClick={() => navigate(`/support/${row.id}`)}>{row.title}</Button> },
    { key: 'store', header: 'Store', render: (row) => <Typography color="text.secondary">{row.shopDomain ?? 'Store scoped'}</Typography> },
    { key: 'severity', header: 'Severity', render: (row) => <Typography>{titleize(row.severity)}</Typography> },
    { key: 'status', header: 'Status', render: (row) => <StatusChip status={row.status} /> },
    { key: 'due', header: 'Due', render: (row) => <Typography color="text.secondary">{formatDate(row.dueAt)}</Typography> },
    { key: 'updated', header: 'Updated', render: (row) => <Typography color="text.secondary">{formatDateTime(row.updatedAt)}</Typography> },
  ]

  if (session.signupRequired) {
    return <Alert severity="info">Complete workspace setup before opening support cases.</Alert>
  }

  return (
    <>
      <PageHeader title="Support" subtitle="Structured escalation cases with partner-visible replies and evidence references." />
      <Box>
        <DataTable
          columns={columns}
          rows={escalationsQuery.data ?? []}
          getRowKey={(row) => row.id}
          loading={escalationsQuery.isLoading}
          empty={
            <EmptyState
              icon={SupportAgentOutlinedIcon}
              title="No escalations"
              body="Escalations can be opened from an approved store workspace when setup or verification is blocked."
            />
          }
        />
      </Box>
    </>
  )
}
