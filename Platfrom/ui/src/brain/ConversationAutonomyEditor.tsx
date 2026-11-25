import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, FormControlLabel, Stack, Switch, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { fetchDeploymentDraft, updateDeploymentDraft } from '../api/platformApi'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { brainPaneSx } from './BrainPageLayout'
import { autonomyLimitFields, autonomyRecord, conversationalAutonomyError, mergeConversationalAutonomy, readConversationalAutonomy } from './conversationalAutonomy'

export function ConversationAutonomyEditor() {
  const { brainId } = useBrainWorkspace()
  const { workspace } = useDeploymentWorkspace()
  const queries = useQueryClient()
  const draft = useQuery({ queryKey: ['deployment-draft', brainId], queryFn: () => fetchDeploymentDraft(brainId) })
  const [changes, setChanges] = useState<Record<string, unknown>>({})
  const saved = readConversationalAutonomy(draft.data?.providerConfig)
  const policy = { ...saved }
  for (const [key, value] of Object.entries(changes)) { if (value === null) delete policy[key]; else policy[key] = value }
  const dirty = Object.keys(changes).length > 0
  const canEdit = workspace?.access.canEdit ?? false
  const raw = autonomyRecord(draft.data?.providerConfig).conversationalAutonomy
  const malformed = raw != null && (typeof raw !== 'object' || Array.isArray(raw))
  const error = malformed ? 'The saved conversation policy is not a supported object. Review Advanced configuration before editing it.' : conversationalAutonomyError(policy)
  const save = useMutation({
    mutationFn: async () => {
      if (!canEdit || error) throw new Error(error ?? 'Editor access is required.')
      const latest = await fetchDeploymentDraft(brainId)
      const providerConfig = mergeConversationalAutonomy(latest.providerConfig, changes)
      const latestError = conversationalAutonomyError(readConversationalAutonomy(providerConfig))
      if (latestError) throw new Error(latestError)
      return updateDeploymentDraft(latest.id, { providerConfig })
    },
    onSuccess: updated => {
      setChanges({})
      queries.setQueryData(['deployment-draft', brainId], updated)
      void queries.invalidateQueries({ queryKey: ['deployment-workspace', brainId] })
    },
  })
  const editor = useMemo(() => ({ dirty, busy: save.isPending, canSave: canEdit && dirty && !error && Boolean(draft.data),
    save: canEdit ? () => save.mutateAsync().catch(() => undefined) : undefined,
    discard: () => { setChanges({}); save.reset() },
    message: !canEdit ? 'Read-only access' : dirty ? 'Unsaved changes' : save.isSuccess ? 'Draft saved; live configuration unchanged' : 'Editing shared draft',
    error: save.isError ? save.error.message : error,
  }), [dirty, save.isPending, save.isSuccess, save.isError, save.error, canEdit, error, draft.data, save.mutateAsync, save.reset])
  useBrainEditor(editor)
  const choose = (key: string, value: unknown) => {
    setChanges(current => ({ ...current, [key]: value })); save.reset()
  }
  const disabled = !canEdit || !draft.data || malformed || save.isPending
  return <Box sx={{ ...brainPaneSx, flex: 1 }}>
    {draft.isError && <Alert severity="error">{draft.error.message}</Alert>}
    {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>}
    <Stack spacing={2.5} sx={{ maxWidth: 880 }}>
      <Box>
        <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 650, mb: 1 }}>Conversation handling</Typography>
        <ToggleButtonGroup exclusive size="small" value={policy.enabled === true ? 'autonomous' : 'standard'} disabled={disabled}
          aria-label="Conversation handling" onChange={(_event, value) => { if (value) choose('enabled', value === 'autonomous') }}>
          <ToggleButton value="standard">Standard</ToggleButton>
          <ToggleButton value="autonomous">Autonomous</ToggleButton>
        </ToggleButtonGroup>
      </Box>
      <Typography variant="body2" color="text.secondary">Tools follow their existing permissions and confirmation policy. Draft changes take effect after publishing and applying an update.</Typography>
      {policy.enabled === true && <Alert severity="info">Autonomous conversations require a compatible runtime and canonical conversation support. Existing customer access and knowledge boundaries are unchanged.</Alert>}
      <Accordion disableGutters elevation={0} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, '&:before': { display: 'none' } }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="body2">Advanced execution limits</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2}>
            <FormControlLabel label="Use Autonomous by default" control={<Switch checked={policy.defaultEnabled !== false} disabled={disabled}
              onChange={(_event, checked) => choose('defaultEnabled', checked)} />} />
            <FormControlLabel label="Let the model recover from non-authority read failures" control={<Switch checked={policy.replanNonAuthorityReadFailures !== false}
              disabled={disabled} onChange={(_event, checked) => choose('replanNonAuthorityReadFailures', checked)} />} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
              {autonomyLimitFields.map(([key, label, min, max, fallback]) => <TextField key={key} label={label} type="number" size="small"
                value={policy[key] ?? ''} placeholder={String(fallback)} disabled={disabled} inputProps={{ min, max, step: 1 }}
                helperText={`Default ${fallback}; ${min}-${max}.`} onChange={event => choose(key, event.target.value === '' ? null : Number(event.target.value))} />)}
            </Box>
            <TextField label="Additional workflow instructions" multiline minRows={4} value={policy.instructions ?? ''} disabled={disabled}
              inputProps={{ maxLength: 8000 }} onChange={event => choose('instructions', event.target.value)} />
          </Stack>
        </AccordionDetails>
      </Accordion>
    </Stack>
  </Box>
}
