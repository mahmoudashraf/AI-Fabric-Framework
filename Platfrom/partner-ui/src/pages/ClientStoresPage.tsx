import { Box, Button, Stack, Typography } from '@mui/material'
import AddTaskOutlinedIcon from '@mui/icons-material/AddTaskOutlined'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import type { PartnerSession, PartnerStore } from '../api/schemas'
import { listPartnerStores } from '../api/stores'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

export function ClientStoresPage({ session }: { session: PartnerSession }) {
  const navigate = useNavigate()
  const { api } = useSupabaseAuth()
  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api), enabled: !session.signupRequired })
  const columns: DataColumn<PartnerStore>[] = [
    {
      key: 'merchant',
      header: 'Store',
      render: (store) => (
        <Stack spacing={0.25}>
          <Button sx={{ justifyContent: 'flex-start', px: 0 }} onClick={() => navigate(`/stores/${encodeURIComponent(store.id)}`)}>
            {store.merchantName}
          </Button>
          <Typography variant="caption" color="text.secondary">{store.shopDomain}</Typography>
        </Stack>
      ),
    },
    { key: 'status', header: 'Status', render: (store) => <StatusChip status={store.status} /> },
    { key: 'knowledge', header: 'Knowledge Sync', render: (store) => <Typography>{titleize(store.knowledgeSyncStatus)}</Typography> },
    { key: 'readiness', header: 'Readiness', render: (store) => <Typography>{titleize(store.readinessStatus)}</Typography> },
    { key: 'surfaces', header: 'Surfaces', render: (store) => <Typography>{store.enabledSurfaces.length}</Typography> },
    { key: 'last', header: 'Last activity', render: (store) => <Typography color="text.secondary">{formatDateTime(store.lastActivityAt)}</Typography> },
    { key: 'open', header: '', align: 'right', render: (store) => <Button onClick={() => navigate(`/stores/${encodeURIComponent(store.id)}`)}>Open</Button> },
  ]

  return (
    <>
      <PageHeader
        title="Client stores"
        subtitle="Approved client store assignments and product implementation state."
        actions={
          <Button variant="contained" startIcon={<AddTaskOutlinedIcon />} onClick={() => navigate('/implementations/new')}>
            New implementation
          </Button>
        }
      />
      <Box>
        <DataTable
          columns={columns}
          rows={storesQuery.data ?? []}
          getRowKey={(row) => row.id}
          loading={storesQuery.isLoading}
          empty={
            <EmptyState
              icon={StorefrontOutlinedIcon}
              title="No approved stores"
              body="Create an implementation request and ask the merchant to approve scoped access before store data appears here."
              actionLabel="New implementation"
              onAction={() => navigate('/implementations/new')}
            />
          }
        />
      </Box>
    </>
  )
}
