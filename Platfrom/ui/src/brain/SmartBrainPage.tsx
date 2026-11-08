import { Alert, Button } from '@mui/material'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useBrainWorkspace } from './BrainWorkspaceContext'
import { currentBrainSection } from './brainNavigation'
import { BrainOverviewPage } from './BrainOverviewPage'
import { BrainKnowledgePage } from './BrainKnowledgePage'
import { BrainResponsesPage } from './BrainResponsesPage'
import { BrainCapabilitiesPage } from './BrainCapabilitiesPage'
import { BrainUpdatesPage } from './BrainUpdatesPage'
import { BrainSettingsPage } from './BrainSettingsPage'
import { BrainChannelsPage, BrainHumanSupportPage } from './BrainExperiencePages'
import { BrainTryPage } from './BrainTryPage'
import { BrainActionReviewsPage } from './BrainActionReviewsPage'

export function SmartBrainPage() {
  const location = useLocation()
  const { brainId, targetProfileId, href } = useBrainWorkspace()
  const section = currentBrainSection(location.pathname)
  const key = `${brainId}:${targetProfileId}`
  switch (section) {
    case '': return <BrainOverviewPage key={key} />
    case 'knowledge': return <BrainKnowledgePage key={key} />
    case 'responses': return <BrainResponsesPage key={key} />
    case 'capabilities': return <BrainCapabilitiesPage key={key} />
    case 'channels': return <BrainChannelsPage key={key} />
    case 'human-support': return <BrainHumanSupportPage key={key} />
    case 'action-reviews': return <BrainActionReviewsPage key={key} />
    case 'try': return <BrainTryPage key={key} />
    case 'updates': return <BrainUpdatesPage key={key} />
    case 'settings': return <BrainSettingsPage key={key} />
    default: return <Alert severity="warning">This Smart Brain page does not exist. <Button component={RouterLink} to={href()}>Open overview</Button></Alert>
  }
}
