import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import RestoreRoundedIcon from '@mui/icons-material/RestoreRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  createDeploymentPromptRevision,
  fetchDeploymentDraft,
  fetchDeploymentPromptRevisions,
  restoreDeploymentPromptRevision,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const PROMPT_FIELDS = [
  {
    key: 'systemPrompt',
    label: 'System Prompt',
    description: 'Global assistant operating instructions and tone.',
  },
  {
    key: 'intentExtractionPrompt',
    label: 'Intent Extraction Prompt',
    description: 'Guidance for mapping user input into intents and tasks.',
  },
  {
    key: 'actionSelectionPrompt',
    label: 'Action Selection Prompt',
    description: 'Rules for choosing live read-only and write actions.',
  },
  {
    key: 'clarificationPrompt',
    label: 'Clarification Prompt',
    description: 'Follow-up behavior when more detail is needed from the user.',
  },
  {
    key: 'answerGenerationPrompt',
    label: 'Answer Generation Prompt',
    description: 'How final grounded answers should be composed.',
  },
  {
    key: 'retrievalPrompt',
    label: 'Retrieval Prompt',
    description: 'Instructions for using knowledge and vector search context.',
  },
  {
    key: 'assistantUiPrompt',
    label: 'Assistant UI Prompt',
    description: 'UI-facing behavioral prompt for embedded chat surfaces.',
  },
] as const

type PromptKey = (typeof PROMPT_FIELDS)[number]['key']
type PromptFormState = Record<PromptKey, string>

function defaultPromptState(): PromptFormState {
  return {
    systemPrompt: '',
    intentExtractionPrompt: '',
    actionSelectionPrompt: '',
    clarificationPrompt: '',
    answerGenerationPrompt: '',
    retrievalPrompt: '',
    assistantUiPrompt: '',
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function normalizePromptState(value: unknown): PromptFormState {
  const next = defaultPromptState()
  if (!isRecord(value)) {
    return next
  }

  for (const field of PROMPT_FIELDS) {
    const candidate = value[field.key]
    next[field.key] = typeof candidate === 'string' ? candidate : ''
  }
  return next
}

function countPopulatedPrompts(formState: PromptFormState) {
  return PROMPT_FIELDS.filter((field) => formState[field.key].trim().length > 0).length
}

function statesEqual(left: PromptFormState, right: PromptFormState) {
  return PROMPT_FIELDS.every((field) => left[field.key] === right[field.key])
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString()
}

export function PromptsPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<PromptFormState>(defaultPromptState())
  const [revisionLabel, setRevisionLabel] = useState('')
  const [revisionSummary, setRevisionSummary] = useState('')

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const promptRevisionsQuery = useQuery({
    queryKey: ['deployment-prompt-revisions', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptRevisions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (!draftQuery.data) {
      return
    }
    setFormState(normalizePromptState(draftQuery.data.promptConfig))
  }, [draftQuery.data])

  const savedState = useMemo(
    () => normalizePromptState(draftQuery.data?.promptConfig),
    [draftQuery.data],
  )
  const draftDirty = useMemo(() => !statesEqual(formState, savedState), [formState, savedState])
  const populatedPromptCount = useMemo(() => countPopulatedPrompts(formState), [formState])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draftQuery.data) {
        throw new Error('No active draft available.')
      }
      return updateDeploymentDraft(draftQuery.data.id, { promptConfig: formState })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const createRevisionMutation = useMutation({
    mutationFn: () =>
      createDeploymentPromptRevision(selectedDeploymentId, {
        revisionLabel: revisionLabel.trim() || undefined,
        revisionSummary: revisionSummary.trim() || undefined,
      }),
    onSuccess: async () => {
      setRevisionLabel('')
      setRevisionSummary('')
      await queryClient.invalidateQueries({ queryKey: ['deployment-prompt-revisions', selectedDeploymentId] })
    },
  })

  const restoreRevisionMutation = useMutation({
    mutationFn: (revisionId: string) => restoreDeploymentPromptRevision(selectedDeploymentId, revisionId),
    onSuccess: async (draft) => {
      setFormState(normalizePromptState(draft.promptConfig))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  return (
    <Stack spacing={3}>
      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt bundle</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt changes are versioned deployment config. Save the draft here, then publish and apply when you
                want the revised prompt bundle to become part of a release.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Change type: Versioned config" color="primary" variant="outlined" />
              <Chip label="Action path: Save Draft -> Publish -> Apply" color="warning" />
              <Chip label={`Filled prompts: ${populatedPromptCount}/${PROMPT_FIELDS.length}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
            </Stack>

            <Alert severity={draftDirty ? 'warning' : 'info'}>
              {draftDirty
                ? 'Prompt edits are pending in the editor. Save the draft before publishing or creating a prompt revision snapshot.'
                : 'Prompt bundle matches the saved draft. Revisions can be snapshotted from this saved state.'}
            </Alert>

            <Grid container spacing={2}>
              {PROMPT_FIELDS.map((field) => (
                <Grid item xs={12} md={6} key={field.key}>
                  <TextField
                    label={field.label}
                    helperText={field.description}
                    multiline
                    minRows={7}
                    fullWidth
                    value={formState[field.key]}
                    onChange={(event) =>
                      setFormState((previous) => ({
                        ...previous,
                        [field.key]: event.target.value,
                      }))
                    }
                  />
                </Grid>
              ))}
            </Grid>

            {saveMutation.isError ? (
              <Alert severity="error">
                {saveMutation.error instanceof Error ? saveMutation.error.message : 'Failed to save prompt draft'}
              </Alert>
            ) : null}
            {saveMutation.isSuccess ? <Alert severity="success">Prompt draft saved.</Alert> : null}

            <Stack direction="row" spacing={1.5}>
              <Button
                variant="contained"
                startIcon={<SaveRoundedIcon />}
                disabled={!draftQuery.data || saveMutation.isPending || !draftDirty}
                onClick={() => saveMutation.mutate()}
              >
                {saveMutation.isPending ? 'Saving...' : 'Save prompt draft'}
              </Button>
              <Button
                variant="outlined"
                onClick={() => {
                  setFormState(savedState)
                }}
              >
                Reset to saved
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt revisions</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt revisions are lightweight prompt-only snapshots inside the deployment workspace. They are useful
                for restoring prompt behavior quickly without rolling back the rest of the deployment draft.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="warning">
                Save the prompt draft before creating a prompt revision. Revisions snapshot the current saved draft
                state, not unsaved editor changes.
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Revision label"
                  placeholder="Grounded commerce baseline"
                  fullWidth
                  value={revisionLabel}
                  onChange={(event) => setRevisionLabel(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={8}>
                <TextField
                  label="Revision summary"
                  placeholder="What changed and why?"
                  fullWidth
                  value={revisionSummary}
                  onChange={(event) => setRevisionSummary(event.target.value)}
                />
              </Grid>
            </Grid>

            {createRevisionMutation.isError ? (
              <Alert severity="error">
                {createRevisionMutation.error instanceof Error
                  ? createRevisionMutation.error.message
                  : 'Failed to create prompt revision'}
              </Alert>
            ) : null}
            {createRevisionMutation.isSuccess ? (
              <Alert severity="success">Prompt revision created from the current saved draft.</Alert>
            ) : null}

            <Stack direction="row" spacing={1.5}>
              <Button
                variant="contained"
                startIcon={<HistoryRoundedIcon />}
                disabled={!selectedDeploymentId || draftDirty || createRevisionMutation.isPending}
                onClick={() => createRevisionMutation.mutate()}
              >
                {createRevisionMutation.isPending ? 'Creating...' : 'Create prompt revision'}
              </Button>
            </Stack>

            {restoreRevisionMutation.isError ? (
              <Alert severity="error">
                {restoreRevisionMutation.error instanceof Error
                  ? restoreRevisionMutation.error.message
                  : 'Failed to restore prompt revision'}
              </Alert>
            ) : null}

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Revision</TableCell>
                  <TableCell>Summary</TableCell>
                  <TableCell>Filled prompts</TableCell>
                  <TableCell>Created by</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(promptRevisionsQuery.data ?? []).map((revision) => (
                  <TableRow key={revision.id} hover>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {revision.revisionLabel}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {revision.id}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{revision.revisionSummary || '—'}</TableCell>
                    <TableCell>{revision.populatedPromptCount}</TableCell>
                    <TableCell>{revision.createdByDisplayName || revision.createdByActorId}</TableCell>
                    <TableCell>{formatDateTime(revision.createdAt)}</TableCell>
                    <TableCell align="right">
                      <Button
                        variant="outlined"
                        size="small"
                        startIcon={<RestoreRoundedIcon />}
                        disabled={restoreRevisionMutation.isPending}
                        onClick={() => restoreRevisionMutation.mutate(revision.id)}
                      >
                        Restore to draft
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {(promptRevisionsQuery.data ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography variant="body2" color="text.secondary">
                        No prompt revisions yet. Save the prompt draft and snapshot the first revision here.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
