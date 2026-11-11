import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Box, Button, Drawer } from '@mui/material'
import { AssistantPanel, useAssistantPanelInputs } from '../assistant/AssistantWorkspace'
import { useAssistant } from '../assistant/useAssistant'
import { operation } from '../assistant/scope'
import type { AssistantScope } from '../assistant/types'
import { mcpApi } from './api'

type Props = { brainId: string; connectionId: string; snapshotId: string; toolNames: string[]; onClose: () => void; onSaved: () => Promise<void> }

export function McpAssistant({ brainId, connectionId, snapshotId, toolNames, onClose, onSaved }: Props) {
  const scope = useMemo<AssistantScope>(() => ({ resourceKind: 'DEPLOYMENT_DRAFT', resourceId: brainId, task: 'ACTIONS', editorContext: { section: 'ACTIONS' } }), [brainId])
  const controller = useAssistant(scope, true, onSaved)
  const inputs = useAssistantPanelInputs()
  const [attaching, setAttaching] = useState(false)
  const [error, setError] = useState('')
  const attempted = useRef(false)
  const live = useRef(true)
  useEffect(() => { live.current = true; return () => { live.current = false } }, [])
  const attach = useCallback(async () => {
    if (!controller.session || controller.busy) return
    attempted.current = true; setAttaching(true); setError('')
    try {
      const before = new Set(controller.session.sources.map(source => source.id))
      const session = await mcpApi.assistantSource(controller.session.id, connectionId, snapshotId, toolNames)
      if (!live.current) return
      if (!await controller.refresh()) throw new Error('The catalogue was attached, but the assistant session could not be refreshed. Close and reopen Help configure to review it.')
      if (!live.current) return
      const added = session.sources.filter(source => !before.has(source.id))
      inputs.setSourceIds(current => [...new Set([...current, ...added.map(source => source.id)])])
      inputs.setOperationIds(current => [...new Set([...current, ...added.flatMap(source => source.operations.map(operation).filter(Boolean).map(item => item!.id))])])
    } catch (failure) { if (live.current) setError(failure instanceof Error ? failure.message : 'The catalogue could not be attached. Importing tools does not require the assistant.') }
    finally { if (live.current) setAttaching(false) }
  }, [controller.session, controller.busy, connectionId, snapshotId, toolNames, controller.refresh])
  useEffect(() => { if (controller.session && !controller.busy && !attempted.current) void attach() }, [controller.session, controller.busy, attach])
  return <Drawer anchor="right" open onClose={onClose} sx={{ '& .MuiDrawer-paper': { width: { xs: '100%', sm: 540 }, maxWidth: '100%', height: '100dvh' } }}>
    <Box sx={{ p: 2, pb: 0 }}><Alert severity="info">Optional help with descriptions, customer inputs and workflow guidance. Sending a request shares selected tool metadata with your configured AI provider, never credentials. The core tool import does not use AI.</Alert>
      {attaching ? <Alert severity="info" sx={{ mt: 1 }}>Attaching the selected, server-sanitized tools. No model request has started.</Alert> : null}
      {error ? <Alert severity="error" sx={{ mt: 1 }} action={<Button disabled={controller.busy} onClick={() => void attach()}>Retry</Button>}>{error}</Alert> : null}
    </Box>
    <AssistantPanel scope={scope} controller={{ ...controller, pending: controller.pending || attaching, busy: controller.busy || attaching }} inputs={inputs} onClose={onClose} editorBusy={attaching || Boolean(error)} />
  </Drawer>
}
