import { Alert, Box, Button, Chip, MenuItem, Paper, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { listPartnerStores } from '../api/stores'
import type { PartnerTemplate } from '../api/schemas'
import { applyTemplate, listTemplateApplications, listTemplates } from '../api/templates'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { DetailDrawer } from '../components/DetailDrawer'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

const tabs = ['VERTICAL_PLAYBOOK', 'LAUNCH_CHECKLIST', 'SUPPORT_HANDOFF']

export function TemplatesPage() {
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const templatesQuery = useQuery({ queryKey: ['templates'], queryFn: () => listTemplates(api) })
  const applicationsQuery = useQuery({ queryKey: ['template-applications'], queryFn: () => listTemplateApplications(api) })
  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api) })
  const [tab, setTab] = useState('VERTICAL_PLAYBOOK')
  const [selected, setSelected] = useState<PartnerTemplate | null>(null)
  const [storeId, setStoreId] = useState('')
  const stores = storesQuery.data ?? []
  const templates = templatesQuery.data ?? []
  const filtered = useMemo(() => templates.filter((template) => template.category === tab), [templates, tab])
  const applications = useMemo(() => {
    const seen = new Set<string>()
    return (applicationsQuery.data ?? []).filter((application) => {
      const key = `${application.templateId}:${application.storeAssignmentId ?? 'workspace'}`
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
  }, [applicationsQuery.data])
  const applyMutation = useMutation({
    mutationFn: (template: PartnerTemplate) => applyTemplate(api, template.id, storeId || undefined),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['template-applications'] })
      setSelected(null)
      setStoreId('')
    },
  })

  return (
    <>
      <PageHeader title="Templates & playbooks" subtitle="Repeatable partner implementation playbooks with persisted application records." />
      {applyMutation.isError ? <Alert severity="error" sx={{ mb: 2 }}>{applyMutation.error instanceof Error ? applyMutation.error.message : 'Template application failed.'}</Alert> : null}
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_event, value) => setTab(value)} variant="scrollable">
          {tabs.map((item) => <Tab key={item} label={titleize(item)} value={item} />)}
        </Tabs>
      </Paper>
      <Stack spacing={1.5}>
        {filtered.map((template) => (
          <Paper key={template.id} sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
              <Box>
                <Typography variant="h3">{template.name}</Typography>
                <Typography color="text.secondary">{template.bodyMarkdown}</Typography>
                <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mt: 1 }}>
                  <Chip label={template.vertical} size="small" />
                  {template.surfaceIds.map((surface) => <Chip key={surface} label={surface} size="small" variant="outlined" />)}
                </Stack>
              </Box>
              <Button onClick={() => setSelected(template)}>Review and use</Button>
            </Stack>
          </Paper>
        ))}
      </Stack>
      <Paper sx={{ p: 2, mt: 3 }}>
        <Typography variant="h2" sx={{ mb: 1.5 }}>Applied templates</Typography>
        <Stack spacing={1}>
          {applications.map((application) => (
            <Stack key={application.id} direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between">
              <Box>
                <Typography fontWeight={800}>{application.templateName} {application.shopDomain ? `· ${application.shopDomain}` : ''}</Typography>
                <Typography variant="caption" color="text.secondary">{titleize(application.category)} · {application.checklist.length} checklist items · {application.assumptions.length} assumptions</Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <StatusChip status={application.status} label="Applied" />
                <Typography variant="caption" color="text.secondary">{formatDateTime(application.appliedAt)}</Typography>
              </Stack>
            </Stack>
          ))}
          {applications.length === 0 ? <Typography color="text.secondary">No templates applied yet.</Typography> : null}
        </Stack>
      </Paper>
      <DetailDrawer open={Boolean(selected)} title={selected?.name ?? 'Template'} onClose={() => setSelected(null)}>
        {selected ? (
          <Stack spacing={2}>
            <Typography>{selected.bodyMarkdown}</Typography>
            <Section title="Assumptions" items={selected.assumptions} />
            <Section title="Checklist" items={selected.checklist} ordered />
            <TextField select label="Apply to store" value={storeId} onChange={(event) => setStoreId(event.target.value)} helperText="Optional. Leave empty to duplicate this playbook to the workspace.">
              <MenuItem value="">Workspace only</MenuItem>
              {stores.map((store) => <MenuItem key={store.id} value={store.id}>{store.shopDomain}</MenuItem>)}
            </TextField>
            <Button variant="contained" onClick={() => applyMutation.mutate(selected)} disabled={applyMutation.isPending}>
              Use template
            </Button>
          </Stack>
        ) : null}
      </DetailDrawer>
    </>
  )
}

function Section({ title, items, ordered }: { title: string; items: string[]; ordered?: boolean }) {
  return (
    <Box>
      <Typography variant="h3" sx={{ mb: 0.75 }}>{title}</Typography>
      <Stack component={ordered ? 'ol' : 'ul'} spacing={0.75} sx={{ pl: 3 }}>
        {items.map((item) => <Typography component="li" key={item}>{item}</Typography>)}
      </Stack>
    </Box>
  )
}
