import { useState } from 'react'
import { Button, Drawer } from '@mui/material'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import { brainPath } from '../brain/brainNavigation'
import { AssistantPanel } from './AssistantWorkspace'
import { useAssistant } from './useAssistant'
import type { AssistantScope } from './types'

/** Kit identity is navigation context only; authority remains with its existing Brain. */
export function KitAssistantGuide({ brainId, targetProfileId, engageHref }: { brainId: string; targetProfileId?: string; engageHref?: string }) {
  const [open, setOpen] = useState(false), [activated, setActivated] = useState(false)
  const scope: AssistantScope = { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: brainId, task: 'KIT' }
  const controller = useAssistant(scope, activated, async () => {})
  const links = [
    ['Knowledge', 'knowledge'], ['Actions', 'capabilities/actions'], ['Connections', 'knowledge/connections'],
    ['Responses', 'responses'], ['AI models', 'models'], ['Human support', 'human-support'],
    ['Action reviews', 'action-reviews'], ['Specialists', 'human-support/specialists'], ['Review & publish', 'updates'],
  ].map(([label, path]) => ({ label, href: brainPath(brainId, path, targetProfileId) }))
  if (engageHref) links.push({ label: 'Engage appearance & preview', href: engageHref })
  return <>
    <Button startIcon={<AutoAwesomeOutlined />} onClick={() => { setActivated(true); setOpen(true) }}>Kit AI guide</Button>
    <Drawer anchor="right" open={open} onClose={() => setOpen(false)} ModalProps={{ keepMounted: true }} PaperProps={{ sx: { width: 'min(100%, 480px)', height: '100dvh' } }}>
      <AssistantPanel controller={controller} scope={scope} guideOnly links={links} onClose={() => setOpen(false)} />
    </Drawer>
  </>
}
