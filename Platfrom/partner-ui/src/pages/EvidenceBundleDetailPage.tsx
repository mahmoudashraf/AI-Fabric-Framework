import { Alert, Button, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { downloadEvidenceBundle, getEvidenceBundle } from '../api/evidence'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

export function EvidenceBundleDetailPage() {
  const { bundleId = '' } = useParams()
  const { api } = useSupabaseAuth()
  const bundleQuery = useQuery({ queryKey: ['evidence-bundle', bundleId], queryFn: () => getEvidenceBundle(api, bundleId), enabled: Boolean(bundleId) })
  const downloadMutation = useMutation({
    mutationFn: async () => {
      const blob = await downloadEvidenceBundle(api, bundleId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `${bundleId}.zip`
      anchor.click()
      URL.revokeObjectURL(url)
    },
  })

  if (bundleQuery.isLoading) return <LinearProgress />
  if (bundleQuery.isError || !bundleQuery.data) return <Alert severity="error">Evidence bundle is not available to this workspace.</Alert>
  const bundle = bundleQuery.data

  return (
    <>
      <PageHeader
        title={bundle.bundleName}
        subtitle={bundle.shopDomain ?? 'Partner evidence'}
        breadcrumbs={[{ label: 'Evidence', to: '/evidence' }, { label: bundle.bundleName }]}
        actions={<Button startIcon={<FileDownloadOutlinedIcon />} onClick={() => downloadMutation.mutate()}>Download ZIP</Button>}
      />
      <Stack spacing={2}>
        <Paper sx={{ p: 2 }}>
          <Stack spacing={1}>
            <StatusChip status={bundle.status} />
            <Typography>Kind: {titleize(bundle.bundleKind)}</Typography>
            <Typography color="text.secondary">Generated {formatDateTime(bundle.generatedAt)} by {bundle.generatedBy ?? 'Partner member'}</Typography>
            <Typography color="text.secondary">Expires {formatDateTime(bundle.expiresAt)}</Typography>
          </Stack>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Attachments</Typography>
          <Stack component="ul" spacing={0.75} sx={{ pl: 3 }}>
            {bundle.attachments.map((item) => <Typography component="li" key={item}>{item}</Typography>)}
          </Stack>
        </Paper>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h3">Merchant-safe summary</Typography>
          <Typography component="pre" sx={{ whiteSpace: 'pre-wrap', overflow: 'auto', fontSize: '0.8125rem' }}>
            {JSON.stringify(bundle.summary, null, 2)}
          </Typography>
        </Paper>
      </Stack>
    </>
  )
}
