import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { Alert, Button, Typography } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import { experienceKitKeys, fetchExperienceKit } from '../../api/experienceKitsApi'
import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary } from '../../api/platformApi'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { deploymentTerm } from '../../deploymentTerminology'

export function EngageReturnNavigation({ app, deployments, brainLibrary, onBrainReturn, fallbackLabel = false }: {
  app: AIWorkspaceInstallationSummary; deployments: CustomerDeploymentSummary[]; brainLibrary?: boolean
  onBrainReturn?: (releaseId: string | null) => void; fallbackLabel?: boolean
}) {
  const location = useLocation(), params = new URLSearchParams(location.search), { session } = usePlatformAuth()
  const kitId = params.get('kitId'), customerId = session?.customerId
  const owned = Boolean(customerId && app.customerId === customerId)
  const kit = useQuery({ queryKey: experienceKitKeys.detail(customerId ?? '', kitId ?? ''),
    queryFn: ({ signal }) => fetchExperienceKit(kitId!, signal), enabled: owned && Boolean(kitId), retry: false, staleTime: 60_000 })
  if (!owned) return null
  if (kitId) {
    if (kit.isPending) return <Typography variant="caption" color="text.secondary">Loading Kit return link...</Typography>
    if (kit.data && [kit.data.workspace, kit.data.embeddedWorkspace].some(workspace => workspace?.installationId === app.installationId && workspace.customerId === customerId)
      && kit.data.deploymentId === app.deploymentId
      && [kit.data.installationId, kit.data.embeddedInstallationId].includes(app.installationId)) {
      return <Button component={RouterLink} to={`/kits/${encodeURIComponent(kit.data.id)}`} startIcon={<ArrowBackRounded />} sx={{ px: 0 }}>Back to Kit</Button>
    }
    return <Alert severity="warning">The original Kit is unavailable for this app. <Button component={RouterLink} to="/kits">My kits</Button></Alert>
  }
  const parent = deployments.find(item => item.deploymentId === app.deploymentId)
  const originId = params.get('fromBrainId') ?? location.state?.brainPanelOrigin?.deploymentId
  if (parent && (brainLibrary || originId === parent.deploymentId)) {
    const releaseId = originId === parent.deploymentId ? params.get('fromBrainReleaseId') ?? location.state?.brainPanelOrigin?.releaseId : null
    const target = new URLSearchParams({ resourceKind: 'deployment', resourceId: parent.deploymentId, deploymentId: parent.deploymentId })
    if (typeof releaseId === 'string') target.set('releaseId', releaseId)
    return onBrainReturn ? <Button startIcon={<ArrowBackRounded />} onClick={() => onBrainReturn(typeof releaseId === 'string' ? releaseId : null)} sx={{ px: 0 }}>Back to {deploymentTerm.singular}</Button>
      : <Button component={RouterLink} to={`/deployments?${target}`} startIcon={<ArrowBackRounded />} sx={{ px: 0 }}>Back to {deploymentTerm.singular}</Button>
  }
  return fallbackLabel ? <Typography variant="overline" color="text.secondary">Engage app</Typography> : null
}
