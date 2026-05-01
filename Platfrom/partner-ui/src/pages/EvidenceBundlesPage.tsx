import { Alert, Box, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createStoreEvidenceBundle, downloadEvidenceBundle, listEvidenceBundles } from '../api/evidence'
import type { PartnerEvidenceBundle } from '../api/schemas'
import { listPartnerStores } from '../api/stores'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DataTable, type DataColumn } from '../components/DataTable'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

export function EvidenceBundlesPage() {
  const { api } = useSupabaseAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const bundlesQuery = useQuery({ queryKey: ['evidence-bundles'], queryFn: () => listEvidenceBundles(api) })
  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api) })
  const stores = storesQuery.data ?? []
  const [storeId, setStoreId] = useState('')
  const selectedStoreId = storeId || stores[0]?.id || ''
  const createMutation = useMutation({
    mutationFn: () => createStoreEvidenceBundle(api, selectedStoreId, { bundleKind: 'LAUNCH_PACKET' }),
    onSuccess: async (bundle) => {
      await queryClient.invalidateQueries({ queryKey: ['evidence-bundles'] })
      navigate(`/evidence/${encodeURIComponent(bundle.id)}`)
    },
  })
  const downloadMutation = useMutation({
    mutationFn: async (bundle: PartnerEvidenceBundle) => {
      const blob = await downloadEvidenceBundle(api, bundle.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `${bundle.id}.zip`
      anchor.click()
      URL.revokeObjectURL(url)
    },
  })
  const columns: DataColumn<PartnerEvidenceBundle>[] = [
    { key: 'name', header: 'Bundle', render: (row) => <Button onClick={() => navigate(`/evidence/${row.id}`)} sx={{ justifyContent: 'flex-start' }}>{row.bundleName}</Button> },
    { key: 'store', header: 'Store', render: (row) => <Typography color="text.secondary">{row.shopDomain ?? 'Store scoped'}</Typography> },
    { key: 'kind', header: 'Kind', render: (row) => <Typography>{titleize(row.bundleKind)}</Typography> },
    { key: 'status', header: 'Status', render: (row) => <StatusChip status={row.status} /> },
    { key: 'attachments', header: 'Files', render: (row) => <Typography>{row.attachments.length}</Typography> },
    { key: 'generated', header: 'Generated', render: (row) => <Typography color="text.secondary">{formatDateTime(row.generatedAt)}</Typography> },
    { key: 'download', header: '', align: 'right', render: (row) => <Button onClick={() => downloadMutation.mutate(row)}>Download</Button> },
  ]

  return (
    <>
      <PageHeader title="Evidence bundles" subtitle="Immutable merchant-safe packets generated from store readiness, verification runs, and partner-visible support evidence." />
      {createMutation.isError ? <Alert severity="error" sx={{ mb: 2 }}>{createMutation.error instanceof Error ? createMutation.error.message : 'Evidence generation failed.'}</Alert> : null}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
          <TextField select label="Approved store" value={selectedStoreId} onChange={(event) => setStoreId(event.target.value)} sx={{ minWidth: 320 }} disabled={stores.length === 0}>
            {stores.map((store) => <MenuItem key={store.id} value={store.id}>{store.shopDomain}</MenuItem>)}
          </TextField>
          <Button variant="contained" disabled={!selectedStoreId || createMutation.isPending} onClick={() => createMutation.mutate()}>
            Export launch evidence
          </Button>
        </Stack>
      </Paper>
      <DataTable
        columns={columns}
        rows={bundlesQuery.data ?? []}
        getRowKey={(row) => row.id}
        loading={bundlesQuery.isLoading}
        empty={<EmptyState icon={FolderZipOutlinedIcon} title="No evidence bundles" body="Run verification or export launch evidence from an approved store to create the first packet." />}
      />
    </>
  )
}
